package com.aquiles.simpleportals;

import com.aquiles.simpleportals.api.SimplePortalsApi;
import com.aquiles.simpleportals.command.PortalCommand;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.DestinationDefinition;
import com.aquiles.simpleportals.data.PortalDefinition;
import com.aquiles.simpleportals.listener.PortalListener;
import com.aquiles.simpleportals.service.PortalStore;
import com.aquiles.simpleportals.service.SelectionService;
import com.aquiles.simpleportals.service.TeleportService;
import com.aquiles.simpleportals.service.UpdateChecker;
import com.aquiles.simpleportals.util.ServerCompatibility;
import com.aquiles.simpleportals.util.Text;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bstats.bukkit.Metrics;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimplePortalsPlugin extends JavaPlugin implements SimplePortalsApi {

    private static final int BSTATS_PLUGIN_ID = 30029;
    private static final String PROXY_CHANNEL = "BungeeCord";

    private ConfigService configService;
    private PortalStore portalStore;
    private SelectionService selectionService;
    private TeleportService teleportService;
    private UpdateChecker updateChecker;
    private NamespacedKey wandKey;
    private String registeredProxyChannel;
    private ServerCompatibility compatibility;
    private PluginCommand registeredCommand;
    private boolean fallbackCommandRegistered;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();
        compatibility = new ServerCompatibility(this);
        logStartupBanner();

        wandKey = new NamespacedKey(this, "selector_wand");
        ensureResourceFile("messages.yml");
        ensureResourceFile("portals.yml");

        configService = new ConfigService(this);
        portalStore = new PortalStore(this, configService);
        selectionService = new SelectionService(this, configService, portalStore);
        teleportService = new TeleportService(this, configService, portalStore);
        updateChecker = new UpdateChecker(this, configService, compatibility);

        registerPermissions();
        reloadPluginState();

        PortalCommand portalCommand = new PortalCommand(this, configService, portalStore, selectionService, teleportService);
        registeredCommand = registerMainCommand(portalCommand);
        if (registeredCommand == null) {
            getLogger().severe("Could not register the /simpleportals command.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PortalListener(this, configService, selectionService, teleportService), this);
        getServer().getServicesManager().register(SimplePortalsApi.class, this, this, ServicePriority.Normal);

        new Metrics(this, BSTATS_PLUGIN_ID);
        updateChecker.checkForUpdatesAsync();
        logStartupSuccess(startedAt);
    }

    @Override
    public void onDisable() {
        if (selectionService != null) {
            selectionService.stopPreviewTask();
        }
        if (teleportService != null) {
            teleportService.shutdown();
        }
        if (portalStore != null) {
            portalStore.save();
        }
        getServer().getServicesManager().unregister(this);
        if (registeredProxyChannel != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, registeredProxyChannel);
        }
        unregisterFallbackCommand();
    }

    public void reloadPluginState() {
        configService.reload();
        portalStore.load();
        selectionService.restartPreviewTask();
        registerProxyChannel(PROXY_CHANNEL);
    }

    private void logStartupBanner() {
        String version = getPluginMeta().getVersion();
        getServer().getConsoleSender().sendMessage(Text.component("<aqua>   _____ _____"));
        getServer().getConsoleSender().sendMessage(Text.component("<aqua>  / ____|  __ \\ <gray>SimplePortals <white>v" + version));
        getServer().getConsoleSender().sendMessage(Text.component("<aqua> | (___ | |__) | <gray>Running on <white>Bukkit - " + getServer().getName()));
        getServer().getConsoleSender().sendMessage(Text.component("<aqua>  \\___ \\|  ___/  <gray>Folia support: " + (compatibility.isFolia() ? "<green>active" : "<yellow>ready")));
        getServer().getConsoleSender().sendMessage(Text.component("<aqua>  ____) | |"));
        getServer().getConsoleSender().sendMessage(Text.component("<aqua> |_____/|_|"));
        getServer().getConsoleSender().sendMessage(Text.component(""));
    }

    private void logStartupSuccess(long startedAt) {
        long took = Math.max(1L, System.currentTimeMillis() - startedAt);
        getServer().getConsoleSender().sendMessage(Text.component("<green>Successfully enabled. <gray>(took <white>" + took + "ms<gray>)"));
    }

    private void registerProxyChannel(String channel) {
        if (registeredProxyChannel != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, registeredProxyChannel);
        }
        registeredProxyChannel = channel;
        getServer().getMessenger().registerOutgoingPluginChannel(this, registeredProxyChannel);
    }

    private void ensureResourceFile(String name) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    private void registerPermissions() {
        registerPermission("simpleportals.command.help", "View the help menu", PermissionDefault.TRUE);
        registerPermission("simpleportals.command.create", "Create a portal definition", PermissionDefault.OP);
        registerPermission("simpleportals.command.wand", "Receive a selector wand", PermissionDefault.OP);
        registerPermission("simpleportals.command.reload", "Reload config and messages", PermissionDefault.OP);
        registerPermission("simpleportals.command.show", "Show nearby portals", PermissionDefault.OP);
        registerPermission("simpleportals.command.remove", "Remove a portal", PermissionDefault.OP);
        registerPermission("simpleportals.command.setblock", "Change a portal trigger block", PermissionDefault.OP);
        registerPermission("simpleportals.command.tp", "Teleport directly to a destination", PermissionDefault.OP);
        registerPermission("simpleportals.command.destination.create", "Create a local destination", PermissionDefault.OP);
        registerPermission("simpleportals.command.destination.remove", "Remove a destination", PermissionDefault.OP);
        registerPermission("simpleportals.use", "Use SimplePortals portals", PermissionDefault.TRUE);
    }

    private void registerPermission(String name, String description, PermissionDefault permissionDefault) {
        if (getServer().getPluginManager().getPermission(name) != null) {
            return;
        }
        getServer().getPluginManager().addPermission(new Permission(name, description, permissionDefault));
    }

    private PluginCommand registerMainCommand(PortalCommand portalCommand) {
        PluginCommand command = null;
        try {
            command = getCommand("simpleportals");
        } catch (UnsupportedOperationException ignored) {
        }
        if (command == null) {
            command = createFallbackCommand();
            fallbackCommandRegistered = command != null;
        }
        if (command == null) {
            return null;
        }
        command.setExecutor(portalCommand);
        command.setTabCompleter(portalCommand);
        return command;
    }

    private PluginCommand createFallbackCommand() {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance("simpleportals", this);
            command.setAliases(List.of("portal"));
            command.setDescription("Main command for SimplePortals");
            command.setUsage("/<command> help");
            CommandMap commandMap = resolveCommandMap();
            if (commandMap == null) {
                return null;
            }
            commandMap.register(getDescription().getName().toLowerCase(Locale.ROOT), command);
            syncCommands();
            return command;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            getLogger().severe("Could not create fallback command registration: " + exception.getMessage());
            return null;
        }
    }

    private CommandMap resolveCommandMap() {
        try {
            Method method = getServer().getClass().getMethod("getCommandMap");
            Object result = method.invoke(getServer());
            return result instanceof CommandMap commandMap ? commandMap : null;
        } catch (ReflectiveOperationException exception) {
            getLogger().severe("Could not access the server command map: " + exception.getMessage());
            return null;
        }
    }

    private void syncCommands() {
        try {
            Method method = getServer().getClass().getMethod("syncCommands");
            method.invoke(getServer());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void unregisterFallbackCommand() {
        if (!fallbackCommandRegistered || registeredCommand == null) {
            return;
        }
        CommandMap commandMap = resolveCommandMap();
        if (commandMap != null) {
            registeredCommand.unregister(commandMap);
            syncCommands();
        }
        registeredCommand = null;
        fallbackCommandRegistered = false;
    }

    public void sendUpdateNotification(Player player) {
        if (updateChecker != null) {
            updateChecker.sendUpdateNotification(player);
        }
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public PortalStore getPortalStore() {
        return portalStore;
    }

    public SelectionService getSelectionService() {
        return selectionService;
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }

    public String getRegisteredProxyChannel() {
        return registeredProxyChannel;
    }

    public ServerCompatibility getCompatibility() {
        return compatibility;
    }

    @Override
    public Collection<PortalDefinition> getPortals() {
        return portalStore.getPortals();
    }

    @Override
    public Collection<DestinationDefinition> getDestinations() {
        return portalStore.getDestinations();
    }

    @Override
    public Optional<PortalDefinition> getPortal(String name) {
        return portalStore.getPortal(name);
    }

    @Override
    public Optional<DestinationDefinition> getDestination(String name) {
        return portalStore.getDestination(name);
    }

    @Override
    public boolean usePortal(Player player, String portalName) {
        return teleportService.usePortal(player, portalName);
    }
}