package net.feelix.stagecoach.compat.jade;

import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class StagecoachJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CoachProvider.INSTANCE, StagecoachBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CoachProvider.INSTANCE, StagecoachBlock.class);
        // a part has no identity of its own, so Jade shows the controller instead
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (accessor instanceof BlockAccessor block && block.getBlockState().getBlock() instanceof StagecoachPartBlock) {
                Level level = block.getLevel();
                BlockPos pos = block.getPosition();
                BlockState part = block.getBlockState();
                if (StagecoachStructure.hasController(level, pos, part)) {
                    BlockPos controller = StagecoachStructure.controllerPos(pos, part);
                    return registration.blockAccessor().from(block)
                            .blockState(level.getBlockState(controller))
                            .blockEntity(level.getBlockEntity(controller))
                            .hit(new BlockHitResult(block.getHitResult().getLocation(), block.getSide(), controller, false))
                            .build();
                }
            }
            return accessor;
        });
    }
}
