package net.feelix.stagecoach.bukkit.listen;

import net.feelix.stagecoach.bukkit.item.CoachItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public final class ItemGuardListener implements Listener {
    private final CoachItems items;

    public ItemGuardListener(CoachItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (items.isCoach(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent e) {
        if (items.isCoach(e.getFuel())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (items.isCoach(s)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (e.getBlock().getState() instanceof org.bukkit.block.Crafter crafter) {
            for (ItemStack s : crafter.getInventory().getContents()) {
                if (items.isCoach(s)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }
}
