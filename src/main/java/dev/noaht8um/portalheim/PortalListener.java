package dev.noaht8um.portalheim;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public final class PortalListener implements Listener {
    private final PortalheimPlugin plugin;
    private final PortalManager portalManager;

    public PortalListener(PortalheimPlugin plugin, PortalManager portalManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        scheduleRefresh(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        scheduleRefresh(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        scheduleRefresh(event.getBlock());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        portalManager.revalidateChunk(event.getChunk());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        portalManager.clearPlayerState(event.getPlayer().getUniqueId());
    }

    private void scheduleRefresh(Block block) {
        Bukkit.getScheduler().runTask(plugin, () -> portalManager.refreshNear(block));
    }
}
