package dev.noaht8um.portalheim;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PortalStorage {
    private final PortalheimPlugin plugin;
    private final File file;

    public PortalStorage(PortalheimPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    public Map<BlockKey, PortalRecord> load() {
        Map<BlockKey, PortalRecord> portals = new HashMap<>();
        if (!file.exists()) {
            return portals;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("portals");
        if (root == null) {
            return portals;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            try {
                UUID worldId = UUID.fromString(section.getString("world"));
                BlockKey anchor = new BlockKey(
                    worldId,
                    section.getInt("anchor.x"),
                    section.getInt("anchor.y"),
                    section.getInt("anchor.z")
                );
                BlockKey sign = new BlockKey(
                    worldId,
                    section.getInt("sign.x"),
                    section.getInt("sign.y"),
                    section.getInt("sign.z")
                );
                PortalOrientation orientation = PortalOrientation.valueOf(section.getString("orientation", PortalOrientation.X.name()));
                int frameWidth = section.getInt("frame.width", 4);
                int frameHeight = section.getInt("frame.height", 5);
                BlockFace frontFace = BlockFace.valueOf(section.getString("front-face", BlockFace.NORTH.name()));
                String tag = section.getString("tag", "");
                boolean active = section.getBoolean("active", false);
                portals.put(sign, new PortalRecord(anchor, sign, orientation, frameWidth, frameHeight, frontFace, tag, active));
            } catch (Exception exception) {
                plugin.getLogger().warning("Skipping malformed portal record '" + key + "': " + exception.getMessage());
            }
        }

        return portals;
    }

    public void save(Iterable<PortalRecord> portals) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Unable to create plugin data folder for portal persistence.");
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("portals");

        for (PortalRecord portal : portals) {
            ConfigurationSection section = root.createSection(portal.sign().storageKey());
            section.set("world", portal.worldId().toString());
            section.set("anchor.x", portal.anchor().x());
            section.set("anchor.y", portal.anchor().y());
            section.set("anchor.z", portal.anchor().z());
            section.set("sign.x", portal.sign().x());
            section.set("sign.y", portal.sign().y());
            section.set("sign.z", portal.sign().z());
            section.set("orientation", portal.orientation().name());
            section.set("frame.width", portal.frameWidth());
            section.set("frame.height", portal.frameHeight());
            section.set("front-face", portal.frontFace().name());
            section.set("tag", portal.tag());
            section.set("active", portal.active());
        }

        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save portals.yml: " + exception.getMessage());
        }
    }
}
