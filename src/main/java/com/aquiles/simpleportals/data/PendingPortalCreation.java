package com.aquiles.simpleportals.data;

public record PendingPortalCreation(
    String portalName,
    String destinationName,
    boolean enabled,
    java.util.List<PortalTrigger> triggerBlocks,
    int cooldownSeconds,
    String requiredPermission,
    PortalDefinition.Conditions conditions,
    PortalDefinition.Actions actions
) {

    public PortalDefinition toPortal(Cuboid region) {
        return new PortalDefinition(
            portalName,
            destinationName,
            region,
            enabled,
            triggerBlocks,
            cooldownSeconds,
            requiredPermission,
            conditions,
            actions
        );
    }
}
