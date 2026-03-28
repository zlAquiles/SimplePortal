package com.aquiles.simpleportals.data;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

public enum PortalTrigger {
    WATER,
    LAVA,
    AIR,
    NETHER_PORTAL,
    END_PORTAL,
    END_GATEWAY;

    public boolean matches(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return switch (this) {
            case WATER -> type == Material.WATER || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
            case LAVA -> type == Material.LAVA;
            case AIR -> type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
            case NETHER_PORTAL -> type == Material.NETHER_PORTAL;
            case END_PORTAL -> type == Material.END_PORTAL;
            case END_GATEWAY -> type == Material.END_GATEWAY;
        };
    }

    public Material placementMaterial() {
        return switch (this) {
            case WATER -> Material.WATER;
            case LAVA -> Material.LAVA;
            case AIR -> Material.AIR;
            case NETHER_PORTAL -> Material.NETHER_PORTAL;
            case END_PORTAL -> Material.END_PORTAL;
            case END_GATEWAY -> Material.END_GATEWAY;
        };
    }

    public static Optional<PortalTrigger> fromInput(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst();
    }

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toList());
    }
}
