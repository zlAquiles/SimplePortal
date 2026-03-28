package com.aquiles.simpleportals.service;

import com.aquiles.simpleportals.SimplePortalsPlugin;
import com.aquiles.simpleportals.api.event.SimplePortalUseEvent;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.DestinationDefinition;
import com.aquiles.simpleportals.data.PortalDefinition;
import com.aquiles.simpleportals.util.ServerCompatibility;
import com.aquiles.simpleportals.util.Text;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class TeleportService {

    private final SimplePortalsPlugin plugin;
    private final ConfigService configService;
    private final PortalStore portalStore;
    private final ServerCompatibility compatibility;
    private final Map<UUID, Long> reentryProtection = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> portalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final Set<UUID> pendingTeleports = ConcurrentHashMap.newKeySet();

    public TeleportService(SimplePortalsPlugin plugin, ConfigService configService, PortalStore portalStore) {
        this.plugin = plugin;
        this.configService = configService;
        this.portalStore = portalStore;
        this.compatibility = plugin.getCompatibility();
    }

    public void handleMove(Player player, Location to) {
        UUID uniqueId = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (pendingTeleports.contains(uniqueId) || reentryProtection.getOrDefault(uniqueId, 0L) > now) {
            return;
        }
        Block feet = to.getBlock();
        Location headLocation = to.clone().add(0.0D, 1.0D, 0.0D);
        Block head = headLocation.getBlock();
        for (PortalDefinition portal : portalStore.getPortalsInChunk(to)) {
            if (portal.matches(to, headLocation, feet, head) && usePortal(player, portal)) {
                return;
            }
        }
    }

    public boolean usePortal(Player player, String portalName) {
        return portalStore.getPortal(portalName).map(portal -> usePortal(player, portal)).orElse(false);
    }

    public boolean teleportToDestination(Player player, String destinationName) {
        DestinationDefinition destination = portalStore.getDestination(destinationName).orElse(null);
        if (destination == null) {
            configService.send(player, "errors.destination_missing", "destination", destinationName);
            return false;
        }
        transportPlayer(player, destination).thenAccept(success -> {
            if (!success) {
                return;
            }
            compatibility.runPlayer(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                protectFromImmediateReentry(player);
                configService.send(player, "status.destination_teleported", "destination", destination.name());
            });
        });
        return true;
    }

    public boolean usePortal(Player player, PortalDefinition portal) {
        if (!portal.enabled()) {
            return false;
        }
        if (!player.hasPermission("simpleportals.use")) {
            configService.send(player, "errors.no_permission");
            return false;
        }
        if (!portal.requiredPermission().isBlank() && !player.hasPermission(portal.requiredPermission())) {
            sendDenyMessage(player, portal);
            return false;
        }
        if (portal.conditions().enabled() && portal.conditions().sneakingRequired() && !player.isSneaking()) {
            configService.send(player, "errors.sneaking_required");
            return false;
        }

        long now = System.currentTimeMillis();
        long availableAt = portalCooldowns
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .getOrDefault(portal.name().toLowerCase(Locale.ROOT), 0L);
        if (availableAt > now) {
            long seconds = Math.max(1L, (availableAt - now + 999L) / 1000L);
            configService.send(player, "errors.cooldown", "seconds", Long.toString(seconds), "portal", portal.name());
            return false;
        }

        DestinationDefinition destination = portalStore.getDestination(portal.destinationName()).orElse(null);
        if (destination == null) {
            configService.send(player, "errors.destination_missing", "destination", portal.destinationName());
            return false;
        }

        SimplePortalUseEvent event = new SimplePortalUseEvent(player, portal, destination);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        if (!pendingTeleports.add(player.getUniqueId())) {
            return false;
        }

        transportPlayer(player, destination).whenComplete((success, exception) -> {
            pendingTeleports.remove(player.getUniqueId());
            if (exception != null || !Boolean.TRUE.equals(success)) {
                return;
            }
            compatibility.runPlayer(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                completePortalUse(player, portal, destination, now);
            });
        });
        return true;
    }

    public void cleanup(UUID uniqueId) {
        reentryProtection.remove(uniqueId);
        portalCooldowns.remove(uniqueId);
        pendingTeleports.remove(uniqueId);
        hideBossBar(uniqueId);
    }

    public void shutdown() {
        for (UUID uniqueId : List.copyOf(activeBars.keySet())) {
            hideBossBar(uniqueId);
        }
        reentryProtection.clear();
        portalCooldowns.clear();
        pendingTeleports.clear();
    }

    private void completePortalUse(Player player, PortalDefinition portal, DestinationDefinition destination, long startedAt) {
        executeCommands(player, portal, destination);
        applyFeedback(player, portal, destination);
        protectFromImmediateReentry(player);
        if (portal.cooldownSeconds() > 0) {
            portalCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(portal.name().toLowerCase(Locale.ROOT), startedAt + (portal.cooldownSeconds() * 1000L));
        }
    }

    private void sendDenyMessage(Player player, PortalDefinition portal) {
        if (portal.conditions().enabled() && !portal.conditions().denyMessage().isBlank()) {
            configService.sendText(player, configService.prefix() + applyPlaceholders(portal.conditions().denyMessage(), player, portal, null));
            return;
        }
        configService.send(player, "errors.use_denied");
    }

    private CompletableFuture<Boolean> transportPlayer(Player player, DestinationDefinition destination) {
        if (destination.type() == DestinationDefinition.Type.LOCAL) {
            World world = Bukkit.getWorld(destination.worldName());
            if (world == null) {
                configService.send(player, "errors.destination_world_missing", "destination", destination.name());
                return CompletableFuture.completedFuture(false);
            }
            return compatibility.teleportEntityFuture(player, destination.toLocation(world));
        }
        return CompletableFuture.completedFuture(connectToProxy(player, destination.serverName()));
    }

    private void protectFromImmediateReentry(Player player) {
        reentryProtection.put(player.getUniqueId(), System.currentTimeMillis() + configService.reentryProtectionMillis());
    }

    private void executeCommands(Player player, PortalDefinition portal, DestinationDefinition destination) {
        PortalDefinition.CommandAction commandAction = portal.actions().commands();
        if (!commandAction.enabled() || commandAction.commands().isEmpty()) {
            return;
        }
        for (String rawCommand : commandAction.commands()) {
            String command = applyPlaceholders(rawCommand, player, portal, destination);
            if (command.isBlank()) {
                continue;
            }
            CommandSender source = commandAction.runAsConsole() ? Bukkit.getConsoleSender() : player;
            Bukkit.dispatchCommand(source, Text.stripLeadingSlash(command));
        }
    }

    private void applyFeedback(Player player, PortalDefinition portal, DestinationDefinition destination) {
        PortalDefinition.MessageAction messageAction = portal.actions().message();
        if (messageAction.enabled() && !messageAction.text().isBlank()) {
            configService.sendText(player, applyPlaceholders(messageAction.text(), player, portal, destination));
        }

        PortalDefinition.TitleAction titleAction = portal.actions().title();
        if (titleAction.enabled()) {
            player.showTitle(Title.title(
                Text.component(applyPlaceholders(titleAction.title(), player, portal, destination)),
                Text.component(applyPlaceholders(titleAction.subtitle(), player, portal, destination)),
                Title.Times.times(
                    Duration.ofMillis(titleAction.fadeIn() * 50L),
                    Duration.ofMillis(titleAction.stay() * 50L),
                    Duration.ofMillis(titleAction.fadeOut() * 50L)
                )
            ));
        }

        PortalDefinition.SoundAction soundAction = portal.actions().sound();
        if (soundAction.enabled()) {
            compatibility.runPlayerLater(player, 1L, () -> playConfiguredSound(player, portal, soundAction));
        }

        PortalDefinition.EffectsAction effectsAction = portal.actions().effects();
        if (effectsAction.enabled() && !effectsAction.list().isEmpty()) {
            applyConfiguredEffects(player, portal, effectsAction);
        }

        PortalDefinition.BossBarAction bossBarAction = portal.actions().bossBar();
        if (bossBarAction.enabled() && !bossBarAction.text().isBlank()) {
            hideBossBar(player.getUniqueId());
            BossBar bossBar = BossBar.bossBar(
                Text.component(applyPlaceholders(bossBarAction.text(), player, portal, destination)),
                1.0F,
                parseBarColor(bossBarAction.color()),
                parseBarOverlay(bossBarAction.style())
            );
            player.showBossBar(bossBar);
            activeBars.put(player.getUniqueId(), bossBar);
            compatibility.runPlayerLater(player, Math.max(1, bossBarAction.seconds()) * 20L, () -> hideBossBar(player.getUniqueId()));
        }
    }

    private void playConfiguredSound(Player player, PortalDefinition portal, PortalDefinition.SoundAction soundAction) {
        String soundKey = normalizeSoundKey(soundAction.sound());
        if (soundKey.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), soundKey, SoundCategory.MASTER, soundAction.volume(), soundAction.pitch());
        } catch (Exception exception) {
            plugin.getLogger().warning("Invalid sound in portal " + portal.name() + ": " + soundAction.sound());
        }
    }

    private void applyConfiguredEffects(Player player, PortalDefinition portal, PortalDefinition.EffectsAction effectsAction) {
        for (PortalDefinition.EffectEntry effect : effectsAction.list()) {
            PotionEffectType type = resolvePotionEffectType(effect.type());
            if (type == null) {
                plugin.getLogger().warning("Invalid potion effect in portal " + portal.name() + ": " + effect.type());
                continue;
            }
            player.addPotionEffect(new PotionEffect(
                type,
                Math.max(1, effect.durationSeconds()) * 20,
                Math.max(0, effect.amplifier()),
                effect.ambient(),
                effect.particles(),
                effect.icon()
            ), true);
        }
    }

    private PotionEffectType resolvePotionEffectType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedName = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        PotionEffectType byName = PotionEffectType.getByName(normalizedName);
        if (byName != null) {
            return byName;
        }

        String keyName = value.trim().toLowerCase(Locale.ROOT);
        if (keyName.startsWith("minecraft:")) {
            keyName = keyName.substring("minecraft:".length());
        }
        PotionEffectType byKey = PotionEffectType.getByKey(NamespacedKey.minecraft(keyName));
        if (byKey != null) {
            return byKey;
        }

        return switch (keyName) {
            case "slowness" -> PotionEffectType.getByName("SLOW");
            case "haste" -> PotionEffectType.getByName("FAST_DIGGING");
            case "mining_fatigue" -> PotionEffectType.getByName("SLOW_DIGGING");
            case "instant_health" -> PotionEffectType.getByName("HEAL");
            case "instant_damage" -> PotionEffectType.getByName("HARM");
            case "jump_boost" -> PotionEffectType.getByName("JUMP");
            case "nausea" -> PotionEffectType.getByName("CONFUSION");
            case "resistance" -> PotionEffectType.getByName("DAMAGE_RESISTANCE");
            case "strength" -> PotionEffectType.getByName("INCREASE_DAMAGE");
            default -> null;
        };
    }

    private String normalizeSoundKey(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        if (normalized.contains(".")) {
            return normalized;
        }
        return normalized.replace('_', '.');
    }

    private void hideBossBar(UUID uniqueId) {
        BossBar bossBar = activeBars.remove(uniqueId);
        if (bossBar == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uniqueId);
        if (player != null && player.isOnline()) {
            player.hideBossBar(bossBar);
        }
    }

    private boolean connectToProxy(Player player, String serverName) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(byteStream);
            output.writeUTF("Connect");
            output.writeUTF(serverName);
            player.sendPluginMessage(plugin, plugin.getRegisteredProxyChannel(), byteStream.toByteArray());
            return true;
        } catch (IOException exception) {
            configService.send(player, "errors.proxy_connect_failed");
            return false;
        }
    }

    private String applyPlaceholders(String input, Player player, PortalDefinition portal, DestinationDefinition destination) {
        Map<String, String> placeholders = Map.of(
            "player", player.getName(),
            "portal", portal.name(),
            "destination", destination == null ? portal.destinationName() : destination.name()
        );
        return Text.replace(input, placeholders);
    }

    private BossBar.Color parseBarColor(String value) {
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.BLUE;
        }
    }

    private BossBar.Overlay parseBarOverlay(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SEGMENTED_6", "NOTCHED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_10", "NOTCHED_10" -> BossBar.Overlay.NOTCHED_10;
            case "SEGMENTED_12", "NOTCHED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20", "NOTCHED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }
}