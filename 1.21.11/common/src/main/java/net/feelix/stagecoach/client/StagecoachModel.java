package net.feelix.stagecoach.client;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachWood;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.EnumMap;
import java.util.Map;

public class StagecoachModel<T extends GeoAnimatable> extends GeoModel<T> {
    /** Which wood's texture to draw; the renderers put it on the render state. */
    public static final DataTicket<StagecoachWood> WOOD = DataTicket.create("stagecoach_wood", StagecoachWood.class);

    // GeckoLib 5 reads models from assets/<ns>/geckolib/models/, named without folder or suffix
    private static final Identifier MODEL = Stagecoach.id("block/stagecoach");
    // no animation file ships with the model, and the block entity never asks for one
    private static final Identifier ANIMATIONS = Stagecoach.id("block/stagecoach");
    // textures/entity/: anything under textures/block/ gets stitched into the block atlas
    private static final Map<StagecoachWood, Identifier> TEXTURES = new EnumMap<>(StagecoachWood.class);

    static {
        for (StagecoachWood wood : StagecoachWood.values()) {
            TEXTURES.put(wood, Stagecoach.id("textures/entity/stagecoach/" + wood.getSerializedName() + ".png"));
        }
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURES.get(state.getOrDefaultGeckolibData(WOOD, StagecoachWood.OAK));
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return ANIMATIONS;
    }
}
