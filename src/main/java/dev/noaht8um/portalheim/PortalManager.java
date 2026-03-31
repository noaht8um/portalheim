package dev.noaht8um.portalheim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

public final class PortalManager {
    private static final int MAX_FRAME_RADIUS = 22;

    private final PortalheimPlugin plugin;
    private final PortalDetector detector;
    private final PortalStorage storage;
    private final Map<BlockKey, PortalRecord> portalsBySign = new HashMap<>();
    private final Map<ChunkKey, Set<BlockKey>> chunkIndex = new HashMap<>();
    private final Map<String, Set<BlockKey>> tagIndex = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    private final Set<UUID> playersWaitingToExitPortal = new HashSet<>();

    private BukkitTask tickTask;
    private BukkitTask ambienceTask;
    private int warmupTicks;
    private int cooldownTicks;
    private boolean particlesEnabled;
    private boolean soundsEnabled;
    private long currentTick;
    private boolean persistenceArmed;
    private Runnable portalUpdateHook = () -> {
    };

    public PortalManager(PortalheimPlugin plugin, PortalDetector detector, PortalStorage storage) {
        this.plugin = plugin;
        this.detector = detector;
        this.storage = storage;
    }

    public void enable() {
        persistenceArmed = false;
        reloadAll();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runTick, 1L, 1L);
        ambienceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runAmbience, 20L, 20L);
    }

    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (ambienceTask != null) {
            ambienceTask.cancel();
        }
        pendingTeleports.clear();
        playersWaitingToExitPortal.clear();
        if (persistenceArmed) {
            storage.save(portalsBySign.values());
        }
    }

    public void reloadAll() {
        plugin.reloadConfig();
        warmupTicks = Math.max(0, plugin.getConfig().getInt("warmup-ticks", 30));
        cooldownTicks = Math.max(0, plugin.getConfig().getInt("teleport-cooldown-ticks", 60));
        particlesEnabled = plugin.getConfig().getBoolean("particles-enabled", true);
        soundsEnabled = plugin.getConfig().getBoolean("sounds-enabled", true);
        loadPersistedPortals();
    }

    public List<PortalRecord> listPortals(@Nullable String normalizedTag) {
        return portalsBySign.values().stream()
            .filter(portal -> normalizedTag == null || portal.tag().equals(normalizedTag))
            .sorted(Comparator.comparing(PortalRecord::tag).thenComparing(portal -> portal.anchor().describe(plugin.getServer())))
            .toList();
    }

    public List<String> knownTags() {
        return tagIndex.keySet().stream()
            .filter(tag -> !tag.isBlank())
            .sorted()
            .toList();
    }

    public Optional<PortalRecord> inspect(Player player) {
        Optional<PortalRecord> currentPortal = findPortalContaining(player, false);
        if (currentPortal.isPresent()) {
            return currentPortal;
        }

        RayTraceResult result = player.rayTraceBlocks(8.0);
        if (result != null && result.getHitBlock() != null) {
            Optional<PortalRecord> targetPortal = findPortalAt(result.getHitBlock());
            if (targetPortal.isPresent()) {
                return targetPortal;
            }
        }

        return nearestPortal(player, 36.0);
    }

    public Optional<PortalRecord> linkedPortal(PortalRecord source) {
        if (source.tag().isBlank() || !source.active()) {
            return Optional.empty();
        }

        return portalsBySign.values().stream()
            .filter(portal -> !portal.sign().equals(source.sign()))
            .filter(portal -> portal.active() && portal.tag().equals(source.tag()))
            .findFirst();
    }

    public void refreshNear(Block origin) {
        BlockKey changedBlock = BlockKey.fromBlock(origin);
        Set<BlockKey> affectedExisting = portalsBySign.values().stream()
            .filter(portal -> portal.isRelatedBlock(changedBlock))
            .map(PortalRecord::sign)
            .collect(Collectors.toSet());

        Map<BlockKey, PortalRecord> detections = new HashMap<>();
        if (shouldSearchForNewFrames(origin) || !affectedExisting.isEmpty()) {
            for (Sign sign : findNearbySigns(origin)) {
                detector.detect(sign).ifPresent(portal -> detections.put(portal.sign(), portal));
            }
        }

        Set<String> affectedTags = new HashSet<>();
        boolean changed = false;

        for (BlockKey signKey : affectedExisting) {
            if (!detections.containsKey(signKey)) {
                changed |= removePortal(signKey, affectedTags);
            }
        }

        for (PortalRecord portal : detections.values()) {
            changed |= upsertPortal(portal, affectedTags);
        }

        changed |= recomputeTags(affectedTags);
        if (changed) {
            storage.save(portalsBySign.values());
            portalUpdateHook.run();
        }
    }

    public void revalidateChunk(Chunk chunk) {
        ChunkKey chunkKey = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Set<BlockKey> trackedPortals = new HashSet<>(chunkIndex.getOrDefault(chunkKey, Set.of()));
        if (trackedPortals.isEmpty()) {
            return;
        }

        Set<String> affectedTags = new HashSet<>();
        boolean changed = false;

        for (BlockKey signKey : trackedPortals) {
            PortalRecord existing = portalsBySign.get(signKey);
            if (existing == null) {
                continue;
            }

            Block signBlock = chunk.getWorld().getBlockAt(signKey.x(), signKey.y(), signKey.z());
            if (!(signBlock.getState() instanceof Sign sign)) {
                changed |= removePortal(signKey, affectedTags);
                continue;
            }

            Optional<PortalRecord> detected = detector.detect(sign);
            if (detected.isPresent()) {
                changed |= upsertPortal(detected.get(), affectedTags);
            } else {
                changed |= removePortal(signKey, affectedTags);
            }
        }

        changed |= recomputeTags(affectedTags);
        if (changed) {
            storage.save(portalsBySign.values());
            portalUpdateHook.run();
        }
    }

    public void clearPlayerState(UUID playerId) {
        pendingTeleports.remove(playerId);
        teleportCooldowns.remove(playerId);
        playersWaitingToExitPortal.remove(playerId);
    }

    public void setPortalUpdateHook(Runnable portalUpdateHook) {
        this.portalUpdateHook = portalUpdateHook;
    }

    public void armPersistence() {
        persistenceArmed = true;
    }

    private void loadPersistedPortals() {
        portalsBySign.clear();
        chunkIndex.clear();
        tagIndex.clear();
        pendingTeleports.clear();
        teleportCooldowns.clear();
        playersWaitingToExitPortal.clear();

        Map<BlockKey, PortalRecord> loaded = storage.load();
        for (PortalRecord storedPortal : loaded.values()) {
            PortalRecord validated = validateStoredPortal(storedPortal);
            if (validated != null) {
                addPortal(validated.withActive(false));
            }
        }

        recomputeTags(new HashSet<>(tagIndex.keySet()));
        portalUpdateHook.run();
        plugin.debug("Loaded " + portalsBySign.size() + " portal records.");
    }

    private @Nullable PortalRecord validateStoredPortal(PortalRecord storedPortal) {
        World world = storedPortal.sign().resolveWorld(plugin.getServer());
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return null;
        }

        world.getChunkAt(storedPortal.sign().x() >> 4, storedPortal.sign().z() >> 4).load();
        Block signBlock = world.getBlockAt(storedPortal.sign().x(), storedPortal.sign().y(), storedPortal.sign().z());
        if (!(signBlock.getState() instanceof Sign sign)) {
            return null;
        }

        return detector.detect(sign).orElse(null);
    }

    private void runTick() {
        currentTick++;
        teleportCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTick);

        Iterator<Map.Entry<UUID, PendingTeleport>> iterator = pendingTeleports.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTeleport> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            PortalRecord source = portalsBySign.get(entry.getValue().portalSign());
            if (source == null || !source.active() || !source.containsPlayer(player)) {
                player.sendActionBar(Component.empty());
                iterator.remove();
                continue;
            }

            long remainingTicks = entry.getValue().executeTick() - currentTick;
            if (remainingTicks <= 0L) {
                iterator.remove();
                teleport(player, source);
                continue;
            }

            player.sendActionBar(Component.text(String.format(Locale.US, "Portal attuning... %.1fs", remainingTicks / 20.0)));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<PortalRecord> currentPortal = findPortalContaining(player, false);
            if (playersWaitingToExitPortal.contains(player.getUniqueId())) {
                if (currentPortal.isPresent()) {
                    continue;
                }
                playersWaitingToExitPortal.remove(player.getUniqueId());
            }

            if (pendingTeleports.containsKey(player.getUniqueId())) {
                continue;
            }

            if (teleportCooldowns.getOrDefault(player.getUniqueId(), 0L) > currentTick) {
                continue;
            }

            if (currentPortal.isEmpty() || !currentPortal.get().active()) {
                continue;
            }

            if (warmupTicks <= 0) {
                teleport(player, currentPortal.get());
                continue;
            }

            pendingTeleports.put(player.getUniqueId(), new PendingTeleport(currentPortal.get().sign(), currentTick + warmupTicks));
            if (soundsEnabled) {
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4f, 1.7f);
            }
        }
    }

    private void runAmbience() {
        if (!particlesEnabled && !soundsEnabled) {
            return;
        }

        for (PortalRecord portal : portalsBySign.values()) {
            if (!portal.active()) {
                continue;
            }

            World world = portal.anchor().resolveWorld(plugin.getServer());
            if (world == null || !world.isChunkLoaded(portal.anchor().x() >> 4, portal.anchor().z() >> 4)) {
                continue;
            }

            if (particlesEnabled) {
                spawnParticles(world, portal);
            }

            if (soundsEnabled && currentTick % 80L == 0L) {
                Location center = portal.center(plugin.getServer());
                if (center.getWorld() != null && hasNearbyPlayer(center, 8.0)) {
                    center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.8f);
                }
            }
        }
    }

    private void spawnParticles(World world, PortalRecord portal) {
        Location center = portal.center(plugin.getServer());
        double xSpread = portal.orientation() == PortalOrientation.X ? Math.max(0.2, (portal.frameWidth() - 2) * 0.18) : 0.03;
        double zSpread = portal.orientation() == PortalOrientation.X ? 0.03 : Math.max(0.2, (portal.frameWidth() - 2) * 0.18);
        double maxY = portal.anchor().y() + portal.frameHeight() - 1.0;

        for (double y = portal.anchor().y() + 1.1; y <= maxY - 0.2; y += 0.7) {
            world.spawnParticle(Particle.END_ROD, center.getX(), y, center.getZ(), 1, xSpread, 0.05, zSpread, 0.0);
            world.spawnParticle(Particle.ENCHANT, center.getX(), y, center.getZ(), 6, xSpread, 0.2, zSpread, 0.0);
        }
    }

    private boolean hasNearbyPlayer(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }

        return false;
    }

    private void teleport(Player player, PortalRecord source) {
        Optional<PortalRecord> destinationOptional = linkedPortal(source);
        if (destinationOptional.isEmpty()) {
            player.sendActionBar(Component.text("Portal link unavailable."));
            return;
        }

        PortalRecord destination = destinationOptional.get();
        Location destinationCenter = destination.center(plugin.getServer());
        World destinationWorld = destinationCenter.getWorld();
        if (destinationWorld == null) {
            return;
        }

        destinationWorld.getChunkAt(destination.anchor().x() >> 4, destination.anchor().z() >> 4).load();
        destinationCenter.setYaw(destination.exitYaw());
        destinationCenter.setPitch(player.getLocation().getPitch());

        Location sourceCenter = source.center(plugin.getServer());
        if (soundsEnabled && sourceCenter.getWorld() != null) {
            sourceCenter.getWorld().playSound(sourceCenter, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.4f);
        }

        if (player.teleport(destinationCenter)) {
            player.setFallDistance(0.0f);
            player.sendActionBar(Component.empty());
            teleportCooldowns.put(player.getUniqueId(), currentTick + cooldownTicks);
            playersWaitingToExitPortal.add(player.getUniqueId());

            if (soundsEnabled) {
                destinationWorld.playSound(destinationCenter, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 0.8f, 1.15f);
                destinationWorld.playSound(destinationCenter, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.35f, 1.75f);
            }
        }
    }

    private Optional<PortalRecord> findPortalContaining(Player player, boolean activeOnly) {
        return portalsBySign.values().stream()
            .filter(portal -> !activeOnly || portal.active())
            .filter(portal -> portal.containsPlayer(player))
            .findFirst();
    }

    private Optional<PortalRecord> findPortalAt(Block block) {
        BlockKey key = BlockKey.fromBlock(block);
        return portalsBySign.values().stream()
            .filter(portal -> portal.sign().equals(key) || portal.containsFrameBlock(key))
            .findFirst();
    }

    private Optional<PortalRecord> nearestPortal(Player player, double radiusSquared) {
        return portalsBySign.values().stream()
            .filter(portal -> portal.worldId().equals(player.getWorld().getUID()))
            .filter(portal -> portal.center(plugin.getServer()).distanceSquared(player.getLocation()) <= radiusSquared)
            .min(Comparator.comparingDouble(portal -> portal.center(plugin.getServer()).distanceSquared(player.getLocation())));
    }

    private Set<Sign> findNearbySigns(Block origin) {
        Set<Sign> signs = new HashSet<>();
        World world = origin.getWorld();
        int minY = Math.max(world.getMinHeight(), origin.getY() - MAX_FRAME_RADIUS);
        int maxY = Math.min(world.getMaxHeight() - 1, origin.getY() + MAX_FRAME_RADIUS);

        for (int x = origin.getX() - MAX_FRAME_RADIUS; x <= origin.getX() + MAX_FRAME_RADIUS; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = origin.getZ() - MAX_FRAME_RADIUS; z <= origin.getZ() + MAX_FRAME_RADIUS; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getState() instanceof Sign sign) {
                        signs.add(sign);
                    }
                }
            }
        }

        return signs;
    }

    private boolean shouldSearchForNewFrames(Block origin) {
        return origin.getType() == org.bukkit.Material.OBSIDIAN
            || origin.getType() == org.bukkit.Material.CRYING_OBSIDIAN
            || origin.getState() instanceof Sign;
    }

    private void addPortal(PortalRecord portal) {
        portalsBySign.put(portal.sign(), portal);
        index(portal);
    }

    private void index(PortalRecord portal) {
        indexChunk(portal.anchor().chunkKey(), portal.sign());
        indexChunk(portal.sign().chunkKey(), portal.sign());
        tagIndex.computeIfAbsent(portal.tag(), unused -> new HashSet<>()).add(portal.sign());
    }

    private void unindex(PortalRecord portal) {
        unindexChunk(portal.anchor().chunkKey(), portal.sign());
        unindexChunk(portal.sign().chunkKey(), portal.sign());

        Set<BlockKey> taggedPortals = tagIndex.get(portal.tag());
        if (taggedPortals != null) {
            taggedPortals.remove(portal.sign());
            if (taggedPortals.isEmpty()) {
                tagIndex.remove(portal.tag());
            }
        }
    }

    private void indexChunk(ChunkKey chunkKey, BlockKey signKey) {
        chunkIndex.computeIfAbsent(chunkKey, unused -> new HashSet<>()).add(signKey);
    }

    private void unindexChunk(ChunkKey chunkKey, BlockKey signKey) {
        Set<BlockKey> signKeys = chunkIndex.get(chunkKey);
        if (signKeys == null) {
            return;
        }

        signKeys.remove(signKey);
        if (signKeys.isEmpty()) {
            chunkIndex.remove(chunkKey);
        }
    }

    private boolean removePortal(BlockKey signKey, Set<String> affectedTags) {
        PortalRecord removed = portalsBySign.remove(signKey);
        if (removed == null) {
            return false;
        }

        unindex(removed);
        affectedTags.add(removed.tag());
        return true;
    }

    private boolean upsertPortal(PortalRecord portal, Set<String> affectedTags) {
        PortalRecord normalized = portal.withActive(false);
        PortalRecord existing = portalsBySign.get(normalized.sign());
        if (existing != null
            && existing.anchor().equals(normalized.anchor())
            && existing.orientation() == normalized.orientation()
            && existing.tag().equals(normalized.tag())) {
            return false;
        }

        if (existing != null) {
            unindex(existing);
            affectedTags.add(existing.tag());
        }

        portalsBySign.put(normalized.sign(), normalized);
        index(normalized);
        affectedTags.add(normalized.tag());
        return true;
    }

    private boolean recomputeTags(Set<String> affectedTags) {
        boolean changed = false;
        for (String tag : affectedTags) {
            Set<BlockKey> signKeys = new HashSet<>(tagIndex.getOrDefault(tag, Set.of()));
            boolean shouldBeActive = !tag.isBlank() && signKeys.size() == 2;

            for (BlockKey signKey : signKeys) {
                PortalRecord portal = portalsBySign.get(signKey);
                if (portal == null || portal.active() == shouldBeActive) {
                    continue;
                }

                portalsBySign.put(signKey, portal.withActive(shouldBeActive));
                changed = true;
            }
        }
        return changed;
    }

    private record PendingTeleport(BlockKey portalSign, long executeTick) {
    }
}
