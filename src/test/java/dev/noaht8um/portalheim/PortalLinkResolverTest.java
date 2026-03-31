package dev.noaht8um.portalheim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class PortalLinkResolverTest {
    private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void exactlyTwoPortalsWithSameTagBecomeActive() {
        PortalRecord first = portal(0, 64, 0, 5, 64, 0, "meadow");
        PortalRecord second = portal(100, 64, 100, 105, 64, 100, "meadow");

        Set<BlockKey> active = PortalLinkResolver.resolveActiveSigns(List.of(first, second));
        assertEquals(Set.of(first.sign(), second.sign()), active);
    }

    @Test
    void thirdPortalDisablesWholeTagGroup() {
        PortalRecord first = portal(0, 64, 0, 5, 64, 0, "swamp");
        PortalRecord second = portal(100, 64, 100, 105, 64, 100, "swamp");
        PortalRecord third = portal(200, 64, 200, 205, 64, 200, "swamp");

        Set<BlockKey> active = PortalLinkResolver.resolveActiveSigns(List.of(first, second, third));
        assertTrue(active.isEmpty());
    }

    private static PortalRecord portal(int anchorX, int anchorY, int anchorZ, int signX, int signY, int signZ, String tag) {
        return new PortalRecord(
            new BlockKey(WORLD_ID, anchorX, anchorY, anchorZ),
            new BlockKey(WORLD_ID, signX, signY, signZ),
            PortalOrientation.X,
            4,
            5,
            BlockFace.NORTH,
            tag,
            false
        );
    }
}
