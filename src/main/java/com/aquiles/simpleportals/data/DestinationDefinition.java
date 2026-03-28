package com.aquiles.simpleportals.data;

import org.bukkit.Location;
import org.bukkit.World;

public record DestinationDefinition(
    String name,
    Type type,
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    String serverName
) {

    public DestinationDefinition {
        worldName = worldName == null ? "" : worldName;
        serverName = serverName == null ? "" : serverName;
    }

    public static DestinationDefinition local(String name, Location location) {
        return new DestinationDefinition(
            name,
            Type.LOCAL,
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            ""
        );
    }

    public static DestinationDefinition proxy(String name, String serverName) {
        return new DestinationDefinition(name, Type.PROXY, "", 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, serverName);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public enum Type {
        LOCAL,
        PROXY
    }
}
