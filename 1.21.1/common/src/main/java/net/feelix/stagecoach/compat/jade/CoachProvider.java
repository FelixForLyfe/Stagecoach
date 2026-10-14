package net.feelix.stagecoach.compat.jade;

import com.mojang.authlib.GameProfile;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.UUID;

enum CoachProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID = Stagecoach.id("coach");

    @Override
    public void appendServerData(NbtCompound data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof StagecoachBlockEntity be) || !(accessor.getLevel() instanceof ServerWorld world)) return;
        StagecoachState state = StagecoachState.get(world.getServer());
        CoachRecord coach = state.coach(be.getCoachId());
        if (coach == null) return;
        PlayerEntity viewer = accessor.getPlayer();
        data.putString("Name", coach.name);
        if (coach.owner != null) {
            data.putString("Owner", ownerName(world.getServer(), coach.owner));
            data.putBoolean("Mine", coach.isOwner(viewer.getUuid()));
        } else {
            data.putBoolean("Server", true);
        }
        data.putBoolean("Known", viewer instanceof ServerPlayerEntity sp && StagecoachService.knows(state, sp, coach));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        NbtCompound data = accessor.getServerData();
        if (!data.contains("Name")) return;
        tooltip.add(Text.literal(data.getString("Name")).formatted(Formatting.GOLD));
        if (data.contains("Owner")) {
            tooltip.add(data.getBoolean("Mine")
                    ? Stagecoach.text("jade.yours").formatted(Formatting.GRAY)
                    : Stagecoach.text("jade.owner", data.getString("Owner")).formatted(Formatting.GRAY));
        } else if (data.getBoolean("Server")) {
            tooltip.add(Stagecoach.text("jade.owner", Stagecoach.text("owner.server")).formatted(Formatting.GRAY));
        }
        if (!data.getBoolean("Known")) tooltip.add(Stagecoach.text("jade.undiscovered").formatted(Formatting.YELLOW));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    private static String ownerName(MinecraftServer server, UUID owner) {
        if (server.getUserCache() == null) return "?";
        return server.getUserCache().getByUuid(owner).map(GameProfile::getName).orElse("?");
    }
}
