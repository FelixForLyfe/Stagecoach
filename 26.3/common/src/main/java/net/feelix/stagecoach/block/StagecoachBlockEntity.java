package net.feelix.stagecoach.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.travel.PendingCoaches;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StagecoachBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Keys a pack developer may set on a coach saved into a structure; read once when it becomes a stop. */
    public static final String TEMPLATE_PREFIX = Stagecoach.MOD_ID + ":template_";
    private static final List<String> TEMPLATE_KEYS = List.of(PendingCoaches.TEMPLATE_NAME, PendingCoaches.TEMPLATE_FLAGS,
            PendingCoaches.TEMPLATE_COST, PendingCoaches.TEMPLATE_SCENE, PendingCoaches.TEMPLATE_THEME, PendingCoaches.TEMPLATE_WOOD);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** -1 until registered in StagecoachState. */
    private long coachId = -1;
    private boolean decoration;
    private boolean noDrop;
    private @Nullable CompoundTag template;

    public StagecoachBlockEntity(BlockPos pos, BlockState state) {
        super(StagecoachRegistry.BLOCK_ENTITY, pos, state);
    }

    /** The model has no animation file, so there is nothing to register. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Chunk loads, structures and commands all end here; the check runs on the next server tick. */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) PendingCoaches.queue(level, worldPosition);
    }

    public long getCoachId() {
        return coachId;
    }

    public void setCoachId(long id) {
        coachId = id;
        setChanged();
    }

    /** A coach taken out of service: never registers again, and right-clicking it does nothing. */
    public boolean isDecoration() {
        return decoration;
    }

    public void makeDecoration() {
        decoration = true;
        coachId = -1;
        setChanged();
    }

    public boolean dropsNothing() {
        return noDrop;
    }

    public void setDropsNothing(boolean value) {
        noDrop = value;
        setChanged();
    }

    public @Nullable CompoundTag template() {
        return template;
    }

    public void clearTemplate() {
        template = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("CoachId", coachId);
        if (decoration) output.putBoolean("Decoration", true);
        if (noDrop) output.putBoolean("NoDrop", true);
        if (template != null) for (String key : template.keySet()) output.store(key, ExtraCodecs.NBT, template.get(key).copy());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        coachId = input.getLongOr("CoachId", -1);
        decoration = input.getBooleanOr("Decoration", false);
        noDrop = input.getBooleanOr("NoDrop", false);
        template = null;
        for (String key : TEMPLATE_KEYS) {
            Tag value = input.read(key, ExtraCodecs.NBT).orElse(null);
            if (value == null) continue;
            if (template == null) template = new CompoundTag();
            template.put(key, value);
        }
    }

    /** However the controller goes, its parts go silently with it and the coach leaves the network. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverWorld && !StagecoachStructure.isRebuilding()) {
            StagecoachStructure.removeParts(serverWorld, pos, state);
            StagecoachService.onControllerRemoved(serverWorld, pos, coachId);
        }
    }
}
