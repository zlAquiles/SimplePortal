package com.aquiles.simpleportals.data;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;

public record PortalDefinition(
    String name,
    String destinationName,
    Cuboid region,
    boolean enabled,
    List<PortalTrigger> triggerBlocks,
    int cooldownSeconds,
    String requiredPermission,
    Conditions conditions,
    Actions actions,
    List<BlockPoint> blocks
) {

    public PortalDefinition(
        String name,
        String destinationName,
        Cuboid region,
        boolean enabled,
        List<PortalTrigger> triggerBlocks,
        int cooldownSeconds,
        String requiredPermission,
        Conditions conditions,
        Actions actions
    ) {
        this(name, destinationName, region, enabled, triggerBlocks, cooldownSeconds, requiredPermission, conditions, actions, List.of());
    }

    public PortalDefinition {
        triggerBlocks = List.copyOf(triggerBlocks);
        requiredPermission = requiredPermission == null ? "" : requiredPermission;
        conditions = conditions == null ? Conditions.disabled() : conditions;
        actions = actions == null ? Actions.defaults() : actions;
        blocks = List.copyOf(blocks == null ? List.of() : blocks);
    }

    public boolean matches(Location feet, Location head, Block feetBlock, Block headBlock) {
        if (!enabled || (!contains(feet) && !contains(head))) {
            return false;
        }
        return triggerBlocks.stream().anyMatch(trigger -> trigger.matches(feetBlock) || trigger.matches(headBlock));
    }

    public boolean hasDiscreteBlocks() {
        return !blocks.isEmpty();
    }

    public boolean contains(Location location) {
        if (!hasDiscreteBlocks()) {
            return region.contains(location);
        }
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(region.worldName())) {
            return false;
        }
        return blocks.contains(BlockPoint.from(location));
    }

    public boolean contains(Block block) {
        if (!hasDiscreteBlocks()) {
            return region.contains(block);
        }
        if (block == null || block.getWorld() == null || !block.getWorld().getName().equalsIgnoreCase(region.worldName())) {
            return false;
        }
        return blocks.contains(BlockPoint.from(block));
    }

    public double distanceSquared(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(region.worldName())) {
            return Double.MAX_VALUE;
        }
        if (hasDiscreteBlocks()) {
            return blocks.stream()
                .mapToDouble(block -> block.distanceSquared(location))
                .min()
                .orElse(Double.MAX_VALUE);
        }
        return region.center(location.getWorld()).distanceSquared(location);
    }

    public record Conditions(boolean enabled, boolean sneakingRequired, String denyMessage) {

        public Conditions {
            denyMessage = denyMessage == null ? "" : denyMessage;
        }

        public static Conditions disabled() {
            return new Conditions(false, false, "");
        }
    }

    public record Actions(
        MessageAction message,
        TitleAction title,
        SoundAction sound,
        BossBarAction bossBar,
        CommandAction commands,
        EffectsAction effects
    ) {

        public Actions {
            message = message == null ? MessageAction.disabled() : message;
            title = title == null ? TitleAction.disabled() : title;
            sound = sound == null ? SoundAction.disabled() : sound;
            bossBar = bossBar == null ? BossBarAction.disabled() : bossBar;
            commands = commands == null ? CommandAction.disabled() : commands;
            effects = effects == null ? EffectsAction.disabled() : effects;
        }

        public static Actions defaults() {
            return new Actions(
                MessageAction.disabled(),
                TitleAction.disabled(),
                SoundAction.disabled(),
                BossBarAction.disabled(),
                CommandAction.disabled(),
                EffectsAction.disabled()
            );
        }
    }

    public record MessageAction(boolean enabled, String text) {

        public MessageAction {
            text = text == null ? "" : text;
        }

        public static MessageAction disabled() {
            return new MessageAction(false, "");
        }
    }

    public record TitleAction(boolean enabled, String title, String subtitle, int fadeIn, int stay, int fadeOut) {

        public TitleAction {
            title = title == null ? "" : title;
            subtitle = subtitle == null ? "" : subtitle;
        }

        public static TitleAction disabled() {
            return new TitleAction(false, "", "", 10, 40, 10);
        }
    }

    public record SoundAction(boolean enabled, String sound, float volume, float pitch) {

        public SoundAction {
            sound = sound == null ? "entity.enderman.teleport" : sound;
        }

        public static SoundAction disabled() {
            return new SoundAction(false, "entity.enderman.teleport", 1.0F, 1.0F);
        }
    }

    public record BossBarAction(boolean enabled, String text, String color, String style, int seconds) {

        public BossBarAction {
            text = text == null ? "" : text;
            color = color == null ? "BLUE" : color;
            style = style == null ? "SOLID" : style;
        }

        public static BossBarAction disabled() {
            return new BossBarAction(false, "", "BLUE", "SOLID", 3);
        }
    }

    public record CommandAction(boolean enabled, boolean runAsConsole, List<String> commands) {

        public CommandAction {
            commands = List.copyOf(commands == null ? List.of() : commands);
        }

        public static CommandAction disabled() {
            return new CommandAction(false, true, List.of());
        }
    }

    public record EffectsAction(boolean enabled, List<EffectEntry> list) {

        public EffectsAction {
            list = List.copyOf(list == null ? List.of() : list);
        }

        public static EffectsAction disabled() {
            return new EffectsAction(false, List.of());
        }
    }

    public record EffectEntry(String type, int durationSeconds, int amplifier, boolean ambient, boolean particles, boolean icon) {

        public EffectEntry {
            type = type == null ? "" : type;
            durationSeconds = Math.max(1, durationSeconds);
            amplifier = Math.max(0, amplifier);
        }
    }
}
