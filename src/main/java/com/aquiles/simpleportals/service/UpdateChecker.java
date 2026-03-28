package com.aquiles.simpleportals.service;

import com.aquiles.simpleportals.SimplePortalsPlugin;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.util.ServerCompatibility;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class UpdateChecker {

    private static final String UPDATE_URL = "https://gist.githubusercontent.com/zlAquiles/6820b13e81d7ee869ac638f013496417/raw/version.txt";
    private static final String BUILTBYBIT_URL = "";
    private static final String SPIGOT_URL = "";
    private static final String MODRINTH_URL = "";

    private final SimplePortalsPlugin plugin;
    private final ConfigService configService;
    private final ServerCompatibility compatibility;
    private final AtomicBoolean updateAvailable = new AtomicBoolean(false);
    private volatile String currentVersion = "";
    private volatile String latestVersion = "";

    public UpdateChecker(SimplePortalsPlugin plugin, ConfigService configService, ServerCompatibility compatibility) {
        this.plugin = plugin;
        this.configService = configService;
        this.compatibility = compatibility;
    }

    public void checkForUpdatesAsync() {
        if (!configService.updateCheckEnabled() || UPDATE_URL.isBlank()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String fetchedLatestVersion = fetchLatestVersion();
                if (fetchedLatestVersion.isEmpty()) {
                    return;
                }

                String installedVersion = plugin.getDescription().getVersion();
                if (compareVersions(fetchedLatestVersion, installedVersion) <= 0) {
                    return;
                }

                currentVersion = installedVersion;
                latestVersion = fetchedLatestVersion;
                updateAvailable.set(true);

                compatibility.runGlobal(() -> {
                    plugin.getLogger().info("A new update is available. Current version: " + currentVersion + " | Latest version: " + latestVersion);
                    notifyOnlineStaffAboutUpdate();
                });
            } catch (Exception exception) {
                plugin.getLogger().fine("Update check failed: " + exception.getMessage());
            }
        });
    }

    public void sendUpdateNotification(Player player) {
        if (player == null || !player.isOnline() || !hasUpdateAvailable()) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("simpleportals.command.reload")) {
            return;
        }

        List<String> lines = configService.messageLines("update-check.available", List.of(
            "&8&m----------------------------------------",
            "&8[&bSimplePortals&8] &eA new update is available.",
            "&7Current version: &f%current_version%",
            "&7Latest version: &f%latest_version%",
            "&7Download: &b%link-bbb% &7- &6%link-spigot% &7- &a%link-modrinth%",
            "&8&m----------------------------------------"
        ),
            "current_version", currentVersion,
            "latest_version", latestVersion,
            "link-bbb", buildLinkText("BuiltByBit", BUILTBYBIT_URL),
            "link-spigot", buildLinkText("Spigot", SPIGOT_URL),
            "link-modrinth", buildLinkText("Modrinth", MODRINTH_URL)
        );
        configService.sendLines(player, lines);
    }

    public boolean hasUpdateAvailable() {
        return updateAvailable.get();
    }

    private void notifyOnlineStaffAboutUpdate() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            compatibility.runPlayer(player, () -> sendUpdateNotification(player));
        }
    }

    private String buildLinkText(String label, String url) {
        if (url == null || url.isBlank()) {
            return label;
        }
        return url;
    }

    private String fetchLatestVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SimplePortals-UpdateChecker");

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } finally {
            connection.disconnect();
        }
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("[^0-9]+");
        String[] rightParts = right.split("[^0-9]+");
        int length = Math.max(leftParts.length, rightParts.length);

        for (int index = 0; index < length; index++) {
            int leftValue = parseVersionPart(leftParts, index);
            int rightValue = parseVersionPart(rightParts, index);
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int parseVersionPart(String[] parts, int index) {
        if (index < 0 || index >= parts.length || parts[index] == null || parts[index].isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}