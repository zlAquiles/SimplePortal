package com.aquiles.simpleportals.service;

import com.aquiles.simpleportals.SimplePortalsPlugin;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.Cuboid;
import com.aquiles.simpleportals.data.SelectionSession;
import com.aquiles.simpleportals.util.ServerCompatibility;
import com.aquiles.simpleportals.util.Text;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SelectionService {

    private static final Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);
    private static final Particle DUST_PARTICLE = resolveDustParticle();
    private static final long SELECTION_TIMEOUT_MILLIS = 60_000L;

    private final SimplePortalsPlugin plugin;
    private final ConfigService configService;
    private final PortalStore portalStore;
    private final ServerCompatibility compatibility;
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, SelectionMarkers> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, Object> previewTasks = new ConcurrentHashMap<>();
    private Object previewTask;

    public SelectionService(SimplePortalsPlugin plugin, ConfigService configService, PortalStore portalStore) {
        this.plugin = plugin;
        this.configService = configService;
        this.portalStore = portalStore;
        this.compatibility = plugin.getCompatibility();
    }

    public SelectionSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> new SelectionSession());
    }

    public Optional<SelectionSession> findSession(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public void giveWand(Player player) {
        SelectionSession session = getSession(player);
        session.setSelectorEnabled(true);
        ensurePreviewTask(player);
        ItemStack wand = configService.createWandItem(plugin.getWandKey());
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(wand);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public void setPosition(Player player, boolean first, Location location) {
        Location snapped = new Location(
            location.getWorld(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
        SelectionSession session = getSession(player);
        if (first) {
            session.setPos1(snapped);
        } else {
            session.setPos2(snapped);
        }
        session.touchSelection();
        ensurePreviewTask(player);
        updateDisplays(player, first, snapped);
    }

    public boolean hasCompleteSelection(Player player) {
        SelectionSession session = getSession(player);
        return session.getPos1() != null && session.getPos2() != null;
    }

    public boolean hasWorldMismatch(Player player) {
        SelectionSession session = getSession(player);
        return hasCompleteSelection(player) && !session.getPos1().getWorld().getName().equalsIgnoreCase(session.getPos2().getWorld().getName());
    }

    public Optional<Cuboid> getSelectionCuboid(Player player) {
        SelectionSession session = getSession(player);
        if (session.getPos1() == null || session.getPos2() == null || hasWorldMismatch(player)) {
            return Optional.empty();
        }
        return Optional.of(Cuboid.fromLocations(session.getPos1(), session.getPos2()));
    }

    public void completeSelection(Player player) {
        SelectionSession session = getSession(player);
        session.clearSelection();
        session.setSelectorEnabled(false);
        cancelPreviewTask(player.getUniqueId());
        removeMarkers(player.getUniqueId());
    }

    public void hideMarkersFrom(Player viewer) {
        for (SelectionMarkers markers : holograms.values()) {
            hideGroup(viewer, markers.pos1);
            hideGroup(viewer, markers.pos2);
        }
    }

    public void clear(Player player) {
        sessions.remove(player.getUniqueId());
        cancelPreviewTask(player.getUniqueId());
        removeMarkers(player.getUniqueId());
    }

    public void restartPreviewTask() {
        stopPreviewTask();
        if (!configService.hologramsEnabled()) {
            holograms.keySet().stream().toList().forEach(this::removeMarkers);
            return;
        }
        long intervalTicks = configService.previewIntervalTicks();
        if (!compatibility.isFolia()) {
            previewTask = compatibility.runGlobalTimer(intervalTicks, intervalTicks, this::renderSelections);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            SelectionSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }
            if (!compatibility.isFolia()) {
                if (session.getPos1() != null) {
                    updateDisplays(player, true, session.getPos1());
                }
                if (session.getPos2() != null) {
                    updateDisplays(player, false, session.getPos2());
                }
                continue;
            }
            if (session.isSelectorEnabled()) {
                ensurePreviewTask(player);
            }
        }
    }

    public void stopPreviewTask() {
        if (previewTask != null) {
            compatibility.cancelTask(previewTask);
            previewTask = null;
        }
        previewTasks.values().forEach(compatibility::cancelTask);
        previewTasks.clear();
        holograms.keySet().stream().toList().forEach(this::removeMarkers);
    }

    private void ensurePreviewTask(Player player) {
        if (!compatibility.isFolia() || player == null || previewTasks.containsKey(player.getUniqueId())) {
            return;
        }
        Object task = compatibility.runPlayerTimer(player, configService.previewIntervalTicks(), configService.previewIntervalTicks(), () -> renderSelection(player));
        if (task != null) {
            previewTasks.put(player.getUniqueId(), task);
        }
    }

    private void cancelPreviewTask(UUID ownerId) {
        Object task = previewTasks.remove(ownerId);
        if (task != null) {
            compatibility.cancelTask(task);
        }
    }

    private void renderSelections() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            renderSelection(player);
        }
    }

    private void renderSelection(Player player) {
        SelectionSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isSelectorEnabled()) {
            if (compatibility.isFolia()) {
                cancelPreviewTask(player.getUniqueId());
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (isSelectionExpired(session, now)) {
            expireSelection(player, session);
            return;
        }
        if (session.getPos1() != null) {
            renderMarker(player, session.getPos1(), Color.RED);
        }
        if (session.getPos2() != null) {
            renderMarker(player, session.getPos2(), Color.AQUA);
        }
        if (session.getPos1() != null && session.getPos2() != null
            && session.getPos1().getWorld().getName().equalsIgnoreCase(session.getPos2().getWorld().getName())) {
            renderBox(player, Cuboid.fromLocations(session.getPos1(), session.getPos2()));
        }
    }

    private boolean isSelectionExpired(SelectionSession session, long now) {
        return session.hasSelection() && session.getLastSelectionAt() > 0L && now - session.getLastSelectionAt() >= SELECTION_TIMEOUT_MILLIS;
    }

    private void expireSelection(Player player, SelectionSession session) {
        session.clearSelection();
        removeMarkers(player.getUniqueId());
        configService.send(player, "status.selection_expired");
    }

    private void updateDisplays(Player owner, boolean first, Location blockLocation) {
        if (!configService.hologramsEnabled()) {
            return;
        }
        SelectionMarkers markers = holograms.computeIfAbsent(owner.getUniqueId(), ignored -> new SelectionMarkers());
        MarkerGroup group = first ? markers.pos1 : markers.pos2;
        String panelText = buildPanelText();
        String posText = Text.colorize(configService.hologramText(first));
        String coordText = buildCoordinateText(blockLocation);
        Color background = configService.hologramBackground(first);

        for (MarkerFace face : MarkerFace.values()) {
            int index = face.ordinal();
            String faceKey = face.configKey();
            Location faceLocation = face.location(
                blockLocation,
                configService.hologramFaceOffset(),
                configService.hologramFaceInset(),
                configService.hologramFaceTopOffset(),
                configService.hologramFaceBottomOffset(),
                configService.hologramFaceTopOffsetX(),
                configService.hologramFaceTopOffsetZ(),
                configService.hologramFaceBottomOffsetX(),
                configService.hologramFaceBottomOffsetZ()
            );
            faceLocation.add(
                configService.hologramFaceOffsetX(faceKey),
                configService.hologramFaceOffsetY(faceKey),
                configService.hologramFaceOffsetZ(faceKey)
            );
            group.panels[index] = updatePanelDisplay(owner, group.panels[index], faceLocation, panelText, background, face);
            if (!shouldShowPosLabel(face)) {
                removeDisplay(group.labels[index]);
                group.labels[index] = null;
                continue;
            }
            Location posLocation = faceLocation.clone().add(
                configService.hologramPosOffsetX() + extraPosOffsetX(face),
                configService.hologramPosOffsetY() + extraPosOffset(face),
                configService.hologramPosOffsetZ() + extraPosOffsetZ(face)
            );
            group.labels[index] = updatePosDisplay(owner, group.labels[index], posLocation, posText, face.yaw(), face.pitch());
        }

        Location coordsLocation = blockLocation.clone().add(
            configService.hologramCoordsOffsetX(),
            configService.hologramCoordsOffsetY(),
            configService.hologramCoordsOffsetZ()
        );
        group.coords = updateCoordinateDisplay(owner, group.coords, coordsLocation, coordText);
    }

    private boolean shouldShowPosLabel(MarkerFace face) {
        return face != MarkerFace.TOP;
    }

    private String buildPanelText() {
        int width = configService.hologramFaceTextWidth();
        int lines = configService.hologramFaceTextLines();
        String blankLine = " ".repeat(width);
        StringBuilder builder = new StringBuilder((blankLine.length() * lines) + Math.max(0, lines - 1));
        for (int index = 0; index < lines; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(blankLine);
        }
        return builder.toString();
    }

    private double extraPosOffset(MarkerFace face) {
        return switch (face) {
            case TOP -> configService.hologramPosTopOffset();
            case BOTTOM -> configService.hologramPosBottomOffset();
            default -> 0.0D;
        };
    }

    private double extraPosOffsetX(MarkerFace face) {
        return switch (face) {
            case TOP -> configService.hologramPosTopOffsetX();
            case BOTTOM -> configService.hologramPosBottomOffsetX();
            default -> 0.0D;
        };
    }

    private double extraPosOffsetZ(MarkerFace face) {
        return switch (face) {
            case TOP -> configService.hologramPosTopOffsetZ();
            case BOTTOM -> configService.hologramPosBottomOffsetZ();
            default -> 0.0D;
        };
    }
    private String buildCoordinateText(Location blockLocation) {
        return Text.colorize(Text.replace(configService.hologramCoordsText(), Map.of(
            "x", Integer.toString(blockLocation.getBlockX()),
            "y", Integer.toString(blockLocation.getBlockY()),
            "z", Integer.toString(blockLocation.getBlockZ())
        )));
    }

    private TextDisplay updatePanelDisplay(Player owner, TextDisplay current, Location location, String text, Color background, MarkerFace face) {
        String faceKey = face.configKey();
        if (current == null || current.isDead() || current.getWorld() != location.getWorld() || compatibility.isFolia()) {
            removeDisplay(current);
            return spawnPanelDisplay(owner, location, text, background, face);
        }
        compatibility.teleportEntity(current, location);
        current.setRotation(face.yaw(), face.pitch());
        current.setText(text);
        current.setBackgroundColor(background);
        current.setLineWidth(configService.hologramFaceLineWidth(faceKey));
        current.setDisplayWidth(configService.hologramFaceDisplayWidth(faceKey));
        current.setDisplayHeight(configService.hologramFaceDisplayHeight(faceKey));
        applyScale(current, configService.hologramFaceScaleX(faceKey), configService.hologramFaceScaleY(faceKey), configService.hologramFaceScaleZ(faceKey));
        applyBrightness(current, configService.hologramFaceBrightnessEnabled(), configService.hologramFaceBrightnessBlock(), configService.hologramFaceBrightnessSky());
        return current;
    }

    private TextDisplay updatePosDisplay(Player owner, TextDisplay current, Location location, String text, float yaw, float pitch) {
        if (current == null || current.isDead() || current.getWorld() != location.getWorld() || compatibility.isFolia()) {
            removeDisplay(current);
            return spawnPosDisplay(owner, location, text, yaw, pitch);
        }
        compatibility.teleportEntity(current, location);
        current.setRotation(yaw, pitch);
        current.setText(text);
        current.setBackgroundColor(TRANSPARENT);
        current.setLineWidth(configService.hologramPosLineWidth());
        current.setDisplayWidth(configService.hologramPosDisplayWidth());
        current.setDisplayHeight(configService.hologramPosDisplayHeight());
        current.setShadowed(configService.hologramPosShadowed());
        applyScale(current, configService.hologramPosScaleX(), configService.hologramPosScaleY(), configService.hologramPosScaleZ());
        applyBrightness(current, configService.hologramPosBrightnessEnabled(), configService.hologramPosBrightnessBlock(), configService.hologramPosBrightnessSky());
        return current;
    }

    private TextDisplay updateCoordinateDisplay(Player owner, TextDisplay current, Location location, String text) {
        if (current == null || current.isDead() || current.getWorld() != location.getWorld() || compatibility.isFolia()) {
            removeDisplay(current);
            return spawnCoordinateDisplay(owner, location, text);
        }
        compatibility.teleportEntity(current, location);
        current.setText(text);
        current.setBackgroundColor(TRANSPARENT);
        current.setLineWidth(configService.hologramCoordsLineWidth());
        current.setDisplayWidth(configService.hologramCoordsDisplayWidth());
        current.setDisplayHeight(configService.hologramCoordsDisplayHeight());
        current.setShadowed(configService.hologramCoordsShadowed());
        current.setSeeThrough(configService.hologramCoordsSeeThrough());
        current.setTextOpacity(configService.hologramCoordsOpacity());
        applyScale(current, configService.hologramCoordsScaleX(), configService.hologramCoordsScaleY(), configService.hologramCoordsScaleZ());
        applyBrightness(current, configService.hologramCoordsBrightnessEnabled(), configService.hologramCoordsBrightnessBlock(), configService.hologramCoordsBrightnessSky());
        return current;
    }

    private TextDisplay spawnPanelDisplay(Player owner, Location location, String text, Color background, MarkerFace face) {
        String faceKey = face.configKey();
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setBillboard(Display.Billboard.FIXED);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setGravity(false);
        display.setSeeThrough(false);
        display.setShadowed(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(background);
        display.setText(text);
        display.setLineWidth(configService.hologramFaceLineWidth(faceKey));
        display.setDisplayWidth(configService.hologramFaceDisplayWidth(faceKey));
        display.setDisplayHeight(configService.hologramFaceDisplayHeight(faceKey));
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setRotation(face.yaw(), face.pitch());
        applyScale(display, configService.hologramFaceScaleX(faceKey), configService.hologramFaceScaleY(faceKey), configService.hologramFaceScaleZ(faceKey));
        applyBrightness(display, configService.hologramFaceBrightnessEnabled(), configService.hologramFaceBrightnessBlock(), configService.hologramFaceBrightnessSky());
        refreshVisibility(owner, display);
        return display;
    }

    private TextDisplay spawnPosDisplay(Player owner, Location location, String text, float yaw, float pitch) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setBillboard(Display.Billboard.FIXED);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setGravity(false);
        display.setSeeThrough(false);
        display.setShadowed(configService.hologramPosShadowed());
        display.setDefaultBackground(false);
        display.setBackgroundColor(TRANSPARENT);
        display.setText(text);
        display.setLineWidth(configService.hologramPosLineWidth());
        display.setDisplayWidth(configService.hologramPosDisplayWidth());
        display.setDisplayHeight(configService.hologramPosDisplayHeight());
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setRotation(yaw, pitch);
        applyScale(display, configService.hologramPosScaleX(), configService.hologramPosScaleY(), configService.hologramPosScaleZ());
        applyBrightness(display, configService.hologramPosBrightnessEnabled(), configService.hologramPosBrightnessBlock(), configService.hologramPosBrightnessSky());
        refreshVisibility(owner, display);
        return display;
    }

    private TextDisplay spawnCoordinateDisplay(Player owner, Location location, String text) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setGravity(false);
        display.setSeeThrough(configService.hologramCoordsSeeThrough());
        display.setShadowed(configService.hologramCoordsShadowed());
        display.setDefaultBackground(false);
        display.setBackgroundColor(TRANSPARENT);
        display.setText(text);
        display.setLineWidth(configService.hologramCoordsLineWidth());
        display.setDisplayWidth(configService.hologramCoordsDisplayWidth());
        display.setDisplayHeight(configService.hologramCoordsDisplayHeight());
        display.setTextOpacity(configService.hologramCoordsOpacity());
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        applyScale(display, configService.hologramCoordsScaleX(), configService.hologramCoordsScaleY(), configService.hologramCoordsScaleZ());
        applyBrightness(display, configService.hologramCoordsBrightnessEnabled(), configService.hologramCoordsBrightnessBlock(), configService.hologramCoordsBrightnessSky());
        refreshVisibility(owner, display);
        return display;
    }

    private void applyScale(TextDisplay display, float scaleX, float scaleY, float scaleZ) {
        display.setTransformation(new Transformation(
            new Vector3f(0.0F, 0.0F, 0.0F),
            new Quaternionf(),
            new Vector3f(scaleX, scaleY, scaleZ),
            new Quaternionf()
        ));
    }

    private void applyBrightness(TextDisplay display, boolean enabled, int blockLight, int skyLight) {
        if (!enabled) {
            display.setBrightness(null);
            return;
        }
        display.setBrightness(new Display.Brightness(blockLight, skyLight));
    }

    private void refreshVisibility(Player owner, TextDisplay display) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!compatibility.isFolia()) {
                if (online.getUniqueId().equals(owner.getUniqueId())) {
                    online.showEntity(plugin, display);
                } else {
                    online.hideEntity(plugin, display);
                }
                continue;
            }
            compatibility.runPlayer(online, () -> {
                if (online.getUniqueId().equals(owner.getUniqueId())) {
                    online.showEntity(plugin, display);
                } else {
                    online.hideEntity(plugin, display);
                }
            });
        }
    }

    private void hideGroup(Player viewer, MarkerGroup group) {
        for (TextDisplay panel : group.panels) {
            hideFrom(viewer, panel);
        }
        for (TextDisplay label : group.labels) {
            hideFrom(viewer, label);
        }
        hideFrom(viewer, group.coords);
    }

    private void hideFrom(Player viewer, TextDisplay display) {
        if (display == null || display.isDead()) {
            return;
        }
        if (!compatibility.isFolia()) {
            viewer.hideEntity(plugin, display);
            return;
        }
        compatibility.runPlayer(viewer, () -> viewer.hideEntity(plugin, display));
    }

    private void removeMarkers(UUID ownerId) {
        SelectionMarkers markers = holograms.remove(ownerId);
        if (markers == null) {
            return;
        }
        removeGroup(markers.pos1);
        removeGroup(markers.pos2);
    }

    private void removeGroup(MarkerGroup group) {
        for (TextDisplay panel : group.panels) {
            removeDisplay(panel);
        }
        for (TextDisplay label : group.labels) {
            removeDisplay(label);
        }
        removeDisplay(group.coords);
    }

    private void removeDisplay(TextDisplay display) {
        if (display != null && !display.isDead()) {
            compatibility.removeEntity(display);
        }
    }

    private void renderMarker(Player player, Location location, Color color) {
        Particle.DustOptions options = new Particle.DustOptions(color, 1.2F);
        double centerX = location.getBlockX() + 0.5D;
        double centerY = location.getBlockY() + 1.1D;
        double centerZ = location.getBlockZ() + 0.5D;
        player.spawnParticle(DUST_PARTICLE, centerX, centerY, centerZ, 6, 0.18D, 0.18D, 0.18D, 0.0D, options);
    }

    private void renderBox(Player player, Cuboid cuboid) {
        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(cuboid.worldName())) {
            return;
        }
        Particle.DustOptions options = new Particle.DustOptions(Color.fromRGB(255, 170, 0), 0.9F);
        int samplesPerEdge = Math.max(2, configService.previewMaxEdgePoints() / 12);
        double minX = cuboid.minX() + 0.5D;
        double minY = cuboid.minY() + 0.5D;
        double minZ = cuboid.minZ() + 0.5D;
        double maxX = cuboid.maxX() + 0.5D;
        double maxY = cuboid.maxY() + 0.5D;
        double maxZ = cuboid.maxZ() + 0.5D;

        spawnEdge(player, minX, minY, minZ, maxX, minY, minZ, samplesPerEdge, options);
        spawnEdge(player, minX, minY, maxZ, maxX, minY, maxZ, samplesPerEdge, options);
        spawnEdge(player, minX, maxY, minZ, maxX, maxY, minZ, samplesPerEdge, options);
        spawnEdge(player, minX, maxY, maxZ, maxX, maxY, maxZ, samplesPerEdge, options);

        spawnEdge(player, minX, minY, minZ, minX, maxY, minZ, samplesPerEdge, options);
        spawnEdge(player, maxX, minY, minZ, maxX, maxY, minZ, samplesPerEdge, options);
        spawnEdge(player, minX, minY, maxZ, minX, maxY, maxZ, samplesPerEdge, options);
        spawnEdge(player, maxX, minY, maxZ, maxX, maxY, maxZ, samplesPerEdge, options);

        spawnEdge(player, minX, minY, minZ, minX, minY, maxZ, samplesPerEdge, options);
        spawnEdge(player, maxX, minY, minZ, maxX, minY, maxZ, samplesPerEdge, options);
        spawnEdge(player, minX, maxY, minZ, minX, maxY, maxZ, samplesPerEdge, options);
        spawnEdge(player, maxX, maxY, minZ, maxX, maxY, maxZ, samplesPerEdge, options);
    }

    private void spawnEdge(
        Player player,
        double x1,
        double y1,
        double z1,
        double x2,
        double y2,
        double z2,
        int samples,
        Particle.DustOptions options
    ) {
        for (int index = 0; index <= samples; index++) {
            double progress = index / (double) samples;
            double x = x1 + ((x2 - x1) * progress);
            double y = y1 + ((y2 - y1) * progress);
            double z = z1 + ((z2 - z1) * progress);
            player.spawnParticle(DUST_PARTICLE, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, options);
        }
    }

    private static Particle resolveDustParticle() {
        try {
            return Particle.valueOf("DUST");
        } catch (IllegalArgumentException ignored) {
            return Particle.valueOf("REDSTONE");
        }
    }

    private static final class SelectionMarkers {
        private final MarkerGroup pos1 = new MarkerGroup();
        private final MarkerGroup pos2 = new MarkerGroup();
    }

    private static final class MarkerGroup {
        private final TextDisplay[] panels = new TextDisplay[MarkerFace.values().length];
        private final TextDisplay[] labels = new TextDisplay[MarkerFace.values().length];
        private TextDisplay coords;
    }

    private enum MarkerFace {
        NORTH(180.0F, 0.0F) {
            @Override
            Location location(Location blockLocation, double sideOffsetY, double inset, double topOffset, double bottomOffset, double topOffsetX, double topOffsetZ, double bottomOffsetX, double bottomOffsetZ) {
                return blockLocation.clone().add(0.5D, 0.5D + sideOffsetY, inset);
            }
        },
        SOUTH(0.0F, 0.0F) {
            @Override
            Location location(Location blockLocation, double sideOffsetY, double inset, double topOffset, double bottomOffset, double topOffsetX, double topOffsetZ, double bottomOffsetX, double bottomOffsetZ) {
                return blockLocation.clone().add(0.5D, 0.5D + sideOffsetY, 1.0D - inset);
            }
        },
        WEST(90.0F, 0.0F) {
            @Override
            Location location(Location blockLocation, double sideOffsetY, double inset, double topOffset, double bottomOffset, double topOffsetX, double topOffsetZ, double bottomOffsetX, double bottomOffsetZ) {
                return blockLocation.clone().add(inset, 0.5D + sideOffsetY, 0.5D);
            }
        },
        EAST(-90.0F, 0.0F) {
            @Override
            Location location(Location blockLocation, double sideOffsetY, double inset, double topOffset, double bottomOffset, double topOffsetX, double topOffsetZ, double bottomOffsetX, double bottomOffsetZ) {
                return blockLocation.clone().add(1.0D - inset, 0.5D + sideOffsetY, 0.5D);
            }
        },
        TOP(0.0F, -90.0F) {
            @Override
            Location location(Location blockLocation, double sideOffsetY, double inset, double topOffset, double bottomOffset, double topOffsetX, double topOffsetZ, double bottomOffsetX, double bottomOffsetZ) {
                return blockLocation.clone().add(0.5D + topOffsetX, (1.0D - inset) + topOffset, 0.5D + topOffsetZ);
            }
        },
        BOTTOM(0.0F, 90.0F) {
            @Override
            Location location(Location blockLocation, double sideOffsetY, double inset, double topOffset, double bottomOffset, double topOffsetX, double topOffsetZ, double bottomOffsetX, double bottomOffsetZ) {
                return blockLocation.clone().add(0.5D + bottomOffsetX, inset + bottomOffset, 0.5D + bottomOffsetZ);
            }
        };

        private final float yaw;
        private final float pitch;

        MarkerFace(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        abstract Location location(
            Location blockLocation,
            double sideOffsetY,
            double inset,
            double topOffset,
            double bottomOffset,
            double topOffsetX,
            double topOffsetZ,
            double bottomOffsetX,
            double bottomOffsetZ
        );

        String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }

        float yaw() {
            return yaw;
        }

        float pitch() {
            return pitch;
        }
    }
}
