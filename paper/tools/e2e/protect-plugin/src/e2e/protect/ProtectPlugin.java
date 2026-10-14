package e2e.protect;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    private static boolean protectedBlock(Block b) {
        return b.getX() >= 100 && b.getX() <= 120;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (protectedBlock(e.getBlockPlaced())) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (protectedBlock(e.getBlock())) e.setCancelled(true);
    }
}
