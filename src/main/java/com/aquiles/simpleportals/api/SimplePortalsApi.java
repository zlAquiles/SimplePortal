package com.aquiles.simpleportals.api;

import com.aquiles.simpleportals.data.DestinationDefinition;
import com.aquiles.simpleportals.data.PortalDefinition;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Player;

public interface SimplePortalsApi {

    Collection<PortalDefinition> getPortals();

    Collection<DestinationDefinition> getDestinations();

    Optional<PortalDefinition> getPortal(String name);

    Optional<DestinationDefinition> getDestination(String name);

    boolean usePortal(Player player, String portalName);
}
