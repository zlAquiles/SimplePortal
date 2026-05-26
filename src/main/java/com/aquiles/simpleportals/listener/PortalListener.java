package com.aquiles.simpleportals.listener;

import com.aquiles.simpleportals.SimplePortalsPlugin;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.PortalTrigger;
import com.aquiles.simpleportals.data.SelectionSession;
import com.aquiles.simpleportals.service.PortalStore;
import com.aquiles.simpleportals.service.SelectionService;
import com.aquiles.simpleportals.service.TeleportService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;

public final class PortalListener implements Listener {

    private final SimplePortalsPlugin plugin;
    private final ConfigService configService;
    private final PortalStore portalStore;
    private final SelectionService selectionService;
    private final TeleportService teleportService;
    private final Map<UUID, Long> lavaDamageProtection = new ConcurrentHashMap<>();

    public PortalListener(
        SimplePortalsPlugin plugin,
        ConfigService configService,
        PortalStore portalStore,
        SelectionService selectionService,
        TeleportService teleportService
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.portalStore = portalStore;
        this.selectionService = selectionService;
        this.teleportService = teleportService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || event.getItem() == null) {
            return;
        }
        if (!configService.isWand(event.getItem(), plugin.getWandKey())) {
            return;
        }

        SelectionSession session = selectionService.getSession(event.getPlayer());
        if (!session.isSelectorEnabled()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        boolean selectionChanged = true;
        if (action == Action.LEFT_CLICK_BLOCK) {
            int point = selectionService.addPosition(event.getPlayer(), event.getClickedBlock().getLocation());
            configService.send(event.getPlayer(), "status.selection_point",
                "point", Integer.toString(point),
                "x", Integer.toString(event.getClickedBlock().getX()),
                "y", Integer.toString(event.getClickedBlock().getY()),
                "z", Integer.toString(event.getClickedBlock().getZ())
            );
        } else {
            int point = selectionService.removePosition(event.getPlayer(), event.getClickedBlock().getLocation());
            if (point == 0) {
                selectionChanged = false;
                configService.send(event.getPlayer(), "errors.selection_point_missing");
            } else {
                configService.send(event.getPlayer(), "status.selection_point_removed",
                    "point", Integer.toString(point),
                    "x", Integer.toString(event.getClickedBlock().getX()),
                    "y", Integer.toString(event.getClickedBlock().getY()),
                    "z", Integer.toString(event.getClickedBlock().getZ())
                );
            }
        }

        if (selectionChanged && selectionService.hasCompleteSelection(event.getPlayer())) {
            if (selectionService.hasWorldMismatch(event.getPlayer())) {
                configService.send(event.getPlayer(), "errors.selection_world_mismatch");
            } else {
                configService.send(event.getPlayer(), "status.selection_ready");
            }
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalFluidFlow(BlockFromToEvent event) {
        if (!portalStore.isProtectedFluidBlock(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalFluidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PortalTrigger trigger = damagingFluid(event.getCause());
        if (trigger == null || !isProtectedFromFluidDamage(player, trigger)) {
            return;
        }
        event.setCancelled(true);
        if (trigger == PortalTrigger.LAVA) {
            player.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalFluidCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isProtectedFromFluidDamage(player, PortalTrigger.LAVA)) {
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        if (event.getPlayer().getFireTicks() > 0 && isTouchingFluidPortal(event.getPlayer(), PortalTrigger.LAVA)) {
            protectFromLavaDamage(event.getPlayer());
            event.getPlayer().setFireTicks(0);
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (isTouchingFluidPortal(event.getPlayer(), PortalTrigger.LAVA)) {
            protectFromLavaDamage(event.getPlayer());
            event.getPlayer().setFireTicks(0);
        }
        teleportService.handleMove(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        selectionService.hideMarkersFrom(event.getPlayer());
        plugin.sendUpdateNotification(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectionService.clear(event.getPlayer());
        teleportService.cleanup(event.getPlayer().getUniqueId());
        lavaDamageProtection.remove(event.getPlayer().getUniqueId());
    }

    private PortalTrigger damagingFluid(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case LAVA, FIRE, FIRE_TICK, HOT_FLOOR -> PortalTrigger.LAVA;
            case DROWNING -> PortalTrigger.WATER;
            default -> null;
        };
    }

    private boolean isProtectedFromFluidDamage(Player player, PortalTrigger trigger) {
        if (isTouchingFluidPortal(player, trigger)) {
            if (trigger == PortalTrigger.LAVA) {
                protectFromLavaDamage(player);
            }
            return true;
        }
        return trigger == PortalTrigger.LAVA && hasLavaProtection(player);
    }

    private boolean isInsideFluidPortal(Player player, PortalTrigger trigger) {
        return portalStore.isInsideFluidPortal(player.getLocation(), trigger)
            || portalStore.isInsideFluidPortal(player.getEyeLocation(), trigger)
            || portalStore.isFluidPortalBlock(player.getLocation().getBlock(), trigger)
            || portalStore.isFluidPortalBlock(player.getEyeLocation().getBlock(), trigger);
    }

    private boolean isTouchingFluidPortal(Player player, PortalTrigger trigger) {
        if (isInsideFluidPortal(player, trigger)) {
            return true;
        }
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        BoundingBox box = player.getBoundingBox().expand(0.08D, 0.05D, 0.08D);
        int minX = blockCoordinate(box.getMinX());
        int maxX = blockCoordinate(box.getMaxX());
        int minY = blockCoordinate(box.getMinY());
        int maxY = blockCoordinate(box.getMaxY());
        int minZ = blockCoordinate(box.getMinZ());
        int maxZ = blockCoordinate(box.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (portalStore.isFluidPortalBlock(world.getBlockAt(x, y, z), trigger)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int blockCoordinate(double value) {
        return (int) Math.floor(value);
    }

    private void protectFromLavaDamage(Player player) {
        lavaDamageProtection.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
    }

    private boolean hasLavaProtection(Player player) {
        long expiresAt = lavaDamageProtection.getOrDefault(player.getUniqueId(), 0L);
        if (expiresAt <= System.currentTimeMillis()) {
            lavaDamageProtection.remove(player.getUniqueId());
            return false;
        }
        return true;
    }
}
