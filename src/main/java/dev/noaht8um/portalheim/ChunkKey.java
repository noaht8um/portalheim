package dev.noaht8um.portalheim;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
}
