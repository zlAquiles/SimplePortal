package com.aquiles.simpleportals.command;

import com.aquiles.simpleportals.SimplePortalsPlugin;
import com.aquiles.simpleportals.config.ConfigService;
import com.aquiles.simpleportals.data.Cuboid;
import com.aquiles.simpleportals.data.DestinationDefinition;
import com.aquiles.simpleportals.data.PortalDefinition;
import com.aquiles.simpleportals.data.PortalTrigger;
import com.aquiles.simpleportals.service.PortalStore;
import com.aquiles.simpleportals.service.SelectionService;
import com.aquiles.simpleportals.service.TeleportService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PortalCommand implements CommandExecutor, TabCompleter {

    private static final Pattern TAG_PATTERN = Pattern.compile("(\\w+):(?:\"([^\"]*)\"|(\\S+))");
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final SimplePortalsPlugin plugin;
    private final ConfigService configService;
    private final PortalStore portalStore;
    private final SelectionService selectionService;
    private final TeleportService teleportService;

    public PortalCommand(
        SimplePortalsPlugin plugin,
        ConfigService configService,
        PortalStore portalStore,
        SelectionService selectionService,
        TeleportService teleportService
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.portalStore = portalStore;
        this.selectionService = selectionService;
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "create" -> handleCreate(sender, args);
            case "wand" -> handleWand(sender);
            case "reload" -> handleReload(sender);
            case "show" -> handleShow(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "setblock" -> handleSetBlock(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "destination" -> handleDestination(sender, args);
            default -> {
                configService.send(sender, "errors.unknown_subcommand");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(availableSubCommands(sender), args[0]);
        }
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if ((subCommand.equals("remove") || subCommand.equals("delete")) && args.length == 2) {
            return filterByPrefix(portalStore.getPortals().stream().map(PortalDefinition::name).toList(), args[1]);
        }
        if (subCommand.equals("show") && args.length == 2) {
            return filterByPrefix(List.of("5", "10", "20", "50", "100"), args[1]);
        }
        if (subCommand.equals("setblock")) {
            return completeSetBlockArguments(args);
        }
        if (subCommand.equals("tp") && args.length == 2) {
            return filterByPrefix(portalStore.getDestinations().stream().map(DestinationDefinition::name).toList(), args[1]);
        }
        if (subCommand.equals("destination")) {
            if (args.length == 2) {
                return filterByPrefix(List.of("create", "remove"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                return filterByPrefix(portalStore.getDestinations().stream().map(DestinationDefinition::name).toList(), args[2]);
            }
        }
        if (subCommand.equals("create")) {
            return completeCreateArguments(args);
        }
        return List.of();
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.create")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Map<String, String> tags = parseTaggedArguments(args, 1);
        String destinationName = firstTag(tags, "desti", "destination");
        if (destinationName == null) {
            configService.send(player, "errors.create_syntax");
            return true;
        }
        if (!isValidName(destinationName)) {
            configService.send(player, "errors.invalid_name");
            return true;
        }
        if (!portalStore.hasDestination(destinationName)) {
            configService.send(player, "errors.destination_missing", "destination", destinationName);
            return true;
        }

        String requestedPortalName = firstTag(tags, "name");
        if (requestedPortalName != null && !requestedPortalName.isBlank()) {
            if (!isValidName(requestedPortalName)) {
                configService.send(player, "errors.invalid_name");
                return true;
            }
            if (portalStore.hasPortal(requestedPortalName)) {
                configService.send(player, "errors.portal_exists", "portal", requestedPortalName);
                return true;
            }
        }

        List<PortalTrigger> triggerBlocks = parseTriggers(sender, tags.getOrDefault("block", tags.getOrDefault("blocks", configService.defaultTriggerName())));
        if (triggerBlocks == null) {
            return true;
        }

        if (!selectionService.hasCompleteSelection(player)) {
            configService.send(player, "errors.selection_missing");
            return true;
        }
        if (selectionService.hasWorldMismatch(player)) {
            configService.send(player, "errors.selection_world_mismatch");
            return true;
        }

        Cuboid cuboid = selectionService.getSelectionCuboid(player).orElse(null);
        if (cuboid == null) {
            configService.send(player, "errors.selection_missing");
            return true;
        }

        String portalName = requestedPortalName == null || requestedPortalName.isBlank()
            ? portalStore.nextPortalName()
            : requestedPortalName;

        PortalDefinition portal = new PortalDefinition(
            portalName,
            destinationName,
            cuboid,
            true,
            triggerBlocks,
            configService.defaultCooldownSeconds(),
            "",
            PortalDefinition.Conditions.disabled(),
            PortalDefinition.Actions.defaults()
        );
        if (!portalStore.createPortal(portal)) {
            configService.send(player, "errors.portal_exists", "portal", portal.name());
            return true;
        }

        if (configService.replaceAirEnabled() && !triggerBlocks.isEmpty()) {
            replaceAirWithTrigger(player, cuboid, triggerBlocks.get(0));
        }

        selectionService.completeSelection(player);
        configService.send(player, "status.portal_created", "portal", portal.name());
        return true;
    }

    private boolean handleWand(CommandSender sender) {
        if (!hasPermission(sender, "simpleportals.command.wand")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        selectionService.giveWand(player);
        configService.send(player, "status.wand_given");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "simpleportals.command.reload")) {
            return true;
        }
        plugin.reloadPluginState();
        configService.send(sender, "status.reload_complete");
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.show")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            configService.send(sender, "errors.missing_argument", "argument", "radius");
            return true;
        }
        double radius;
        try {
            radius = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            configService.send(sender, "errors.invalid_radius");
            return true;
        }
        if (radius <= 0.0D) {
            configService.send(sender, "errors.invalid_radius");
            return true;
        }
        if (radius > configService.maxShowRadius()) {
            configService.send(sender, "errors.show_radius_limit", "max", Integer.toString(configService.maxShowRadius()));
            return true;
        }

        List<PortalDefinition> nearbyPortals = portalStore.getNearbyPortals(player.getLocation(), radius);
        if (nearbyPortals.isEmpty()) {
            configService.send(player, "status.no_portals_nearby", "radius", Integer.toString((int) radius));
            return true;
        }
        for (PortalDefinition portal : nearbyPortals) {
            double distance = Math.sqrt(portal.distanceSquared(player.getLocation()));
            configService.send(player, "status.portal_line",
                "portal", portal.name(),
                "destination", portal.destinationName(),
                "blocks", String.join(",", portal.triggerBlocks().stream().map(Enum::name).toList()),
                "distance", String.format(Locale.US, "%.1f", distance)
            );
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.remove")) {
            return true;
        }
        if (args.length < 2) {
            configService.send(sender, "errors.missing_argument", "argument", "name");
            return true;
        }
        if (!portalStore.removePortal(args[1])) {
            configService.send(sender, "errors.portal_missing", "portal", args[1]);
            return true;
        }
        configService.send(sender, "status.portal_removed", "portal", args[1]);
        return true;
    }

    private boolean handleSetBlock(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.setblock")) {
            return true;
        }
        if (args.length < 2) {
            configService.send(sender, "errors.missing_argument", "argument", "portal");
            return true;
        }
        if (args.length < 3) {
            configService.send(sender, "errors.missing_argument", "argument", "block");
            return true;
        }
        PortalDefinition portal = portalStore.getPortal(args[1]).orElse(null);
        if (portal == null) {
            configService.send(sender, "errors.portal_missing", "portal", args[1]);
            return true;
        }
        Optional<PortalTrigger> trigger = PortalTrigger.fromInput(args[2]);
        if (trigger.isEmpty()) {
            configService.send(sender, "errors.invalid_trigger", "triggers", configService.triggerList());
            return true;
        }
        PortalTrigger newTrigger = trigger.get();
        if (!portalStore.updatePortalTriggers(portal.name(), List.of(newTrigger))) {
            configService.send(sender, "errors.portal_missing", "portal", portal.name());
            return true;
        }
        replacePortalBlocks(portal, newTrigger);
        configService.send(sender, "status.portal_block_updated", "portal", portal.name(), "block", newTrigger.name());
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.tp")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            configService.send(sender, "errors.missing_argument", "argument", "name");
            return true;
        }
        teleportService.teleportToDestination(player, args[1]);
        return true;
    }

    private boolean handleDestination(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create" -> handleDestinationCreate(sender, args);
            case "remove" -> handleDestinationRemove(sender, args);
            default -> {
                configService.send(sender, "errors.unknown_subcommand");
                yield true;
            }
        };
    }

    private boolean handleDestinationCreate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.destination.create")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 3) {
            configService.send(sender, "errors.missing_argument", "argument", "name");
            return true;
        }
        String name = args[2];
        if (!isValidName(name)) {
            configService.send(sender, "errors.invalid_name");
            return true;
        }
        if (!portalStore.createDestination(DestinationDefinition.local(name, player.getLocation()))) {
            configService.send(sender, "errors.destination_exists", "destination", name);
            return true;
        }
        configService.send(sender, "status.destination_created", "destination", name);
        return true;
    }

    private boolean handleDestinationRemove(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "simpleportals.command.destination.remove")) {
            return true;
        }
        if (args.length < 3) {
            configService.send(sender, "errors.missing_argument", "argument", "name");
            return true;
        }
        String name = args[2];
        if (!portalStore.hasDestination(name)) {
            configService.send(sender, "errors.destination_missing", "destination", name);
            return true;
        }
        if (portalStore.isDestinationInUse(name)) {
            configService.send(sender, "errors.destination_in_use", "destination", name);
            return true;
        }
        portalStore.removeDestination(name);
        configService.send(sender, "status.destination_removed", "destination", name);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        if (!sender.hasPermission("simpleportals.command.help")) {
            configService.send(sender, "errors.no_permission");
            return;
        }
        configService.sendRaw(sender, "help.header");
        if (sender.hasPermission("simpleportals.command.create")) {
            configService.sendRaw(sender, "help.create");
        }
        if (sender.hasPermission("simpleportals.command.wand")) {
            configService.sendRaw(sender, "help.wand");
        }
        if (sender.hasPermission("simpleportals.command.reload")) {
            configService.sendRaw(sender, "help.reload");
        }
        if (sender.hasPermission("simpleportals.command.show")) {
            configService.sendRaw(sender, "help.show");
        }
        if (sender.hasPermission("simpleportals.command.remove")) {
            configService.sendRaw(sender, "help.remove");
        }
        if (sender.hasPermission("simpleportals.command.setblock")) {
            configService.sendRaw(sender, "help.setblock");
        }
        if (sender.hasPermission("simpleportals.command.tp")) {
            configService.sendRaw(sender, "help.tp");
        }
        if (sender.hasPermission("simpleportals.command.destination.create")) {
            configService.sendRaw(sender, "help.destination_create");
        }
        if (sender.hasPermission("simpleportals.command.destination.remove")) {
            configService.sendRaw(sender, "help.destination_remove");
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        configService.send(sender, "errors.no_permission");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        configService.send(sender, "errors.player_only");
        return null;
    }

    private String firstTag(Map<String, String> tags, String... keys) {
        for (String key : keys) {
            if (tags.containsKey(key)) {
                return tags.get(key);
            }
        }
        return null;
    }

    private Map<String, String> parseTaggedArguments(String[] args, int startIndex) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (args.length <= startIndex) {
            return tags;
        }
        String joined = String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
        Matcher matcher = TAG_PATTERN.matcher(joined);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String quotedValue = matcher.group(2);
            String plainValue = matcher.group(3);
            tags.put(key, quotedValue != null ? quotedValue : plainValue);
        }
        return tags;
    }

    private void replacePortalBlocks(PortalDefinition portal, PortalTrigger newTrigger) {
        World world = Bukkit.getWorld(portal.region().worldName());
        if (world == null) {
            return;
        }
        Material material = newTrigger.placementMaterial();
        for (int x = portal.region().minX(); x <= portal.region().maxX(); x++) {
            for (int y = portal.region().minY(); y <= portal.region().maxY(); y++) {
                for (int z = portal.region().minZ(); z <= portal.region().maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!shouldReplacePortalBlock(block, portal.triggerBlocks())) {
                        continue;
                    }
                    block.setType(material, false);
                }
            }
        }
    }

    private boolean shouldReplacePortalBlock(Block block, List<PortalTrigger> currentTriggers) {
        return block.isEmpty() || currentTriggers.stream().anyMatch(trigger -> trigger.matches(block));
    }

    private void replaceAirWithTrigger(Player player, Cuboid cuboid, PortalTrigger trigger) {
        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(cuboid.worldName())) {
            return;
        }
        Material material = trigger.placementMaterial();
        for (int x = cuboid.minX(); x <= cuboid.maxX(); x++) {
            for (int y = cuboid.minY(); y <= cuboid.maxY(); y++) {
                for (int z = cuboid.minZ(); z <= cuboid.maxZ(); z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty()) {
                        continue;
                    }
                    block.setType(material, false);
                }
            }
        }
    }

    private List<PortalTrigger> parseTriggers(CommandSender sender, String rawValue) {
        List<PortalTrigger> triggers = new ArrayList<>();
        for (String token : rawValue.split(",")) {
            Optional<PortalTrigger> trigger = PortalTrigger.fromInput(token.trim());
            if (trigger.isEmpty()) {
                configService.send(sender, "errors.invalid_trigger", "triggers", configService.triggerList());
                return null;
            }
            triggers.add(trigger.get());
        }
        return triggers;
    }

    private boolean isValidName(String value) {
        return VALID_NAME.matcher(value).matches();
    }

    private List<String> availableSubCommands(CommandSender sender) {
        List<String> commands = new ArrayList<>();
        if (sender.hasPermission("simpleportals.command.create")) {
            commands.add("create");
        }
        if (sender.hasPermission("simpleportals.command.wand")) {
            commands.add("wand");
        }
        if (sender.hasPermission("simpleportals.command.reload")) {
            commands.add("reload");
        }
        if (sender.hasPermission("simpleportals.command.show")) {
            commands.add("show");
        }
        if (sender.hasPermission("simpleportals.command.remove")) {
            commands.add("remove");
        }
        if (sender.hasPermission("simpleportals.command.setblock")) {
            commands.add("setblock");
        }
        if (sender.hasPermission("simpleportals.command.tp")) {
            commands.add("tp");
        }
        if (sender.hasPermission("simpleportals.command.destination.create")
            || sender.hasPermission("simpleportals.command.destination.remove")) {
            commands.add("destination");
        }
        commands.add("help");
        return commands;
    }

    private List<String> completeSetBlockArguments(String[] args) {
        if (args.length == 2) {
            String token = args[1].toLowerCase(Locale.ROOT);
            return portalStore.getPortals().stream()
                .filter(portal -> portal.name().toLowerCase(Locale.ROOT).startsWith(token)
                    || portal.destinationName().toLowerCase(Locale.ROOT).startsWith(token))
                .map(PortalDefinition::name)
                .distinct()
                .toList();
        }
        if (args.length == 3) {
            return filterByPrefix(PortalTrigger.names(), args[2]);
        }
        return List.of();
    }

    private List<String> completeCreateArguments(String[] args) {
        String current = args[args.length - 1];
        if (current.startsWith("block:")) {
            return filterByPrefix(PortalTrigger.names().stream().map(value -> "block:" + value).toList(), current);
        }
        if (current.startsWith("desti:")) {
            return filterByPrefix(portalStore.getDestinations().stream().map(destination -> "desti:" + destination.name()).toList(), current);
        }
        if (current.startsWith("destination:")) {
            return filterByPrefix(portalStore.getDestinations().stream().map(destination -> "destination:" + destination.name()).toList(), current);
        }
        return filterByPrefix(List.of("desti:", "block:WATER"), current);
    }

    private List<String> filterByPrefix(List<String> suggestions, String token) {
        String loweredToken = token.toLowerCase(Locale.ROOT);
        return suggestions.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(loweredToken))
            .toList();
    }
}