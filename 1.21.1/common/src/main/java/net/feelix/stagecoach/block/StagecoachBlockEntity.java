package net.feelix.stagecoach.block;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.travel.PendingCoaches;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StagecoachBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Keys a pack developer may set on a coach saved into a structure; read once when it becomes a stop. */
    public static final String TEMPLATE_PREFIX = Stagecoach.MOD_ID + ":template_";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** -1 until registered in StagecoachState. */
    private long coachId = -1;
    private boolean decoration;
    private boolean noDrop;
    private @Nullable NbtCompound template;

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
    public void setWorld(World world) {
        super.setWorld(world);
        if (!world.isClient) PendingCoaches.queue(world, pos);
    }

    public long getCoachId() {
        return coachId;
    }

    public void setCoachId(long id) {
        coachId = id;
        markDirty();
    }

    /** A coach taken out of service: never registers again, and right-clicking it does nothing. */
    public boolean isDecoration() {
        return decoration;
    }

    public void makeDecoration() {
        decoration = true;
        coachId = -1;
        markDirty();
    }

    public boolean dropsNothing() {
        return noDrop;
    }

    public void setDropsNothing(boolean value) {
        noDrop = value;
        markDirty();
    }

    public @Nullable NbtCompound template() {
        return template;
    }

    public void clearTemplate() {
        template = null;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putLong("CoachId", coachId);
        if (decoration) nbt.putBoolean("Decoration", true);
        if (noDrop) nbt.putBoolean("NoDrop", true);
        if (template != null) for (String key : template.getKeys()) nbt.put(key, template.get(key).copy());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        coachId = nbt.contains("CoachId") ? nbt.getLong("CoachId") : -1;
        decoration = nbt.getBoolean("Decoration");
        noDrop = nbt.getBoolean("NoDrop");
        template = null;
        for (String key : nbt.getKeys()) {
            if (!key.startsWith(TEMPLATE_PREFIX)) continue;
            if (template == null) template = new NbtCompound();
            template.put(key, nbt.get(key).copy());
        }
    }
}
