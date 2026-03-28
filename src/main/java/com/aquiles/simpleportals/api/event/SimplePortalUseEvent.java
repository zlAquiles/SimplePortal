package com.aquiles.simpleportals.api.event;

import com.aquiles.simpleportals.data.DestinationDefinition;
import com.aquiles.simpleportals.data.PortalDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SimplePortalUseEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PortalDefinition portal;
    private final DestinationDefinition destination;
    private boolean cancelled;

    public SimplePortalUseEvent(Player who, PortalDefinition portal, DestinationDefinition destination) {
        super(who);
        this.portal = portal;
        this.destination = destination;
    }

    public PortalDefinition getPortal() {
        return portal;
    }

    public DestinationDefinition getDestination() {
        return destination;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
