package com.aquiles.simpleportals.config;

import com.aquiles.simpleportals.data.PortalTrigger;
import com.aquiles.simpleportals.util.Text;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigService {

    private static final Color POS1_HOLOGRAM_COLOR = Color.fromARGB(0x66, 0xFF, 0x55, 0x55);
    private static final Color POS2_HOLOGRAM_COLOR = Color.fromARGB(0x66, 0x33, 0xFF, 0xFF);
    private static final Color[] EXTRA_HOLOGRAM_COLORS = {
        Color.fromARGB(0x66, 0xFF, 0xDD, 0x33),
        Color.fromARGB(0x66, 0x55, 0xFF, 0x55),
        Color.fromARGB(0x66, 0xFF, 0x55, 0xFF),
        Color.fromARGB(0x66, 0xFF, 0xAA, 0x33)
    };

    private static final boolean HOLOGRAMS_ENABLED = true;
    private static final String POS1_TEXT = "&cPOS 1";
    private static final String POS2_TEXT = "&bPOS 2";

    private static final double FACE_OFFSET_Y = 0.0D;
    private static final double FACE_INSET = -0.004D;
    private static final double FACE_TOP_OFFSET = 0.0D;
    private static final double FACE_BOTTOM_OFFSET = 0.0D;
    private static final double FACE_TOP_OFFSET_X = 0.0D;
    private static final double FACE_TOP_OFFSET_Z = 0.0D;
    private static final double FACE_BOTTOM_OFFSET_X = 0.0D;
    private static final double FACE_BOTTOM_OFFSET_Z = 0.0D;
    private static final int FACE_TEXT_WIDTH = 10;
    private static final int FACE_TEXT_LINES = 4;
    private static final FaceSettings GLOBAL_FACE_SETTINGS = new FaceSettings(0.0D, 0.0D, 0.0D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
    private static final FaceSettings NORTH_FACE_SETTINGS = new FaceSettings(0.01D, -0.5D, 0.0D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
    private static final FaceSettings SOUTH_FACE_SETTINGS = new FaceSettings(-0.01D, -0.5D, 0.0D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
    private static final FaceSettings EAST_FACE_SETTINGS = new FaceSettings(0.0D, -0.5D, 0.01D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
    private static final FaceSettings WEST_FACE_SETTINGS = new FaceSettings(0.0D, -0.5D, -0.01D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
    private static final FaceSettings TOP_FACE_SETTINGS = new FaceSettings(-0.01D, 0.0D, 0.5D, 1.0F, 1.0F, 220, 1.01F, 1.01F, 1.01F);
    private static final FaceSettings BOTTOM_FACE_SETTINGS = new FaceSettings(-0.01D, 0.0D, -0.5D, 1.0F, 1.0F, 220, 1.01F, 1.01F, 1.01F);
    private static final Map<String, FaceSettings> FACE_SETTINGS = Map.of(
        "north", NORTH_FACE_SETTINGS,
        "south", SOUTH_FACE_SETTINGS,
        "east", EAST_FACE_SETTINGS,
        "west", WEST_FACE_SETTINGS,
        "top", TOP_FACE_SETTINGS,
        "bottom", BOTTOM_FACE_SETTINGS
    );
    private static final boolean FACE_BRIGHTNESS_ENABLED = true;
    private static final int FACE_BRIGHTNESS_BLOCK = 15;
    private static final int FACE_BRIGHTNESS_SKY = 15;

    private static final double POS_OFFSET_X = 0.0D;
    private static final double POS_OFFSET_Y = 0.4D;
    private static final double POS_OFFSET_Z = 0.0D;
    private static final double POS_TOP_OFFSET = 0.0D;
    private static final double POS_BOTTOM_OFFSET = 0.0D;
    private static final double POS_TOP_OFFSET_X = 0.0D;
    private static final double POS_TOP_OFFSET_Z = 0.0D;
    private static final double POS_BOTTOM_OFFSET_X = 0.0D;
    private static final double POS_BOTTOM_OFFSET_Z = 0.0D;
    private static final float POS_DISPLAY_WIDTH = 0.75F;
    private static final float POS_DISPLAY_HEIGHT = 0.28F;
    private static final int POS_LINE_WIDTH = 160;
    private static final boolean POS_SHADOWED = true;
    private static final float POS_SCALE_X = 1.0F;
    private static final float POS_SCALE_Y = 1.0F;
    private static final float POS_SCALE_Z = 1.0F;
    private static final boolean POS_BRIGHTNESS_ENABLED = true;
    private static final int POS_BRIGHTNESS_BLOCK = 15;
    private static final int POS_BRIGHTNESS_SKY = 15;

    private static final double COORDS_OFFSET_X = 0.5D;
    private static final double COORDS_OFFSET_Y = 1.1D;
    private static final double COORDS_OFFSET_Z = 0.5D;
    private static final String COORDS_TEXT = "&f%x%, %y%, %z%";
    private static final float COORDS_DISPLAY_WIDTH = 0.9F;
    private static final float COORDS_DISPLAY_HEIGHT = 0.3F;
    private static final int COORDS_LINE_WIDTH = 180;
    private static final boolean COORDS_SHADOWED = true;
    private static final boolean COORDS_SEE_THROUGH = true;
    private static final byte COORDS_OPACITY = (byte) 190;
    private static final float COORDS_SCALE_X = 0.5F;
    private static final float COORDS_SCALE_Y = 0.5F;
    private static final float COORDS_SCALE_Z = 0.5F;
    private static final boolean COORDS_BRIGHTNESS_ENABLED = true;
    private static final int COORDS_BRIGHTNESS_BLOCK = 15;
    private static final int COORDS_BRIGHTNESS_SKY = 15;

    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        plugin.saveDefaultConfig();
        ensureResource("messages.yml");
        ensureResource("portals.yml");
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        boolean changed = pruneLegacyConfigSections(config);
        changed |= ensureConfigDefaults(config);
        if (changed) {
            plugin.saveConfig();
            plugin.reloadConfig();
        }
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        if (plugin.getResource("messages.yml") != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }
    }

    private void ensureResource(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
    }

    private boolean pruneLegacyConfigSections(FileConfiguration config) {
        boolean changed = false;
        changed |= clearPath(config, "selector.holograms");
        changed |= clearPath(config, "proxy");
        changed |= clearPath(config, "metrics");
        return changed;
    }

    private boolean ensureConfigDefaults(FileConfiguration config) {
        boolean changed = false;
        changed |= setDefaultIfMissing(config, "portals.replace-air", true);

        return changed;
    }

    private boolean clearPath(FileConfiguration config, String path) {
        if (!config.contains(path)) {
            return false;
        }
        config.set(path, null);
        return true;
    }

    private boolean setDefaultIfMissing(FileConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    public String message(String path, String... replacements) {
        return render(messageValue(path, path), replacements);
    }

    public List<String> messageLines(String path, String... replacements) {
        return messageLines(path, List.of(), replacements);
    }

    public List<String> messageLines(String path, List<String> fallback, String... replacements) {
        List<String> lines = messages.getStringList(path);
        if (lines.isEmpty() && messages.getDefaults() != null) {
            lines = messages.getDefaults().getStringList(path);
        }
        if (lines.isEmpty()) {
            lines = fallback;
        }
        return lines.stream().map(line -> render(line, replacements)).toList();
    }

    public void send(CommandSender sender, String path, String... replacements) {
        sendText(sender, prefix() + message(path, replacements));
    }

    public void sendRaw(CommandSender sender, String path, String... replacements) {
        sendText(sender, message(path, replacements));
    }

    public void sendText(CommandSender sender, String text) {
        for (var line : Text.components(text)) {
            sender.sendMessage(line);
        }
    }

    public void sendLines(CommandSender sender, List<String> lines) {
        for (String line : lines) {
            sendText(sender, line);
        }
    }

    private String messageValue(String path, String fallback) {
        String value = messages.getString(path);
        if (value == null && messages.getDefaults() != null) {
            value = messages.getDefaults().getString(path);
        }
        return value == null ? fallback : value;
    }

    private String render(String input, String... replacements) {
        Map<String, String> placeholders = new HashMap<>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            placeholders.put(replacements[index], replacements[index + 1]);
        }
        return Text.replace(input, placeholders);
    }

    public String prefix() {
        return messageValue("prefix", "");
    }

    public ItemStack createWandItem(NamespacedKey key) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("selector.item.material", "GOLDEN_AXE"));
        if (material == null) {
            material = Material.GOLDEN_AXE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.colorize(plugin.getConfig().getString("selector.item.name", "&6Portal Selector")));
        meta.setLore(plugin.getConfig().getStringList("selector.item.lore").stream().map(Text::colorize).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public long previewIntervalTicks() {
        return Math.max(1L, plugin.getConfig().getLong("selector.preview.interval-ticks", 10L));
    }

    public int previewMaxEdgePoints() {
        return Math.max(12, plugin.getConfig().getInt("selector.preview.max-edge-points", 72));
    }

    public boolean hologramsEnabled() {
        return HOLOGRAMS_ENABLED;
    }

    public String hologramText(boolean first) {
        return first ? POS1_TEXT : POS2_TEXT;
    }

    public String hologramText(int positionNumber) {
        if (positionNumber == 1) {
            return POS1_TEXT;
        }
        if (positionNumber == 2) {
            return POS2_TEXT;
        }
        return "&ePOS " + positionNumber;
    }

    public Color hologramBackground(boolean first) {
        return first ? POS1_HOLOGRAM_COLOR : POS2_HOLOGRAM_COLOR;
    }

    public Color hologramBackground(int positionNumber) {
        if (positionNumber == 1) {
            return POS1_HOLOGRAM_COLOR;
        }
        if (positionNumber == 2) {
            return POS2_HOLOGRAM_COLOR;
        }
        return EXTRA_HOLOGRAM_COLORS[Math.floorMod(positionNumber - 3, EXTRA_HOLOGRAM_COLORS.length)];
    }

    public double hologramFaceOffset() {
        return FACE_OFFSET_Y;
    }

    public double hologramFaceInset() {
        return FACE_INSET;
    }

    public double hologramFaceTopOffset() {
        return FACE_TOP_OFFSET;
    }

    public double hologramFaceBottomOffset() {
        return FACE_BOTTOM_OFFSET;
    }

    public double hologramFaceTopOffsetX() {
        return FACE_TOP_OFFSET_X;
    }

    public double hologramFaceTopOffsetZ() {
        return FACE_TOP_OFFSET_Z;
    }

    public double hologramFaceBottomOffsetX() {
        return FACE_BOTTOM_OFFSET_X;
    }

    public double hologramFaceBottomOffsetZ() {
        return FACE_BOTTOM_OFFSET_Z;
    }

    public int hologramFaceTextWidth() {
        return FACE_TEXT_WIDTH;
    }

    public int hologramFaceTextLines() {
        return FACE_TEXT_LINES;
    }

    public int hologramFaceLineWidth() {
        return GLOBAL_FACE_SETTINGS.lineWidth();
    }

    public float hologramFaceDisplayWidth() {
        return GLOBAL_FACE_SETTINGS.displayWidth();
    }

    public float hologramFaceDisplayWidth(String face) {
        return faceSettings(face).displayWidth();
    }

    public float hologramFaceDisplayHeight() {
        return GLOBAL_FACE_SETTINGS.displayHeight();
    }

    public float hologramFaceDisplayHeight(String face) {
        return faceSettings(face).displayHeight();
    }

    public int hologramFaceLineWidth(String face) {
        return faceSettings(face).lineWidth();
    }

    public double hologramFaceOffsetX(String face) {
        return faceSettings(face).offsetX();
    }

    public double hologramFaceOffsetY(String face) {
        return faceSettings(face).offsetY();
    }

    public double hologramFaceOffsetZ(String face) {
        return faceSettings(face).offsetZ();
    }

    public float hologramFaceScaleX() {
        return GLOBAL_FACE_SETTINGS.scaleX();
    }

    public float hologramFaceScaleX(String face) {
        return faceSettings(face).scaleX();
    }

    public float hologramFaceScaleY() {
        return GLOBAL_FACE_SETTINGS.scaleY();
    }

    public float hologramFaceScaleY(String face) {
        return faceSettings(face).scaleY();
    }

    public float hologramFaceScaleZ() {
        return GLOBAL_FACE_SETTINGS.scaleZ();
    }

    public float hologramFaceScaleZ(String face) {
        return faceSettings(face).scaleZ();
    }

    public boolean hologramFaceBrightnessEnabled() {
        return FACE_BRIGHTNESS_ENABLED;
    }

    public int hologramFaceBrightnessBlock() {
        return FACE_BRIGHTNESS_BLOCK;
    }

    public int hologramFaceBrightnessSky() {
        return FACE_BRIGHTNESS_SKY;
    }

    public double hologramPosOffsetX() {
        return POS_OFFSET_X;
    }

    public double hologramPosOffsetY() {
        return POS_OFFSET_Y;
    }

    public double hologramPosOffsetZ() {
        return POS_OFFSET_Z;
    }

    public int hologramPosLineWidth() {
        return POS_LINE_WIDTH;
    }

    public float hologramPosDisplayWidth() {
        return POS_DISPLAY_WIDTH;
    }

    public float hologramPosDisplayHeight() {
        return POS_DISPLAY_HEIGHT;
    }

    public boolean hologramPosShadowed() {
        return POS_SHADOWED;
    }

    public double hologramPosTopOffset() {
        return POS_TOP_OFFSET;
    }

    public double hologramPosBottomOffset() {
        return POS_BOTTOM_OFFSET;
    }

    public double hologramPosTopOffsetX() {
        return POS_TOP_OFFSET_X;
    }

    public double hologramPosTopOffsetZ() {
        return POS_TOP_OFFSET_Z;
    }

    public double hologramPosBottomOffsetX() {
        return POS_BOTTOM_OFFSET_X;
    }

    public double hologramPosBottomOffsetZ() {
        return POS_BOTTOM_OFFSET_Z;
    }

    public float hologramPosScaleX() {
        return POS_SCALE_X;
    }

    public float hologramPosScaleY() {
        return POS_SCALE_Y;
    }

    public float hologramPosScaleZ() {
        return POS_SCALE_Z;
    }

    public boolean hologramPosBrightnessEnabled() {
        return POS_BRIGHTNESS_ENABLED;
    }

    public int hologramPosBrightnessBlock() {
        return POS_BRIGHTNESS_BLOCK;
    }

    public int hologramPosBrightnessSky() {
        return POS_BRIGHTNESS_SKY;
    }

    public double hologramCoordsOffsetX() {
        return COORDS_OFFSET_X;
    }

    public double hologramCoordsOffsetY() {
        return COORDS_OFFSET_Y;
    }

    public double hologramCoordsOffsetZ() {
        return COORDS_OFFSET_Z;
    }

    public String hologramCoordsText() {
        return COORDS_TEXT;
    }

    public int hologramCoordsLineWidth() {
        return COORDS_LINE_WIDTH;
    }

    public float hologramCoordsDisplayWidth() {
        return COORDS_DISPLAY_WIDTH;
    }

    public float hologramCoordsDisplayHeight() {
        return COORDS_DISPLAY_HEIGHT;
    }

    public boolean hologramCoordsShadowed() {
        return COORDS_SHADOWED;
    }

    public boolean hologramCoordsSeeThrough() {
        return COORDS_SEE_THROUGH;
    }

    public byte hologramCoordsOpacity() {
        return COORDS_OPACITY;
    }

    public float hologramCoordsScaleX() {
        return COORDS_SCALE_X;
    }

    public float hologramCoordsScaleY() {
        return COORDS_SCALE_Y;
    }

    public float hologramCoordsScaleZ() {
        return COORDS_SCALE_Z;
    }

    public boolean hologramCoordsBrightnessEnabled() {
        return COORDS_BRIGHTNESS_ENABLED;
    }

    public int hologramCoordsBrightnessBlock() {
        return COORDS_BRIGHTNESS_BLOCK;
    }

    public int hologramCoordsBrightnessSky() {
        return COORDS_BRIGHTNESS_SKY;
    }

    public int maxShowRadius() {
        return Math.max(1, plugin.getConfig().getInt("commands.show.max-radius", 100));
    }

    public long reentryProtectionMillis() {
        long ticks = Math.max(1L, plugin.getConfig().getLong("portals.reentry-protection-ticks", 20L));
        return ticks * 50L;
    }

    public String defaultTriggerName() {
        return plugin.getConfig().getString("portals.default-trigger", "WATER");
    }

    public int defaultCooldownSeconds() {
        return Math.max(0, plugin.getConfig().getInt("portals.default-cooldown-seconds", 0));
    }

    public boolean replaceAirEnabled() {
        return plugin.getConfig().getBoolean("portals.replace-air", true);
    }

    public boolean updateCheckEnabled() {
        return plugin.getConfig().getBoolean("update-check.enabled", true);
    }

    public String triggerList() {
        return String.join(", ", PortalTrigger.names());
    }

    private FaceSettings faceSettings(String face) {
        return FACE_SETTINGS.getOrDefault(face, GLOBAL_FACE_SETTINGS);
    }

    private record FaceSettings(
        double offsetX,
        double offsetY,
        double offsetZ,
        float displayWidth,
        float displayHeight,
        int lineWidth,
        float scaleX,
        float scaleY,
        float scaleZ
    ) {
    }
}
