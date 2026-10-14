package net.feelix.stagecoach;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.api.JourneyOptions;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.net.ServerNet;
import net.feelix.stagecoach.permission.Permissions;
import net.feelix.stagecoach.travel.CoachNames;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.Journey;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.travel.SafeSpot;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.feelix.stagecoach.travel.StopExport;
import net.feelix.stagecoach.travel.TravelMath;
import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StagecoachCommands {
    private StagecoachCommands() {}

    private static final int PER_PAGE = 10;
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    private static final DynamicCommandExceptionType NO_STOP = new DynamicCommandExceptionType(s -> Stagecoach.text("command.no_stop", s));
    private static final DynamicCommandExceptionType AMBIGUOUS = new DynamicCommandExceptionType(s -> Stagecoach.text("command.ambiguous", s));
    private static final DynamicCommandExceptionType BAD_FACING = new DynamicCommandExceptionType(s -> Stagecoach.text("command.bad_facing", s));
    private static final DynamicCommandExceptionType BAD_WOOD = new DynamicCommandExceptionType(s -> Stagecoach.text("command.bad_wood", s));
    private static final DynamicCommandExceptionType BAD_NAME = new DynamicCommandExceptionType(n -> Stagecoach.text("error.bad_name", n));
    private static final DynamicCommandExceptionType REFUSED = new DynamicCommandExceptionType(t -> (Component) t);
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(Stagecoach.text("command.one_player"));

    private static final SuggestionProvider<CommandSourceStack> STOPS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (CoachRecord r : StagecoachState.get(c.getSource().getServer()).coaches()) names.add(StringArgumentType.escapeIfRequired(r.name));
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<CommandSourceStack> WOODS = (c, b) -> {
        List<String> woods = new ArrayList<>();
        for (StagecoachWood w : StagecoachWood.values()) woods.add(w.getSerializedName());
        return SharedSuggestionProvider.suggest(woods, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Stagecoach.MOD_ID)
                .requires(StagecoachCommands::isAdmin)
                .then(Commands.literal("reload").executes(StagecoachCommands::reload))
                .then(Commands.literal("stop")
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string())
                                .executes(c -> create(c, false, false, false, false))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> create(c, true, false, false, false))
                                        .then(Commands.argument("facing", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(FACINGS, b))
                                                .executes(c -> create(c, true, true, false, false))
                                                .then(Commands.argument("wood", StringArgumentType.word()).suggests(WOODS)
                                                        .executes(c -> create(c, true, true, true, false))
                                                        .then(Commands.literal("force").executes(c -> create(c, true, true, true, true))))))))
                        .then(Commands.literal("list")
                                .executes(c -> list(c, null, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> list(c, null, IntegerArgumentType.getInteger(c, "page"))))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(c -> list(c, DimensionArgument.getDimension(c, "dimension").dimension(), 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(c -> list(c, DimensionArgument.getDimension(c, "dimension").dimension(),
                                                        IntegerArgumentType.getInteger(c, "page"))))))
                        .then(Commands.literal("info").then(stopArgument().executes(StagecoachCommands::info)))
                        .then(Commands.literal("rename").then(stopArgument()
                                .then(Commands.argument("newName", StringArgumentType.greedyString()).executes(StagecoachCommands::rename))))
                        .then(Commands.literal("setowner").then(stopArgument()
                                .then(Commands.literal("none").executes(c -> setOwner(c, false)))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> setOwner(c, true)))))
                        .then(Commands.literal("flag").then(flag("public")).then(flag("hidden")).then(flag("locked")).then(flag("free")))
                        .then(Commands.literal("cost").then(stopArgument()
                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0, 1000)).executes(StagecoachCommands::cost))))
                        .then(Commands.literal("scene").then(stopArgument()
                                .then(Commands.literal("none").executes(c -> scene(c, null)))
                                .then(Commands.argument("scene", IdentifierArgument.id())
                                        .executes(c -> scene(c, IdentifierArgument.getId(c, "scene"))))))
                        .then(Commands.literal("remove").then(stopArgument()
                                .executes(c -> remove(c, false))
                                .then(Commands.literal("keepCoach").executes(c -> remove(c, true)))))
                        .then(Commands.literal("tp").then(stopArgument().executes(StagecoachCommands::teleport))))
                .then(discovery("discover", true))
                .then(discovery("forget", false))
                .then(Commands.literal("journey")
                        .then(Commands.literal("send").then(Commands.argument("targets", EntityArgument.players())
                                .then(stopArgument()
                                        .executes(c -> send(c, false, false))
                                        .then(Commands.literal("free")
                                                .executes(c -> send(c, true, false))
                                                .then(Commands.literal("instant").executes(c -> send(c, true, true))))
                                        .then(Commands.literal("instant").executes(c -> send(c, false, true))))))
                        .then(Commands.literal("cancel").then(Commands.argument("targets", EntityArgument.players())
                                .executes(StagecoachCommands::cancel)))
                        .then(Commands.literal("list").executes(StagecoachCommands::journeys)))
                .then(Commands.literal("export").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> StopExport.export(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("import").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> StopExport.importFrom(c.getSource(), StringArgumentType.getString(c, "name"), false))
                        .then(Commands.literal("replace")
                                .executes(c -> StopExport.importFrom(c.getSource(), StringArgumentType.getString(c, "name"), true))))));
    }

    /** Players by the admin.commands node; the console, command blocks and functions by permission level. */
    private static boolean isAdmin(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return Permissions.check(player, Permissions.Node.ADMIN_COMMANDS);
        return Permissions.hasLevel(source.permissions(), StagecoachConfig.get().adminCommandLevel);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> stopArgument() {
        return Commands.argument("stop", StringArgumentType.string()).suggests(STOPS);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flag(String name) {
        return Commands.literal(name).then(stopArgument().then(Commands.argument("value", BoolArgumentType.bool())
                .executes(c -> setFlag(c, name, BoolArgumentType.getBool(c, "value")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> discovery(String name, boolean discover) {
        return Commands.literal(name).then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.literal("*").executes(c -> discover(c, discover, true)))
                .then(stopArgument().executes(c -> discover(c, discover, false))));
    }

    /** By id ("12" or "#12") or by name, ignoring case; several matches is an error that lists them. */
    static CoachRecord resolve(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "stop");
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        List<CoachRecord> found = new ArrayList<>();
        String digits = raw.startsWith("#") ? raw.substring(1) : raw;
        if (!digits.isEmpty() && digits.length() < 19 && digits.chars().allMatch(Character::isDigit)) {
            CoachRecord byId = network.coach(Long.parseLong(digits));
            if (byId != null) found.add(byId);
        }
        if (!raw.startsWith("#")) for (CoachRecord r : network.named(raw)) if (!found.contains(r)) found.add(r);
        if (found.isEmpty()) throw NO_STOP.create(raw);
        if (found.size() > 1) {
            for (CoachRecord r : found) c.getSource().sendSuccess(() -> line(c.getSource().getServer(), r), false);
            throw AMBIGUOUS.create(raw);
        }
        return found.get(0);
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        List<String> warnings = Stagecoach.reloadConfig();
        MinecraftServer server = c.getSource().getServer();
        StructureInjections.apply(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerNet.serverSettings(p);
        if (warnings.isEmpty()) {
            c.getSource().sendSuccess(() -> Stagecoach.text("command.reloaded"), true);
        } else {
            c.getSource().sendSuccess(() -> Stagecoach.text("command.reloaded_with_problems", warnings.size()).withStyle(ChatFormatting.YELLOW), true);
            for (String w : warnings) c.getSource().sendSuccess(() -> Component.literal(" - " + w).withStyle(ChatFormatting.GRAY), false);
        }
        c.getSource().sendSuccess(() -> Stagecoach.text("command.reloaded_data").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> c, boolean hasPos, boolean hasFacing, boolean hasWood, boolean force)
            throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        StagecoachConfig cfg = StagecoachConfig.get();
        String name = CoachNames.clean(StringArgumentType.getString(c, "name"), cfg.maxNameLength);
        if (name == null) throw BAD_NAME.create(cfg.maxNameLength);
        BlockPos pos = hasPos ? BlockPosArgument.getLoadedBlockPos(c, "pos") : BlockPos.containing(source.getPosition());
        Direction facing = hasFacing ? facing(StringArgumentType.getString(c, "facing"))
                : source.getEntity() != null ? source.getEntity().getDirection() : Direction.NORTH;
        StagecoachWood wood = hasWood ? wood(StringArgumentType.getString(c, "wood")) : StagecoachWood.OAK;
        BlockState controller = StagecoachRegistry.BLOCKS.get(wood).defaultBlockState().setValue(StagecoachBlock.FACING, facing);
        if (!force) {
            StagecoachStructure.Refusal blocked = StagecoachStructure.checkClear(level, pos, facing);
            if (blocked != null) throw REFUSED.create(blocked.message());
        }
        Component refusal = StagecoachService.placementRefusal(level, null, pos, controller);
        if (refusal != null && !(force && cfg.blockedDimensions.contains(level.dimension().identifier().toString()))) {
            throw REFUSED.create(refusal);
        }
        CoachRecord record = createStop(level, pos, controller, name);
        source.sendSuccess(() -> Stagecoach.text("command.created", record.id, record.name, pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    /** Ownerless, never drops an item; shared with import. */
    public static CoachRecord createStop(ServerLevel level, BlockPos pos, BlockState controller, String name) {
        StagecoachStructure.placeForced(level, pos, controller);
        StagecoachState network = StagecoachState.get(level.getServer());
        CoachRecord record = network.register(pos, level.dimension(), controller.getValue(StagecoachBlock.FACING),
                ((StagecoachBlock) controller.getBlock()).wood(), null, CreatedBy.ADMIN, name);
        if (level.getBlockEntity(pos) instanceof StagecoachBlockEntity be) {
            be.setCoachId(record.id);
            be.setDropsNothing(true);
        }
        ServerNet.knownStopsChanged();
        return record;
    }

    private static int list(CommandContext<CommandSourceStack> c, @Nullable ResourceKey<Level> dimension, int page) {
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        Collection<CoachRecord> all = dimension == null ? network.coaches() : network.coaches(dimension);
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int shown = Math.min(page, pages);
        c.getSource().sendSuccess(() -> Stagecoach.text("command.list.header", all.size(), shown, pages).withStyle(ChatFormatting.GOLD), false);
        int i = 0;
        for (CoachRecord r : all) {
            if (i++ / PER_PAGE == shown - 1) c.getSource().sendSuccess(() -> line(c.getSource().getServer(), r), false);
        }
        if (pages > 1) {
            String base = "/" + Stagecoach.MOD_ID + " stop list " + (dimension == null ? "" : dimension.identifier() + " ");
            MutableComponent nav = Component.empty();
            if (shown > 1) nav.append(button(Stagecoach.text("command.list.previous"), base + (shown - 1))).append(" ");
            if (shown < pages) nav.append(button(Stagecoach.text("command.list.next"), base + (shown + 1)));
            c.getSource().sendSuccess(() -> nav, false);
        }
        return all.size();
    }

    private static Component line(MinecraftServer server, CoachRecord r) {
        MutableComponent flags = Component.empty();
        if (r.isPublic) flags.append(" ").append(Stagecoach.text("command.flag.public"));
        if (r.hidden) flags.append(" ").append(Stagecoach.text("command.flag.hidden"));
        if (r.locked) flags.append(" ").append(Stagecoach.text("command.flag.locked"));
        if (r.free) flags.append(" ").append(Stagecoach.text("command.flag.free"));
        Component text = Stagecoach.text("command.list.line", r.id, r.name, r.dimension.identifier().toString(), r.pos.getX(), r.pos.getY(),
                r.pos.getZ(), owner(server, r), flags.withStyle(ChatFormatting.AQUA));
        return button(text, "/" + Stagecoach.MOD_ID + " stop info " + r.id);
    }

    private static MutableComponent button(Component text, String command) {
        return text.copy().withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command).withStyle(ChatFormatting.GRAY))));
    }

    private static Component owner(MinecraftServer server, CoachRecord r) {
        if (r.owner == null) return Stagecoach.text("owner.server");
        return Component.literal(server.services().nameToIdCache().get(r.owner).map(NameAndId::name).orElse(r.owner.toString()));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        CommandSourceStack s = c.getSource();
        s.sendSuccess(() -> Stagecoach.text("command.info.title", r.id, r.name).withStyle(ChatFormatting.GOLD), false);
        s.sendSuccess(() -> Stagecoach.text("command.info.where", r.dimension.identifier().toString(), r.pos.getX(), r.pos.getY(),
                r.pos.getZ(), r.facing.getSerializedName()), false);
        s.sendSuccess(() -> Stagecoach.text("command.info.owner", owner(s.getServer(), r), r.createdBy.name().toLowerCase(Locale.ROOT),
                r.woodOrOak().getSerializedName()), false);
        s.sendSuccess(() -> Stagecoach.text("command.info.flags", yesNo(r.isPublic), yesNo(r.hidden), yesNo(r.locked), yesNo(r.free)), false);
        s.sendSuccess(() -> Stagecoach.text("command.info.cost", String.valueOf(r.costMultiplier),
                r.scene == null ? Stagecoach.text("command.none") : Component.literal(r.scene.toString())), false);
        if (r.structureKey != null) s.sendSuccess(() -> Stagecoach.text("command.info.structure", r.structureKey), false);
        return 1;
    }

    private static Component yesNo(boolean v) {
        return Stagecoach.text(v ? "command.yes" : "command.no");
    }

    private static int rename(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        int max = StagecoachConfig.get().maxNameLength;
        String name = CoachNames.clean(StringArgumentType.getString(c, "newName"), max);
        if (name == null) throw BAD_NAME.create(max);
        StagecoachState.get(c.getSource().getServer()).rename(r, name);
        ServerNet.knownStopsChanged();
        c.getSource().sendSuccess(() -> Stagecoach.text("command.renamed", r.id, name), true);
        return 1;
    }

    private static int setOwner(CommandContext<CommandSourceStack> c, boolean toPlayer) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        if (!toPlayer) {
            network.setOwner(r, null);
            c.getSource().sendSuccess(() -> Stagecoach.text("command.owner_none", r.id, r.name), true);
            return 1;
        }
        Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(c, "player");
        if (profiles.size() != 1) throw ONE_PLAYER.create();
        NameAndId profile = profiles.iterator().next();
        network.setOwner(r, profile.id());
        network.discover(profile.id(), r.id);
        c.getSource().sendSuccess(() -> Stagecoach.text("command.owner_set", r.id, r.name, profile.name()), true);
        return 1;
    }

    private static int setFlag(CommandContext<CommandSourceStack> c, String flag, boolean value) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        switch (flag) {
            case "public" -> r.isPublic = value;
            case "hidden" -> r.hidden = value;
            case "locked" -> r.locked = value;
            default -> r.free = value;
        }
        StagecoachState.get(c.getSource().getServer()).changed();
        ServerNet.knownStopsChanged();
        c.getSource().sendSuccess(() -> Stagecoach.text("command.flag_set", r.id, r.name, flag, value), true);
        return 1;
    }

    private static int cost(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        r.costMultiplier = DoubleArgumentType.getDouble(c, "multiplier");
        StagecoachState.get(c.getSource().getServer()).changed();
        c.getSource().sendSuccess(() -> Stagecoach.text("command.cost_set", r.id, r.name, String.valueOf(r.costMultiplier)), true);
        return 1;
    }

    private static int scene(CommandContext<CommandSourceStack> c, @Nullable Identifier scene) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        r.scene = scene;
        StagecoachState.get(c.getSource().getServer()).changed();
        c.getSource().sendSuccess(() -> Stagecoach.text("command.scene_set", r.id, r.name,
                scene == null ? Stagecoach.text("command.none") : Component.literal(scene.toString())), true);
        return 1;
    }

    /** Works on locked stops too; keepCoach leaves the blocks as an out-of-service decoration. */
    private static int remove(CommandContext<CommandSourceStack> c, boolean keepCoach) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        MinecraftServer server = c.getSource().getServer();
        ServerLevel level = server.getLevel(r.dimension);
        StagecoachState network = StagecoachState.get(server);
        if (level != null && level.getBlockEntity(r.pos) instanceof StagecoachBlockEntity be && be.getCoachId() == r.id) {
            if (keepCoach) be.makeDecoration();
            else StagecoachStructure.removeCoach(level, r.pos, level.getBlockState(r.pos));
        }
        network.remove(r.id);
        ServerNet.knownStopsChanged();
        c.getSource().sendSuccess(() -> Stagecoach.text(keepCoach ? "command.removed_kept" : "command.removed", r.id, r.name), true);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = c.getSource().getServer().getLevel(r.dimension);
        if (level == null) throw NO_STOP.create(r.name);
        Vec3 spot = SafeSpot.near(level, r.pos, r.facing);
        player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), r.facing.toYRot(), 0, true);
        c.getSource().sendSuccess(() -> Stagecoach.text("command.teleported", r.name), false);
        return 1;
    }

    private static int discover(CommandContext<CommandSourceStack> c, boolean discover, boolean every) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        List<CoachRecord> stops = every ? new ArrayList<>(network.coaches()) : List.of(resolve(c));
        int changed = 0;
        for (ServerPlayer player : targets) {
            for (CoachRecord r : stops) {
                if (discover ? StagecoachService.discoverByCommand(player, r) : network.forget(player.getUUID(), r.id)) changed++;
            }
            ServerNet.knownStops(player);
        }
        int n = changed;
        c.getSource().sendSuccess(() -> Stagecoach.text(discover ? "command.discovered" : "command.forgot", n, targets.size()), true);
        return n;
    }

    private static int send(CommandContext<CommandSourceStack> c, boolean free, boolean instant) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        CoachRecord dest = resolve(c);
        JourneyOptions options = new JourneyOptions(free, instant, true, true, true);
        int started = 0;
        for (ServerPlayer player : targets) {
            StagecoachService.Outcome outcome = StagecoachService.startJourney(player, dest, options);
            if (outcome.result().started()) {
                started++;
            } else {
                Component why = outcome.message() != null ? outcome.message() : Component.literal(outcome.result().name());
                c.getSource().sendSuccess(() -> Stagecoach.text("command.send_failed", player.getName(), why).withStyle(ChatFormatting.RED), false);
            }
        }
        int n = started;
        c.getSource().sendSuccess(() -> Stagecoach.text("command.sent", n, dest.name), true);
        return n;
    }

    private static int cancel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) if (JourneyManager.cancelByAdmin(player)) n++;
        int cancelled = n;
        c.getSource().sendSuccess(() -> Stagecoach.text("command.cancelled", cancelled), true);
        return n;
    }

    private static int journeys(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Collection<Journey> all = StagecoachState.get(server).journeys();
        c.getSource().sendSuccess(() -> Stagecoach.text("command.journeys.header", all.size()).withStyle(ChatFormatting.GOLD), false);
        long now = server.overworld().getGameTime();
        for (Journey j : all) {
            ServerPlayer p = server.getPlayerList().getPlayer(j.player);
            Component who = p != null ? p.getName() : Component.literal(j.player.toString());
            long left = Math.max(0, j.endTime() - now);
            c.getSource().sendSuccess(() -> Stagecoach.text("command.journeys.line", who, j.destName, TravelMath.clock(left)), false);
        }
        return all.size();
    }

    private static Direction facing(String s) throws CommandSyntaxException {
        Direction d = Direction.byName(s.toLowerCase(Locale.ROOT));
        if (d == null || d.getAxis().isVertical()) throw BAD_FACING.create(s);
        return d;
    }

    private static StagecoachWood wood(String s) throws CommandSyntaxException {
        StagecoachWood w = StagecoachWood.byId(s.toLowerCase(Locale.ROOT));
        if (w == null) throw BAD_WOOD.create(s);
        return w;
    }
}
