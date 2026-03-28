package com.aquiles.simpleportals.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ServerCompatibility {

    private final JavaPlugin plugin;
    private final boolean folia;

    public ServerCompatibility(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = hasMethod(plugin.getServer().getClass(), "getGlobalRegionScheduler");
    }

    public boolean isFolia() {
        return folia;
    }

    public Object runGlobalTimer(long initialDelayTicks, long periodTicks, Runnable task) {
        if (!folia) {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
        Object scheduler = invoke(plugin.getServer(), "getGlobalRegionScheduler");
        return invoke(
            scheduler,
            "runAtFixedRate",
            plugin,
            (Consumer<Object>) scheduledTask -> task.run(),
            Math.max(1L, initialDelayTicks),
            Math.max(1L, periodTicks)
        );
    }

    public Object runPlayerTimer(Player player, long initialDelayTicks, long periodTicks, Runnable task) {
        if (player == null) {
            return null;
        }
        if (!folia) {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (player.isOnline()) {
                    task.run();
                }
            }, initialDelayTicks, periodTicks);
        }
        Object scheduler = invoke(player, "getScheduler");
        return invoke(
            scheduler,
            "runAtFixedRate",
            plugin,
            (Consumer<Object>) scheduledTask -> {
                if (player.isOnline()) {
                    task.run();
                }
            },
            null,
            Math.max(1L, initialDelayTicks),
            Math.max(1L, periodTicks)
        );
    }

    public void cancelTask(Object task) {
        if (task == null) {
            return;
        }
        if (task instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }
        invoke(task, "cancel");
    }

    public void runGlobal(Runnable task) {
        runGlobalLater(1L, task);
    }

    public void runGlobalLater(long delayTicks, Runnable task) {
        if (!folia) {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
            return;
        }
        Object scheduler = invoke(plugin.getServer(), "getGlobalRegionScheduler");
        invoke(scheduler, "runDelayed", plugin, (Consumer<Object>) scheduledTask -> task.run(), Math.max(1L, delayTicks));
    }

    public void runPlayer(Player player, Runnable task) {
        runPlayerLater(player, 1L, task);
    }

    public void runPlayerLater(Player player, long delayTicks, Runnable task) {
        if (player == null) {
            return;
        }
        if (!folia) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    task.run();
                }
            }, Math.max(1L, delayTicks));
            return;
        }
        Object scheduler = invoke(player, "getScheduler");
        invoke(scheduler, "execute", plugin, (Runnable) () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, null, Math.max(1L, delayTicks));
    }

    public boolean teleportEntity(Entity entity, Location location) {
        if (entity == null || location == null) {
            return false;
        }
        if (!folia) {
            return entity.teleport(location);
        }
        try {
            Object result = invoke(entity, "teleportAsync", location);
            attachTeleportLogging(entity, result);
            return true;
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Could not start async teleport for " + entity.getType(), exception);
            return false;
        }
    }

    public CompletableFuture<Boolean> teleportEntityFuture(Entity entity, Location location) {
        if (entity == null || location == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (!folia) {
            return CompletableFuture.completedFuture(entity.teleport(location));
        }
        try {
            Object result = invoke(entity, "teleportAsync", location);
            return toTeleportFuture(entity, result);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Could not start async teleport for " + entity.getType(), exception);
            return CompletableFuture.completedFuture(false);
        }
    }

    public void removeEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        if (!folia) {
            if (!entity.isDead()) {
                entity.remove();
            }
            return;
        }
        Object scheduler = invoke(entity, "getScheduler");
        invoke(scheduler, "execute", plugin, (Runnable) () -> {
            if (!entity.isDead()) {
                entity.remove();
            }
        }, null, 1L);
    }

    private CompletableFuture<Boolean> toTeleportFuture(Entity entity, Object result) {
        if (!(result instanceof CompletableFuture<?> future)) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Boolean> wrapped = new CompletableFuture<>();
        future.whenComplete((teleportResult, exception) -> {
            if (exception != null) {
                plugin.getLogger().log(Level.FINE, "Async teleport failed for " + entity.getType(), exception);
                wrapped.complete(false);
                return;
            }
            if (teleportResult instanceof Boolean bool) {
                wrapped.complete(bool);
                return;
            }
            wrapped.complete(true);
        });
        return wrapped;
    }

    private void attachTeleportLogging(Entity entity, Object result) {
        if (result instanceof CompletableFuture<?> future) {
            future.whenComplete((ignored, exception) -> {
                if (exception != null) {
                    plugin.getLogger().log(Level.FINE, "Async teleport failed for " + entity.getType(), exception);
                }
            });
        }
    }

    private boolean hasMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private Object invoke(Object target, String methodName, Object... arguments) {
        if (target == null) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a null target.");
        }
        Method method = resolveMethod(target.getClass(), methodName, arguments);
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not invoke " + methodName + " on " + target.getClass().getName(), exception);
        }
    }

    private Method resolveMethod(Class<?> type, String methodName, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                Object argument = arguments[index];
                if (argument == null) {
                    if (parameterTypes[index].isPrimitive()) {
                        matches = false;
                        break;
                    }
                    continue;
                }
                if (!wrap(parameterTypes[index]).isAssignableFrom(argument.getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("No compatible method named " + methodName + " found on " + type.getName());
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}