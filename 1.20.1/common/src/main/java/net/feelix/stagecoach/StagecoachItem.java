package net.feelix.stagecoach;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StagecoachItem extends BlockItem {
    public StagecoachItem(StagecoachBlock block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Stagecoach.text("item.place").formatted(Formatting.GRAY));
        tooltip.add(Stagecoach.text("item.travel").formatted(Formatting.GRAY));
    }
}
