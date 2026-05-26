package com.aquiles.simpleportals.data;

import org.bukkit.Location;
import org.bukkit.block.Block;

public record BlockPoint(int x, int y, int z) {

    public static BlockPoint from(Location location) {
        return new BlockPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPoint from(Block block) {
        return new BlockPoint(block.getX(), block.getY(), block.getZ());
    }

    public boolean matches(Location location) {
        return location != null
            && x == location.getBlockX()
            && y == location.getBlockY()
            && z == location.getBlockZ();
    }

    public double distanceSquared(Location location) {
        double centerX = x + 0.5D;
        double centerY = y + 0.5D;
        double centerZ = z + 0.5D;
        double deltaX = centerX - location.getX();
        double deltaY = centerY - location.getY();
        double deltaZ = centerZ - location.getZ();
        return (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);
    }
}
