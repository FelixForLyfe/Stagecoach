package net.feelix.stagecoach.forge;

import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

final class ForgeStagecoachItem extends StagecoachItem {
    ForgeStagecoachItem(StagecoachBlock block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BuiltinModelItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ForgeCoachItemRenderer((StagecoachBlock) getBlock());
                return renderer;
            }
        });
    }
}
