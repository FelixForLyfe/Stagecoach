package net.feelix.stagecoach.compat.jade;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
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
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof StagecoachBlockEntity be) || !(accessor.getLevel() instanceof ServerLevel level)) return;
        StagecoachState state = StagecoachState.get(level.getServer());
        CoachRecord coach = state.coach(be.getCoachId());
        if (coach == null) return;
        Player viewer = accessor.getPlayer();
        data.putString("Name", coach.name);
        if (coach.owner != null) {
            data.putString("Owner", ownerName(level.getServer(), coach.owner));
            data.putBoolean("Mine", coach.isOwner(viewer.getUUID()));
        } else {
            data.putBoolean("Server", true);
        }
        data.putBoolean("Known", viewer instanceof ServerPlayer sp && StagecoachService.knows(state, sp, coach));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("Name")) return;
        tooltip.add(Component.literal(data.getStringOr("Name", "")).withStyle(ChatFormatting.GOLD));
        if (data.contains("Owner")) {
            tooltip.add(data.getBooleanOr("Mine", false)
                    ? Stagecoach.text("jade.yours").withStyle(ChatFormatting.GRAY)
                    : Stagecoach.text("jade.owner", data.getStringOr("Owner", "?")).withStyle(ChatFormatting.GRAY));
        } else if (data.getBooleanOr("Server", false)) {
            tooltip.add(Stagecoach.text("jade.owner", Stagecoach.text("owner.server")).withStyle(ChatFormatting.GRAY));
        }
        if (!data.getBooleanOr("Known", false)) tooltip.add(Stagecoach.text("jade.undiscovered").withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    private static String ownerName(MinecraftServer server, UUID owner) {
        return server.services().nameToIdCache().get(owner).map(NameAndId::name).orElse("?");
    }
}
