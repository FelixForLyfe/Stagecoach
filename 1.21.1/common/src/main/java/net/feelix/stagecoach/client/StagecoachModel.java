package net.feelix.stagecoach.client;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import java.util.EnumMap;
import java.util.Map;

public class StagecoachModel extends GeoModel<StagecoachBlockEntity> {
    private static final Identifier MODEL = Stagecoach.id("geo/block/stagecoach.geo.json");
    // no animation file ships with the model, and the block entity never asks for one
    private static final Identifier ANIMATIONS = Stagecoach.id("animations/block/stagecoach.animation.json");
    // textures/entity/: anything under textures/block/ gets stitched into the block atlas
    private static final Map<StagecoachWood, Identifier> TEXTURES = new EnumMap<>(StagecoachWood.class);

    static {
        for (StagecoachWood wood : StagecoachWood.values()) {
            TEXTURES.put(wood, Stagecoach.id("textures/entity/stagecoach/" + wood.asString() + ".png"));
        }
    }

    @Override
    public Identifier getModelResource(StagecoachBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(StagecoachBlockEntity animatable) {
        return animatable.getCachedState().getBlock() instanceof StagecoachBlock block
                ? TEXTURES.get(block.wood())
                : TEXTURES.get(StagecoachWood.OAK);
    }

    @Override
    public Identifier getAnimationResource(StagecoachBlockEntity animatable) {
        return ANIMATIONS;
    }
}
