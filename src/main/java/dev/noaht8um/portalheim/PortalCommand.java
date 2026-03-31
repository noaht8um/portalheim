package dev.noaht8um.portalheim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class PortalCommand implements TabExecutor {
    private final PortalheimPlugin plugin;
    private final PortalManager portalManager;

    public PortalCommand(PortalheimPlugin plugin, PortalManager portalManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/portalheim <reload|list|inspect>");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "reload" -> {
                portalManager.reloadAll();
                sender.sendMessage("Portalheim reloaded.");
                return true;
            }
            case "list" -> {
                String normalizedTag = args.length > 1
                    ? TagNormalizer.normalize(String.join(" ", Arrays.copyOfRange(args, 1, args.length)))
                    : null;

                List<PortalRecord> portals = portalManager.listPortals(normalizedTag);
                if (portals.isEmpty()) {
                    sender.sendMessage(normalizedTag == null ? "No portals found." : "No portals found for tag '" + normalizedTag + "'.");
                    return true;
                }

                sender.sendMessage("Portalheim portals:");
                portals.forEach(portal -> sender.sendMessage(" - " + portal.describe(plugin.getServer())));
                return true;
            }
            case "inspect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can inspect portals.");
                    return true;
                }

                Optional<PortalRecord> portal = portalManager.inspect(player);
                if (portal.isEmpty()) {
                    sender.sendMessage("No portal found nearby.");
                    return true;
                }

                sender.sendMessage("Tag: " + (portal.get().tag().isBlank() ? "<empty>" : portal.get().tag()));
                sender.sendMessage("State: " + (portal.get().active() ? "active" : "inactive"));
                sender.sendMessage("Anchor: " + portal.get().anchor().describe(plugin.getServer()));
                sender.sendMessage("Sign: " + portal.get().sign().describe(plugin.getServer()));
                sender.sendMessage("Orientation: " + portal.get().orientation());
                sender.sendMessage("Linked: " + portalManager.linkedPortal(portal.get())
                    .map(linked -> linked.anchor().describe(plugin.getServer()))
                    .orElse("none"));
                return true;
            }
            default -> {
                sender.sendMessage("/portalheim <reload|list|inspect>");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "list", "inspect").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }

        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            return portalManager.knownTags().stream()
                .filter(tag -> tag.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .toList();
        }

        return new ArrayList<>();
    }
}
