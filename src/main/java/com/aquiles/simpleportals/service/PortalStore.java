package com.aquiles.simpleportals.service;

import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.Cuboid;
import com.aquiles.simpleportals.data.DestinationDefinition;
import com.aquiles.simpleportals.data.PortalDefinition;
import com.aquiles.simpleportals.data.PortalTrigger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PortalStore {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final File portalFile;
    private final Map<String, DestinationDefinition> destinations = new LinkedHashMap<>();
    private final Map<String, PortalDefinition> portals = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<PortalDefinition>>> portalsByChunk = new LinkedHashMap<>();

    public PortalStore(JavaPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.portalFile = new File(plugin.getDataFolder(), "portals.yml");
    }

    public void load() {
        destinations.clear();
        portals.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(portalFile);

        ConfigurationSection destinationSection = yaml.getConfigurationSection("destinations");
        if (destinationSection != null) {
            for (String name : destinationSection.getKeys(false)) {
                ConfigurationSection section = destinationSection.getConfigurationSection(name);
                if (section == null) {
                    continue;
                }
                DestinationDefinition.Type type = DestinationDefinition.Type.valueOf(section.getString("type", "LOCAL").toUpperCase(Locale.ROOT));
                DestinationDefinition destination = type == DestinationDefinition.Type.PROXY
                    ? DestinationDefinition.proxy(name, section.getString("server", ""))
                    : new DestinationDefinition(
                        name,
                        DestinationDefinition.Type.LOCAL,
                        section.getString("world", "world"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        (float) section.getDouble("yaw"),
                        (float) section.getDouble("pitch"),
                        ""
                    );
                destinations.put(normalize(name), destination);
            }
        }

        ConfigurationSection portalSection = yaml.getConfigurationSection("portals");
        if (portalSection != null) {
            for (String name : portalSection.getKeys(false)) {
                ConfigurationSection section = portalSection.getConfigurationSection(name);
                if (section == null) {
                    continue;
                }
                ConfigurationSection min = section.getConfigurationSection("min");
                ConfigurationSection max = section.getConfigurationSection("max");
                if (min == null || max == null) {
                    continue;
                }
                List<PortalTrigger> triggerBlocks = section.getStringList("trigger-blocks").stream()
                    .map(PortalTrigger::fromInput)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toCollection(ArrayList::new));
                if (triggerBlocks.isEmpty()) {
                    PortalTrigger.fromInput(configService.defaultTriggerName()).ifPresent(triggerBlocks::add);
                }
                PortalDefinition portal = new PortalDefinition(
                    name,
                    section.getString("destination", ""),
                    new Cuboid(
                        section.getString("world", "world"),
                        min.getInt("x"),
                        min.getInt("y"),
                        min.getInt("z"),
                        max.getInt("x"),
                        max.getInt("y"),
                        max.getInt("z")
                    ),
                    section.getBoolean("enabled", true),
                    triggerBlocks,
                    Math.max(0, section.getInt("settings.cooldown-seconds", configService.defaultCooldownSeconds())),
                    section.getString("settings.required-permission", ""),
                    new PortalDefinition.Conditions(
                        section.getBoolean("conditions.enabled", false),
                        section.getBoolean("conditions.sneaking-required", false),
                        section.getString("conditions.deny-message", "")
                    ),
                    new PortalDefinition.Actions(
                        new PortalDefinition.MessageAction(
                            section.getBoolean("actions.message.enabled", false),
                            section.getString("actions.message.text", "")
                        ),
                        new PortalDefinition.TitleAction(
                            section.getBoolean("actions.title.enabled", false),
                            section.getString("actions.title.title", ""),
                            section.getString("actions.title.subtitle", ""),
                            section.getInt("actions.title.fade-in", 10),
                            section.getInt("actions.title.stay", 40),
                            section.getInt("actions.title.fade-out", 10)
                        ),
                        new PortalDefinition.SoundAction(
                            section.getBoolean("actions.sound.enabled", false),
                            section.getString("actions.sound.sound", "entity.enderman.teleport"),
                            (float) section.getDouble("actions.sound.volume", 1.0D),
                            (float) section.getDouble("actions.sound.pitch", 1.0D)
                        ),
                        new PortalDefinition.BossBarAction(
                            section.getBoolean("actions.bossbar.enabled", false),
                            section.getString("actions.bossbar.text", ""),
                            section.getString("actions.bossbar.color", "BLUE"),
                            section.getString("actions.bossbar.style", "SOLID"),
                            section.getInt("actions.bossbar.seconds", 3)
                        ),
                        new PortalDefinition.CommandAction(
                            section.getBoolean("actions.commands.enabled", false),
                            section.getBoolean("actions.commands.run-as-console", true),
                            section.getStringList("actions.commands.list")
                        ),
                        loadEffectsAction(section)
                    )
                );
                portals.put(normalize(name), portal);
            }
        }

        rebuildIndex();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection destinationSection = yaml.createSection("destinations");
        for (DestinationDefinition destination : destinations.values()) {
            ConfigurationSection section = destinationSection.createSection(destination.name());
            section.set("type", destination.type().name());
            if (destination.type() == DestinationDefinition.Type.PROXY) {
                section.set("server", destination.serverName());
            } else {
                section.set("world", destination.worldName());
                section.set("x", destination.x());
                section.set("y", destination.y());
                section.set("z", destination.z());
                section.set("yaw", destination.yaw());
                section.set("pitch", destination.pitch());
            }
        }

        ConfigurationSection portalSection = yaml.createSection("portals");
        for (PortalDefinition portal : portals.values()) {
            ConfigurationSection section = portalSection.createSection(portal.name());
            section.set("enabled", portal.enabled());
            section.set("destination", portal.destinationName());
            section.set("world", portal.region().worldName());
            section.set("trigger-blocks", portal.triggerBlocks().stream().map(Enum::name).toList());
            section.set("min.x", portal.region().minX());
            section.set("min.y", portal.region().minY());
            section.set("min.z", portal.region().minZ());
            section.set("max.x", portal.region().maxX());
            section.set("max.y", portal.region().maxY());
            section.set("max.z", portal.region().maxZ());
            section.set("settings.cooldown-seconds", portal.cooldownSeconds());
            section.set("settings.required-permission", portal.requiredPermission());
            section.set("conditions.enabled", portal.conditions().enabled());
            section.set("conditions.sneaking-required", portal.conditions().sneakingRequired());
            section.set("conditions.deny-message", portal.conditions().denyMessage());
            section.set("actions.message.enabled", portal.actions().message().enabled());
            section.set("actions.message.text", portal.actions().message().text());
            section.set("actions.title.enabled", portal.actions().title().enabled());
            section.set("actions.title.title", portal.actions().title().title());
            section.set("actions.title.subtitle", portal.actions().title().subtitle());
            section.set("actions.title.fade-in", portal.actions().title().fadeIn());
            section.set("actions.title.stay", portal.actions().title().stay());
            section.set("actions.title.fade-out", portal.actions().title().fadeOut());
            section.set("actions.sound.enabled", portal.actions().sound().enabled());
            section.set("actions.sound.sound", portal.actions().sound().sound());
            section.set("actions.sound.volume", portal.actions().sound().volume());
            section.set("actions.sound.pitch", portal.actions().sound().pitch());
            section.set("actions.bossbar.enabled", portal.actions().bossBar().enabled());
            section.set("actions.bossbar.text", portal.actions().bossBar().text());
            section.set("actions.bossbar.color", portal.actions().bossBar().color());
            section.set("actions.bossbar.style", portal.actions().bossBar().style());
            section.set("actions.bossbar.seconds", portal.actions().bossBar().seconds());
            section.set("actions.commands.enabled", portal.actions().commands().enabled());
            section.set("actions.commands.run-as-console", portal.actions().commands().runAsConsole());
            section.set("actions.commands.list", portal.actions().commands().commands());
            section.set("actions.effects.enabled", portal.actions().effects().enabled());
            section.set("actions.effects.list", serializeEffects(portal.actions().effects()));
        }

        try {
            yaml.save(portalFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save portals.yml: " + exception.getMessage());
        }
    }

    public boolean createDestination(DestinationDefinition destination) {
        String key = normalize(destination.name());
        if (destinations.containsKey(key)) {
            return false;
        }
        destinations.put(key, destination);
        save();
        return true;
    }

    public boolean createPortal(PortalDefinition portal) {
        String key = normalize(portal.name());
        if (portals.containsKey(key)) {
            return false;
        }
        portals.put(key, portal);
        rebuildIndex();
        save();
        return true;
    }

    public boolean removePortal(String name) {
        if (portals.remove(normalize(name)) == null) {
            return false;
        }
        rebuildIndex();
        save();
        return true;
    }

    public boolean updatePortalTriggers(String name, List<PortalTrigger> triggerBlocks) {
        String key = normalize(name);
        PortalDefinition existing = portals.get(key);
        if (existing == null) {
            return false;
        }
        PortalDefinition updated = new PortalDefinition(
            existing.name(),
            existing.destinationName(),
            existing.region(),
            existing.enabled(),
            triggerBlocks,
            existing.cooldownSeconds(),
            existing.requiredPermission(),
            existing.conditions(),
            existing.actions()
        );
        portals.put(key, updated);
        rebuildIndex();
        save();
        return true;
    }

    public boolean removeDestination(String name) {
        if (destinations.remove(normalize(name)) == null) {
            return false;
        }
        save();
        return true;
    }

    public boolean hasPortal(String name) {
        return portals.containsKey(normalize(name));
    }

    public boolean hasDestination(String name) {
        return destinations.containsKey(normalize(name));
    }

    public boolean isDestinationInUse(String name) {
        String normalized = normalize(name);
        return portals.values().stream().anyMatch(portal -> normalize(portal.destinationName()).equals(normalized));
    }

    public String nextPortalName() {
        int index = 1;
        while (hasPortal("portal-" + index)) {
            index++;
        }
        return "portal-" + index;
    }

    public Optional<PortalDefinition> getPortal(String name) {
        return Optional.ofNullable(portals.get(normalize(name)));
    }

    public Optional<DestinationDefinition> getDestination(String name) {
        return Optional.ofNullable(destinations.get(normalize(name)));
    }

    public Collection<PortalDefinition> getPortals() {
        return List.copyOf(portals.values());
    }

    public Collection<DestinationDefinition> getDestinations() {
        return List.copyOf(destinations.values());
    }

    public List<PortalDefinition> getPortalsInChunk(Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        Map<Long, List<PortalDefinition>> worldIndex = portalsByChunk.get(location.getWorld().getName().toLowerCase(Locale.ROOT));
        if (worldIndex == null) {
            return List.of();
        }
        return worldIndex.getOrDefault(chunkKey(location.getChunk().getX(), location.getChunk().getZ()), List.of());
    }

    public List<PortalDefinition> getNearbyPortals(Location location, double radius) {
        double radiusSquared = radius * radius;
        return portals.values().stream()
            .filter(portal -> portal.distanceSquared(location) <= radiusSquared)
            .sorted(Comparator.comparingDouble(portal -> portal.distanceSquared(location)))
            .toList();
    }

    private PortalDefinition.EffectsAction loadEffectsAction(ConfigurationSection section) {
        boolean enabled = section.getBoolean("actions.effects.enabled", false);
        List<PortalDefinition.EffectEntry> effects = new ArrayList<>();
        for (Map<?, ?> rawEntry : section.getMapList("actions.effects.list")) {
            String type = asString(rawEntry.get("type"), "");
            if (type.isBlank()) {
                continue;
            }
            Object durationValue = rawEntry.containsKey("duration-seconds") ? rawEntry.get("duration-seconds") : rawEntry.get("duration");
            effects.add(new PortalDefinition.EffectEntry(
                type,
                asInt(durationValue, 3),
                asInt(rawEntry.get("amplifier"), 0),
                asBoolean(rawEntry.get("ambient"), false),
                asBoolean(rawEntry.get("particles"), true),
                asBoolean(rawEntry.get("icon"), true)
            ));
        }
        return new PortalDefinition.EffectsAction(enabled, effects);
    }

    private List<Map<String, Object>> serializeEffects(PortalDefinition.EffectsAction effectsAction) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (PortalDefinition.EffectEntry effect : effectsAction.list()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", effect.type());
            entry.put("duration-seconds", effect.durationSeconds());
            entry.put("amplifier", effect.amplifier());
            entry.put("ambient", effect.ambient());
            entry.put("particles", effect.particles());
            entry.put("icon", effect.icon());
            serialized.add(entry);
        }
        return serialized;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string.trim());
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private void rebuildIndex() {
        portalsByChunk.clear();
        for (PortalDefinition portal : portals.values()) {
            Map<Long, List<PortalDefinition>> worldIndex = portalsByChunk.computeIfAbsent(
                portal.region().worldName().toLowerCase(Locale.ROOT),
                ignored -> new LinkedHashMap<>()
            );
            for (int chunkX = portal.region().minChunkX(); chunkX <= portal.region().maxChunkX(); chunkX++) {
                for (int chunkZ = portal.region().minChunkZ(); chunkZ <= portal.region().maxChunkZ(); chunkZ++) {
                    worldIndex.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(portal);
                }
            }
        }
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT);
    }
}