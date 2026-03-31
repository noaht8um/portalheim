package dev.noaht8um.portalheim;

import java.util.Optional;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;

public final class PortalDetector {
    private static final int MIN_FRAME_WIDTH = 4;
    private static final int MAX_FRAME_WIDTH = 23;
    private static final int MIN_FRAME_HEIGHT = 5;
    private static final int MAX_FRAME_HEIGHT = 23;
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    public Optional<PortalRecord> detect(Sign sign) {
        Block signBlock = sign.getBlock();
        if (signBlock.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return Optional.empty();
        }

        BlockData data = signBlock.getBlockData();
        if (!(data instanceof org.bukkit.block.data.type.WallSign wallSign)) {
            return Optional.empty();
        }

        BlockFace facing = wallSign.getFacing();
        PortalOrientation orientation = switch (facing) {
            case NORTH, SOUTH -> PortalOrientation.X;
            case EAST, WEST -> PortalOrientation.Z;
            default -> null;
        };

        if (orientation == null) {
            return Optional.empty();
        }

        Block attachedFrame = signBlock.getRelative(facing.getOppositeFace());
        if (!isFrameMaterial(attachedFrame.getType())) {
            return Optional.empty();
        }

        String tag = TagNormalizer.normalize(PLAIN_TEXT.serialize(sign.getSide(Side.FRONT).line(0)));
        return detectFrame(signBlock, attachedFrame, orientation, facing, tag);
    }

    private Optional<PortalRecord> detectFrame(
        Block signBlock,
        Block attachedFrame,
        PortalOrientation orientation,
        BlockFace frontFace,
        String tag
    ) {
        World world = signBlock.getWorld();
        int attachedHorizontal = orientation == PortalOrientation.X ? attachedFrame.getX() : attachedFrame.getZ();
        int fixedCoordinate = orientation == PortalOrientation.X ? attachedFrame.getZ() : attachedFrame.getX();
        int minHorizontal = attachedHorizontal - (MAX_FRAME_WIDTH - 1);
        int minY = attachedFrame.getY() - (MAX_FRAME_HEIGHT - 1);
        int maxY = attachedFrame.getY();
        int maxHorizontal = attachedHorizontal;

        PortalRecord bestMatch = null;

        for (int baseY = minY; baseY <= maxY; baseY++) {
            for (int baseHorizontal = minHorizontal; baseHorizontal <= maxHorizontal; baseHorizontal++) {
                BlockKey anchor = orientation == PortalOrientation.X
                    ? new BlockKey(world.getUID(), baseHorizontal, baseY, fixedCoordinate)
                    : new BlockKey(world.getUID(), fixedCoordinate, baseY, baseHorizontal);

                if (!isPotentialBottomLeftCorner(world, anchor, orientation)) {
                    continue;
                }

                CandidateFrame candidate = measureCandidate(world, anchor, orientation);
                if (candidate == null) {
                    continue;
                }

                if (!containsBorder(attachedFrame, anchor, orientation, candidate.width(), candidate.height())) {
                    continue;
                }

                if (!isValidFrame(world, anchor, orientation, candidate.width(), candidate.height())) {
                    continue;
                }

                PortalRecord match = new PortalRecord(
                    anchor,
                    BlockKey.fromBlock(signBlock),
                    orientation,
                    candidate.width(),
                    candidate.height(),
                    frontFace,
                    tag,
                    false
                );

                if (bestMatch == null || area(match) < area(bestMatch)) {
                    bestMatch = match;
                }
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    private boolean isPotentialBottomLeftCorner(World world, BlockKey anchor, PortalOrientation orientation) {
        Block anchorBlock = blockAt(world, anchor, orientation, 0, 0);
        if (!isFrameMaterial(anchorBlock.getType())) {
            return false;
        }

        Block leftNeighbor = blockAt(world, anchor, orientation, -1, 0);
        Block belowNeighbor = blockAt(world, anchor, orientation, 0, -1);
        return !isFrameMaterial(leftNeighbor.getType()) && !isFrameMaterial(belowNeighbor.getType());
    }

    private CandidateFrame measureCandidate(World world, BlockKey anchor, PortalOrientation orientation) {
        int width = 0;
        while (width < MAX_FRAME_WIDTH && isFrameMaterial(blockAt(world, anchor, orientation, width, 0).getType())) {
            width++;
        }

        int height = 0;
        while (height < MAX_FRAME_HEIGHT && isFrameMaterial(blockAt(world, anchor, orientation, 0, height).getType())) {
            height++;
        }

        if (width < MIN_FRAME_WIDTH || height < MIN_FRAME_HEIGHT) {
            return null;
        }

        return new CandidateFrame(width, height);
    }

    private boolean containsBorder(Block attachedFrame, BlockKey anchor, PortalOrientation orientation, int frameWidth, int frameHeight) {
        int horizontalOffset = orientation == PortalOrientation.X ? attachedFrame.getX() - anchor.x() : attachedFrame.getZ() - anchor.z();
        int verticalOffset = attachedFrame.getY() - anchor.y();

        return horizontalOffset >= 0
            && horizontalOffset < frameWidth
            && verticalOffset >= 0
            && verticalOffset < frameHeight
            && (horizontalOffset == 0 || horizontalOffset == frameWidth - 1 || verticalOffset == 0 || verticalOffset == frameHeight - 1);
    }

    private boolean isValidFrame(World world, BlockKey anchor, PortalOrientation orientation, int frameWidth, int frameHeight) {
        int cryingObsidianCount = 0;

        for (int yOffset = 0; yOffset < frameHeight; yOffset++) {
            for (int horizontalOffset = 0; horizontalOffset < frameWidth; horizontalOffset++) {
                Block block = blockAt(world, anchor, orientation, horizontalOffset, yOffset);
                boolean border = horizontalOffset == 0 || horizontalOffset == frameWidth - 1 || yOffset == 0 || yOffset == frameHeight - 1;

                if (border) {
                    if (!isFrameMaterial(block.getType())) {
                        return false;
                    }
                    if (block.getType() == Material.CRYING_OBSIDIAN) {
                        cryingObsidianCount++;
                    }
                    continue;
                }

                if (!isInteriorClear(block)) {
                    return false;
                }
            }
        }

        return cryingObsidianCount >= 1;
    }

    private Block blockAt(World world, BlockKey anchor, PortalOrientation orientation, int horizontalOffset, int yOffset) {
        if (orientation == PortalOrientation.X) {
            return world.getBlockAt(anchor.x() + horizontalOffset, anchor.y() + yOffset, anchor.z());
        }
        return world.getBlockAt(anchor.x(), anchor.y() + yOffset, anchor.z() + horizontalOffset);
    }

    private boolean isFrameMaterial(Material material) {
        return material == Material.OBSIDIAN || material == Material.CRYING_OBSIDIAN;
    }

    private boolean isInteriorClear(Block block) {
        return block.getType().isAir() || block.isPassable();
    }

    private int area(PortalRecord portal) {
        return portal.frameWidth() * portal.frameHeight();
    }

    private record CandidateFrame(int width, int height) {
    }
}
