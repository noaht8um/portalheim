package dev.noaht8um.portalheim;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public record PortalRecord(
    BlockKey anchor,
    BlockKey sign,
    PortalOrientation orientation,
    int frameWidth,
    int frameHeight,
    BlockFace frontFace,
    String tag,
    boolean active
) {
    public UUID worldId() {
        return anchor.worldId();
    }

    public PortalRecord withActive(boolean value) {
        return new PortalRecord(anchor, sign, orientation, frameWidth, frameHeight, frontFace, tag, value);
    }

    public boolean containsFrameBlock(BlockKey block) {
        if (!worldId().equals(block.worldId())) {
            return false;
        }

        int planeOffset = orientation == PortalOrientation.X ? block.z() - anchor.z() : block.x() - anchor.x();
        int horizontalOffset = orientation == PortalOrientation.X ? block.x() - anchor.x() : block.z() - anchor.z();
        int verticalOffset = block.y() - anchor.y();

        return planeOffset == 0
            && horizontalOffset >= 0
            && horizontalOffset < frameWidth
            && verticalOffset >= 0
            && verticalOffset < frameHeight
            && (horizontalOffset == 0 || horizontalOffset == frameWidth - 1 || verticalOffset == 0 || verticalOffset == frameHeight - 1);
    }

    public boolean isRelatedBlock(BlockKey block) {
        if (!worldId().equals(block.worldId())) {
            return false;
        }
        if (containsFrameBlock(block) || sign.equals(block)) {
            return true;
        }

        if (orientation == PortalOrientation.X) {
            return block.x() >= anchor.x() - 1
                && block.x() <= anchor.x() + frameWidth
                && block.y() >= anchor.y() - 1
                && block.y() <= anchor.y() + frameHeight
                && block.z() >= anchor.z() - 2
                && block.z() <= anchor.z() + 2;
        }

        return block.x() >= anchor.x() - 2
            && block.x() <= anchor.x() + 2
            && block.y() >= anchor.y() - 1
            && block.y() <= anchor.y() + frameHeight
            && block.z() >= anchor.z() - 1
            && block.z() <= anchor.z() + frameWidth;
    }

    public Location center(Server server) {
        Location location;
        if (orientation == PortalOrientation.X) {
            location = new Location(anchor.resolveWorld(server), anchor.x() + (frameWidth / 2.0), anchor.y() + 1.0, anchor.z() + 0.5);
        } else {
            location = new Location(anchor.resolveWorld(server), anchor.x() + 0.5, anchor.y() + 1.0, anchor.z() + (frameWidth / 2.0));
        }
        return location;
    }

    public boolean containsPlayer(Player player) {
        if (!player.getWorld().getUID().equals(worldId())) {
            return false;
        }

        Location location = player.getLocation();
        if (location.getY() < anchor.y() + 1.0 || location.getY() > anchor.y() + frameHeight - 1.0) {
            return false;
        }

        if (orientation == PortalOrientation.X) {
            return location.getX() >= anchor.x() + 0.9
                && location.getX() <= anchor.x() + frameWidth - 0.9
                && Math.abs(location.getZ() - (anchor.z() + 0.5)) <= 0.7;
        }

        return location.getZ() >= anchor.z() + 0.9
            && location.getZ() <= anchor.z() + frameWidth - 0.9
            && Math.abs(location.getX() - (anchor.x() + 0.5)) <= 0.7;
    }

    public List<BlockKey> frameBlocks() {
        List<BlockKey> blocks = new ArrayList<>((frameWidth * 2) + (frameHeight * 2) - 4);
        for (int yOffset = 0; yOffset < frameHeight; yOffset++) {
            for (int horizontalOffset = 0; horizontalOffset < frameWidth; horizontalOffset++) {
                if (horizontalOffset != 0 && horizontalOffset != frameWidth - 1 && yOffset != 0 && yOffset != frameHeight - 1) {
                    continue;
                }

                if (orientation == PortalOrientation.X) {
                    blocks.add(new BlockKey(worldId(), anchor.x() + horizontalOffset, anchor.y() + yOffset, anchor.z()));
                } else {
                    blocks.add(new BlockKey(worldId(), anchor.x(), anchor.y() + yOffset, anchor.z() + horizontalOffset));
                }
            }
        }
        return blocks;
    }

    public float exitYaw() {
        return switch (frontFace) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    public String describe(Server server) {
        return "'" + tag + "' @ " + anchor.describe(server)
            + " (" + frameWidth + "x" + frameHeight + ", " + frontFace + ", " + (active ? "active" : "inactive") + ")";
    }
}
