package dev.noaht8um.portalheim;

import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

public record BlockKey(UUID worldId, int x, int y, int z) {
    public static BlockKey fromBlock(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public ChunkKey chunkKey() {
        return new ChunkKey(worldId, x >> 4, z >> 4);
    }

    public @Nullable World resolveWorld(Server server) {
        return server.getWorld(worldId);
    }

    public @Nullable Block resolveBlock(Server server) {
        World world = resolveWorld(server);
        if (world == null) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    public String storageKey() {
        return worldId + "_" + x + "_" + y + "_" + z;
    }

    public String describe(Server server) {
        World world = resolveWorld(server);
        String worldName = world != null ? world.getName() : worldId.toString();
        return worldName + " [" + x + ", " + y + ", " + z + "]";
    }
}
