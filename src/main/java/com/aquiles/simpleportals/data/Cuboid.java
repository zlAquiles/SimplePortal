package com.aquiles.simpleportals.data;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record Cuboid(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Cuboid {
        int sortedMinX = Math.min(minX, maxX);
        int sortedMinY = Math.min(minY, maxY);
        int sortedMinZ = Math.min(minZ, maxZ);
        int sortedMaxX = Math.max(minX, maxX);
        int sortedMaxY = Math.max(minY, maxY);
        int sortedMaxZ = Math.max(minZ, maxZ);
        minX = sortedMinX;
        minY = sortedMinY;
        minZ = sortedMinZ;
        maxX = sortedMaxX;
        maxY = sortedMaxY;
        maxZ = sortedMaxZ;
    }

    public static Cuboid fromLocations(Location first, Location second) {
        return new Cuboid(
            first.getWorld().getName(),
            first.getBlockX(),
            first.getBlockY(),
            first.getBlockZ(),
            second.getBlockX(),
            second.getBlockY(),
            second.getBlockZ()
        );
    }

    public static Cuboid fromLocations(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("At least one location is required.");
        }
        Location first = locations.get(0);
        int minX = first.getBlockX();
        int minY = first.getBlockY();
        int minZ = first.getBlockZ();
        int maxX = first.getBlockX();
        int maxY = first.getBlockY();
        int maxZ = first.getBlockZ();
        for (Location location : locations) {
            minX = Math.min(minX, location.getBlockX());
            minY = Math.min(minY, location.getBlockY());
            minZ = Math.min(minZ, location.getBlockZ());
            maxX = Math.max(maxX, location.getBlockX());
            maxY = Math.max(maxY, location.getBlockY());
            maxZ = Math.max(maxZ, location.getBlockZ());
        }
        return new Cuboid(first.getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return contains(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        return contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public boolean contains(String targetWorld, int x, int y, int z) {
        return worldName.equalsIgnoreCase(targetWorld)
            && x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public Location center(World world) {
        return new Location(
            world,
            (minX + maxX + 1) / 2.0D,
            (minY + maxY + 1) / 2.0D,
            (minZ + maxZ + 1) / 2.0D
        );
    }

    public int minChunkX() {
        return Math.floorDiv(minX, 16);
    }

    public int maxChunkX() {
        return Math.floorDiv(maxX, 16);
    }

    public int minChunkZ() {
        return Math.floorDiv(minZ, 16);
    }

    public int maxChunkZ() {
        return Math.floorDiv(maxZ, 16);
    }
}
