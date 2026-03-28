package com.aquiles.simpleportals.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
        .character(ChatColor.COLOR_CHAR)
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
        Map.entry('0', "<black>"),
        Map.entry('1', "<dark_blue>"),
        Map.entry('2', "<dark_green>"),
        Map.entry('3', "<dark_aqua>"),
        Map.entry('4', "<dark_red>"),
        Map.entry('5', "<dark_purple>"),
        Map.entry('6', "<gold>"),
        Map.entry('7', "<gray>"),
        Map.entry('8', "<dark_gray>"),
        Map.entry('9', "<blue>"),
        Map.entry('a', "<green>"),
        Map.entry('b', "<aqua>"),
        Map.entry('c', "<red>"),
        Map.entry('d', "<light_purple>"),
        Map.entry('e', "<yellow>"),
        Map.entry('f', "<white>"),
        Map.entry('k', "<obfuscated>"),
        Map.entry('l', "<bold>"),
        Map.entry('m', "<strikethrough>"),
        Map.entry('n', "<underlined>"),
        Map.entry('o', "<italic>"),
        Map.entry('r', "<reset>")
    );

    private Text() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(component(input));
    }

    public static Component component(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(toMiniMessage(input));
        } catch (Exception ignored) {
            return LEGACY_SERIALIZER.deserialize(ChatColor.translateAlternateColorCodes('&', input));
        }
    }

    public static List<Component> components(String input) {
        if (input == null) {
            return List.of(Component.empty());
        }
        String[] lines = input.split("\\R", -1);
        List<Component> components = new ArrayList<>(lines.length);
        for (String line : lines) {
            components.add(component(line));
        }
        return components;
    }

    public static String replace(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return input == null ? "" : input;
        }
        String output = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return output;
    }

    public static String stripLeadingSlash(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static String toMiniMessage(String input) {
        StringBuilder builder = new StringBuilder(input.length() + 16);
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current == '&' || current == ChatColor.COLOR_CHAR) && index + 1 < input.length()) {
                String repeatedHex = parseRepeatedHex(input, index, current);
                if (repeatedHex != null) {
                    builder.append("<color:#").append(repeatedHex).append('>');
                    index += 13;
                    continue;
                }
                char next = input.charAt(index + 1);
                if (next == '#' && index + 7 < input.length()) {
                    String compactHex = input.substring(index + 2, index + 8);
                    if (isHex(compactHex)) {
                        builder.append("<color:#").append(compactHex).append('>');
                        index += 7;
                        continue;
                    }
                }
                String tag = LEGACY_TAGS.get(Character.toLowerCase(next));
                if (tag != null) {
                    builder.append(tag);
                    index++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static String parseRepeatedHex(String input, int start, char marker) {
        if (start + 13 >= input.length()) {
            return null;
        }
        if (Character.toLowerCase(input.charAt(start + 1)) != 'x') {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int offset = 0; offset < 6; offset++) {
            int markerIndex = start + 2 + (offset * 2);
            int valueIndex = markerIndex + 1;
            if (input.charAt(markerIndex) != marker || !isHexDigit(input.charAt(valueIndex))) {
                return null;
            }
            hex.append(input.charAt(valueIndex));
        }
        return hex.toString();
    }

    private static boolean isHex(String input) {
        for (int index = 0; index < input.length(); index++) {
            if (!isHexDigit(input.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char value) {
        return (value >= '0' && value <= '9')
            || (value >= 'a' && value <= 'f')
            || (value >= 'A' && value <= 'F');
    }
}