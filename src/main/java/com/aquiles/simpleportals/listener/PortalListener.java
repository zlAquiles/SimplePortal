package com.aquiles.simpleportals.listener;

import com.aquiles.simpleportals.SimplePortalsPlugin;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.SelectionSession;
import com.aquiles.simpleportals.service.SelectionService;
import com.aquiles.simpleportals.service.TeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PortalListener implements Listener {

    private final SimplePortalsPlugin plugin;
    private final ConfigService configService;
    private final SelectionService selectionService;
    private final TeleportService teleportService;

    public PortalListener(
        SimplePortalsPlugin plugin,
        ConfigService configService,
        SelectionService selectionService,
        TeleportService teleportService
    ) {
        this.plugin = plugin;
        this.configService = configService;
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

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionService.setPosition(event.getPlayer(), true, event.getClickedBlock().getLocation());
            configService.send(event.getPlayer(), "status.selection_pos1",
                "x", Integer.toString(event.getClickedBlock().getX()),
                "y", Integer.toString(event.getClickedBlock().getY()),
                "z", Integer.toString(event.getClickedBlock().getZ())
            );
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionService.setPosition(event.getPlayer(), false, event.getClickedBlock().getLocation());
            configService.send(event.getPlayer(), "status.selection_pos2",
                "x", Integer.toString(event.getClickedBlock().getX()),
                "y", Integer.toString(event.getClickedBlock().getY()),
                "z", Integer.toString(event.getClickedBlock().getZ())
            );
        } else {
            return;
        }

        if (selectionService.hasCompleteSelection(event.getPlayer())) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
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
    }
}