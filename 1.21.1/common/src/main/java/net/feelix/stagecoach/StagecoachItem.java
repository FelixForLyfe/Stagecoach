package net.feelix.stagecoach;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class StagecoachItem extends BlockItem {
    public StagecoachItem(StagecoachBlock block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Stagecoach.text("item.place").formatted(Formatting.GRAY));
        tooltip.add(Stagecoach.text("item.travel").formatted(Formatting.GRAY));
    }
}
