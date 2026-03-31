package dev.noaht8um.portalheim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Server;
import org.bukkit.World;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

public final class SquaremapIntegration {
    private static final Key LAYER_KEY = Key.of("portalheim.portals");
    private static final Key ACTIVE_ICON_KEY = Key.of("portalheim.portal.active");
    private static final Key INACTIVE_ICON_KEY = Key.of("portalheim.portal.inactive");
    private static final int ICON_SIZE = 30;
    private static final Color ACTIVE_RING = new Color(0x58, 0xD6, 0x9A);
    private static final Color ACTIVE_GLOW = new Color(0x8D, 0xF5, 0xD0, 175);
    private static final Color INACTIVE_RING = new Color(0xD9, 0xA3, 0x54);
    private static final Color INACTIVE_GLOW = new Color(0xF2, 0xCC, 0x8A, 165);
    private static final Color FRAME = new Color(0x2A, 0x1E, 0x14);
    private static final Color CORE = new Color(0x0F, 0x16, 0x12, 210);
    private static final Color SHADOW = new Color(0, 0, 0, 95);
    private static final Color HIGHLIGHT = new Color(255, 255, 255, 140);

    private final PortalheimPlugin plugin;
    private final PortalManager portalManager;
    private final Map<WorldIdentifier, SimpleLayerProvider> providers = new HashMap<>();

    private Squaremap api;

    public SquaremapIntegration(PortalheimPlugin plugin, PortalManager portalManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
    }

    public void enable() {
        if (plugin.getServer().getPluginManager().getPlugin("squaremap") == null) {
            return;
        }

        try {
            api = SquaremapProvider.get();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning("squaremap was present but its API was not ready. Portal markers will stay disabled.");
            return;
        }

        registerIcons();
        refresh();
        plugin.getLogger().info("Enabled squaremap portal layer integration.");
    }

    public void disable() {
        if (api == null) {
            return;
        }

        for (Map.Entry<WorldIdentifier, SimpleLayerProvider> entry : providers.entrySet()) {
            api.getWorldIfEnabled(entry.getKey()).ifPresent(mapWorld -> unregisterLayer(mapWorld.layerRegistry()));
        }

        providers.clear();
        unregisterIcons();
        api = null;
    }

    public void refresh() {
        if (api == null) {
            return;
        }

        Map<UUID, List<PortalRecord>> portalsByWorld = portalManager.listPortals(null).stream()
            .collect(Collectors.groupingBy(PortalRecord::worldId));

        for (MapWorld mapWorld : api.mapWorlds()) {
            World world = BukkitAdapter.bukkitWorld(mapWorld);
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }

            SimpleLayerProvider provider = providers.computeIfAbsent(
                mapWorld.identifier(),
                unused -> registerLayer(mapWorld.layerRegistry(), world.getName())
            );

            provider.clearMarkers();
            for (PortalRecord portal : portalsByWorld.getOrDefault(world.getUID(), List.of())) {
                provider.addMarker(markerKey(portal), markerFor(portal, plugin.getServer()));
            }
        }
    }

    private SimpleLayerProvider registerLayer(Registry<xyz.jpenilla.squaremap.api.LayerProvider> registry, String worldName) {
        if (registry.hasEntry(LAYER_KEY)) {
            return (SimpleLayerProvider) registry.get(LAYER_KEY);
        }

        SimpleLayerProvider provider = SimpleLayerProvider.builder("Portalheim")
            .layerPriority(40)
            .zIndex(40)
            .defaultHidden(false)
            .showControls(true)
            .build();
        registry.register(LAYER_KEY, provider);
        plugin.debug("Registered squaremap layer for " + worldName + ".");
        return provider;
    }

    private void unregisterLayer(Registry<xyz.jpenilla.squaremap.api.LayerProvider> registry) {
        if (registry.hasEntry(LAYER_KEY)) {
            registry.unregister(LAYER_KEY);
        }
    }

    private Key markerKey(PortalRecord portal) {
        return Key.of("portalheim.portal." + portal.sign().storageKey());
    }

    private Marker markerFor(PortalRecord portal, Server server) {
        String tooltip = tooltipFor(portal, server);
        MarkerOptions options = MarkerOptions.builder()
            .hoverTooltip(tooltip)
            .clickTooltip(tooltip)
            .build();

        return Marker.icon(
            Point.of(portal.center(server).getX(), portal.center(server).getZ()),
            portal.active() ? ACTIVE_ICON_KEY : INACTIVE_ICON_KEY,
            ICON_SIZE
        ).markerOptions(options);
    }

    private String tooltipFor(PortalRecord portal, Server server) {
        String tag = portal.tag().isBlank() ? "(empty)" : escapeHtml(portal.tag());
        String state = portal.active() ? "active" : "inactive";
        String link = portalManager.linkedPortal(portal)
            .map(other -> escapeHtml(other.anchor().describe(server)))
            .orElse("none");

        return "<div>"
            + "<strong>Portalheim</strong><br>"
            + "Tag: " + tag + "<br>"
            + "State: " + state + "<br>"
            + "Facing: " + portal.frontFace().name() + "<br>"
            + "Location: " + escapeHtml(portal.anchor().describe(server)) + "<br>"
            + "Linked: " + link
            + "</div>";
    }

    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private void registerIcons() {
        Registry<BufferedImage> iconRegistry = api.iconRegistry();
        registerIcon(iconRegistry, ACTIVE_ICON_KEY, createIcon(ACTIVE_RING, ACTIVE_GLOW));
        registerIcon(iconRegistry, INACTIVE_ICON_KEY, createIcon(INACTIVE_RING, INACTIVE_GLOW));
    }

    private void unregisterIcons() {
        Registry<BufferedImage> iconRegistry = api.iconRegistry();
        unregisterIcon(iconRegistry, ACTIVE_ICON_KEY);
        unregisterIcon(iconRegistry, INACTIVE_ICON_KEY);
    }

    private void registerIcon(Registry<BufferedImage> iconRegistry, Key key, BufferedImage image) {
        if (iconRegistry.hasEntry(key)) {
            iconRegistry.unregister(key);
        }
        iconRegistry.register(key, image);
    }

    private void unregisterIcon(Registry<BufferedImage> iconRegistry, Key key) {
        if (iconRegistry.hasEntry(key)) {
            iconRegistry.unregister(key);
        }
    }

    private BufferedImage createIcon(Color ringColor, Color glowColor) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            graphics.setColor(SHADOW);
            graphics.fillOval(4, 5, 22, 22);

            graphics.setColor(glowColor);
            graphics.fillOval(3, 3, 24, 24);

            graphics.setColor(FRAME);
            graphics.fillRoundRect(6, 4, 18, 22, 8, 8);

            graphics.setColor(ringColor);
            graphics.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawRoundRect(7, 5, 16, 20, 7, 7);

            graphics.setColor(CORE);
            graphics.fillRoundRect(10, 8, 10, 14, 5, 5);

            graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(HIGHLIGHT);
            graphics.drawArc(9, 6, 8, 7, 120, 120);
            graphics.drawLine(15, 8, 15, 20);
            graphics.drawLine(17, 10, 17, 18);
        } finally {
            graphics.dispose();
        }

        return image;
    }
}
