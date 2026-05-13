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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class UpdateChecker {

    private static final String MODRINTH_PROJECT_ID = "QUxBPZJz";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version?include_changelog=false";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/simpleportals-";
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\\"version_number\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

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
        if (!configService.updateCheckEnabled() || MODRINTH_PROJECT_ID.isBlank()) {
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
                    plugin.getLogger().info("Update available! (" + currentVersion + " -> " + latestVersion + ") " + MODRINTH_URL);
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

        List<String> lines = configService.messageLines(
            "update-check.available",
            List.of("&8[&bSimplePortals&8] &aUpdate available! &7(%current_version% &8-> &f%latest_version%&7) <click:open_url:'%download_url%'><aqua>[Download here]</aqua></click>"),
            "current_version", currentVersion,
            "latest_version", latestVersion,
            "download_url", MODRINTH_URL
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

    private String fetchLatestVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODRINTH_API_URL).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SimplePortals/" + plugin.getDescription().getVersion() + " (https://modrinth.com/plugin/simpleportals-)");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return parseLatestVersion(response.toString());
        } finally {
            connection.disconnect();
        }
    }

    private String parseLatestVersion(String response) {
        String latest = "";
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(response);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (!candidate.isEmpty() && (latest.isEmpty() || compareVersions(candidate, latest) > 0)) {
                latest = candidate;
            }
        }
        return latest;
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