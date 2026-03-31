package dev.noaht8um.portalheim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class PortalRecordTest {
    private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void borderCoordinatesAreRecognizedAsFrameBlocks() {
        PortalRecord portal = new PortalRecord(
            new BlockKey(WORLD_ID, 10, 64, 20),
            new BlockKey(WORLD_ID, 9, 65, 20),
            PortalOrientation.X,
            4,
            5,
            BlockFace.NORTH,
            "meadow",
            false
        );

        assertTrue(portal.containsFrameBlock(new BlockKey(WORLD_ID, 10, 64, 20)));
        assertTrue(portal.containsFrameBlock(new BlockKey(WORLD_ID, 13, 68, 20)));
        assertFalse(portal.containsFrameBlock(new BlockKey(WORLD_ID, 11, 65, 20)));
    }

    @Test
    void relatedBlockSearchCoversNearbyMutations() {
        PortalRecord portal = new PortalRecord(
            new BlockKey(WORLD_ID, 10, 64, 20),
            new BlockKey(WORLD_ID, 9, 65, 20),
            PortalOrientation.Z,
            4,
            5,
            BlockFace.EAST,
            "meadow",
            false
        );

        assertTrue(portal.isRelatedBlock(new BlockKey(WORLD_ID, 10, 65, 22)));
        assertTrue(portal.isRelatedBlock(new BlockKey(WORLD_ID, 9, 65, 20)));
        assertFalse(portal.isRelatedBlock(new BlockKey(WORLD_ID, 15, 70, 30)));
    }

    @Test
    void exitYawMatchesSignFrontFace() {
        PortalRecord portal = new PortalRecord(
            new BlockKey(WORLD_ID, 10, 64, 20),
            new BlockKey(WORLD_ID, 9, 65, 20),
            PortalOrientation.Z,
            6,
            7,
            BlockFace.SOUTH,
            "meadow",
            false
        );

        assertTrue(portal.exitYaw() == 0.0f);
    }
}
