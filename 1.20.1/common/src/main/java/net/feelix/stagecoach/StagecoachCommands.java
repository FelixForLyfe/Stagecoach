package net.feelix.stagecoach;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class StagecoachCommands {
    private StagecoachCommands() {}

    private static final int PER_PAGE = 10;
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    private static final DynamicCommandExceptionType NO_STOP = new DynamicCommandExceptionType(s -> Stagecoach.text("command.no_stop", s));
    private static final DynamicCommandExceptionType AMBIGUOUS = new DynamicCommandExceptionType(s -> Stagecoach.text("command.ambiguous", s));
    private static final DynamicCommandExceptionType BAD_FACING = new DynamicCommandExceptionType(s -> Stagecoach.text("command.bad_facing", s));
    private static final DynamicCommandExceptionType BAD_WOOD = new DynamicCommandExceptionType(s -> Stagecoach.text("command.bad_wood", s));
    private static final DynamicCommandExceptionType BAD_NAME = new DynamicCommandExceptionType(n -> Stagecoach.text("error.bad_name", n));
    private static final DynamicCommandExceptionType REFUSED = new DynamicCommandExceptionType(t -> (Text) t);
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(Stagecoach.text("command.one_player"));

    private static final SuggestionProvider<ServerCommandSource> STOPS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (CoachRecord r : StagecoachState.get(c.getSource().getServer()).coaches()) names.add(StringArgumentType.escapeIfRequired(r.name));
        return CommandSource.suggestMatching(names, b);
    };
    private static final SuggestionProvider<ServerCommandSource> WOODS = (c, b) -> {
        List<String> woods = new ArrayList<>();
        for (StagecoachWood w : StagecoachWood.values()) woods.add(w.asString());
        return CommandSource.suggestMatching(woods, b);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal(Stagecoach.MOD_ID)
                .requires(StagecoachCommands::isAdmin)
                .then(CommandManager.literal("reload").executes(StagecoachCommands::reload))
                .then(CommandManager.literal("stop")
                        .then(CommandManager.literal("create").then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(c -> create(c, false, false, false, false))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(c -> create(c, true, false, false, false))
                                        .then(CommandManager.argument("facing", StringArgumentType.word())
                                                .suggests((c, b) -> CommandSource.suggestMatching(FACINGS, b))
                                                .executes(c -> create(c, true, true, false, false))
                                                .then(CommandManager.argument("wood", StringArgumentType.word()).suggests(WOODS)
                                                        .executes(c -> create(c, true, true, true, false))
                                                        .then(CommandManager.literal("force").executes(c -> create(c, true, true, true, true))))))))
                        .then(CommandManager.literal("list")
                                .executes(c -> list(c, null, 1))
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> list(c, null, IntegerArgumentType.getInteger(c, "page"))))
                                .then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
                                        .executes(c -> list(c, DimensionArgumentType.getDimensionArgument(c, "dimension").getRegistryKey(), 1))
                                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                                .executes(c -> list(c, DimensionArgumentType.getDimensionArgument(c, "dimension").getRegistryKey(),
                                                        IntegerArgumentType.getInteger(c, "page"))))))
                        .then(CommandManager.literal("info").then(stopArgument().executes(StagecoachCommands::info)))
                        .then(CommandManager.literal("rename").then(stopArgument()
                                .then(CommandManager.argument("newName", StringArgumentType.greedyString()).executes(StagecoachCommands::rename))))
                        .then(CommandManager.literal("setowner").then(stopArgument()
                                .then(CommandManager.literal("none").executes(c -> setOwner(c, false)))
                                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile()).executes(c -> setOwner(c, true)))))
                        .then(CommandManager.literal("flag").then(flag("public")).then(flag("hidden")).then(flag("locked")).then(flag("free")))
                        .then(CommandManager.literal("cost").then(stopArgument()
                                .then(CommandManager.argument("multiplier", DoubleArgumentType.doubleArg(0, 1000)).executes(StagecoachCommands::cost))))
                        .then(CommandManager.literal("scene").then(stopArgument()
                                .then(CommandManager.literal("none").executes(c -> scene(c, null)))
                                .then(CommandManager.argument("scene", IdentifierArgumentType.identifier())
                                        .executes(c -> scene(c, IdentifierArgumentType.getIdentifier(c, "scene"))))))
                        .then(CommandManager.literal("remove").then(stopArgument()
                                .executes(c -> remove(c, false))
                                .then(CommandManager.literal("keepCoach").executes(c -> remove(c, true)))))
                        .then(CommandManager.literal("tp").then(stopArgument().executes(StagecoachCommands::teleport))))
                .then(discovery("discover", true))
                .then(discovery("forget", false))
                .then(CommandManager.literal("journey")
                        .then(CommandManager.literal("send").then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(stopArgument()
                                        .executes(c -> send(c, false, false))
                                        .then(CommandManager.literal("free")
                                                .executes(c -> send(c, true, false))
                                                .then(CommandManager.literal("instant").executes(c -> send(c, true, true))))
                                        .then(CommandManager.literal("instant").executes(c -> send(c, false, true))))))
                        .then(CommandManager.literal("cancel").then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(StagecoachCommands::cancel)))
                        .then(CommandManager.literal("list").executes(StagecoachCommands::journeys)))
                .then(CommandManager.literal("export").then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> StopExport.export(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(CommandManager.literal("import").then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> StopExport.importFrom(c.getSource(), StringArgumentType.getString(c, "name"), false))
                        .then(CommandManager.literal("replace")
                                .executes(c -> StopExport.importFrom(c.getSource(), StringArgumentType.getString(c, "name"), true))))));
    }

    /** Players by the admin.commands node; the console, command blocks and functions by permission level. */
    private static boolean isAdmin(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) return Permissions.check(player, Permissions.Node.ADMIN_COMMANDS);
        return source.hasPermissionLevel(StagecoachConfig.get().adminCommandLevel);
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> stopArgument() {
        return CommandManager.argument("stop", StringArgumentType.string()).suggests(STOPS);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> flag(String name) {
        return CommandManager.literal(name).then(stopArgument().then(CommandManager.argument("value", BoolArgumentType.bool())
                .executes(c -> setFlag(c, name, BoolArgumentType.getBool(c, "value")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> discovery(String name, boolean discover) {
        return CommandManager.literal(name).then(CommandManager.argument("targets", EntityArgumentType.players())
                .then(CommandManager.literal("*").executes(c -> discover(c, discover, true)))
                .then(stopArgument().executes(c -> discover(c, discover, false))));
    }

    /** By id ("12" or "#12") or by name, ignoring case; several matches is an error that lists them. */
    static CoachRecord resolve(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
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
            for (CoachRecord r : found) c.getSource().sendFeedback(() -> line(c.getSource().getServer(), r), false);
            throw AMBIGUOUS.create(raw);
        }
        return found.get(0);
    }

    private static int reload(CommandContext<ServerCommandSource> c) {
        List<String> warnings = Stagecoach.reloadConfig();
        MinecraftServer server = c.getSource().getServer();
        StructureInjections.apply(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ServerNet.serverSettings(p);
        if (warnings.isEmpty()) {
            c.getSource().sendFeedback(() -> Stagecoach.text("command.reloaded"), true);
        } else {
            c.getSource().sendFeedback(() -> Stagecoach.text("command.reloaded_with_problems", warnings.size()).formatted(Formatting.YELLOW), true);
            for (String w : warnings) c.getSource().sendFeedback(() -> Text.literal(" - " + w).formatted(Formatting.GRAY), false);
        }
        c.getSource().sendFeedback(() -> Stagecoach.text("command.reloaded_data").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int create(CommandContext<ServerCommandSource> c, boolean hasPos, boolean hasFacing, boolean hasWood, boolean force)
            throws CommandSyntaxException {
        ServerCommandSource source = c.getSource();
        ServerWorld world = source.getWorld();
        StagecoachConfig cfg = StagecoachConfig.get();
        String name = CoachNames.clean(StringArgumentType.getString(c, "name"), cfg.maxNameLength);
        if (name == null) throw BAD_NAME.create(cfg.maxNameLength);
        BlockPos pos = hasPos ? BlockPosArgumentType.getLoadedBlockPos(c, "pos") : BlockPos.ofFloored(source.getPosition());
        Direction facing = hasFacing ? facing(StringArgumentType.getString(c, "facing"))
                : source.getEntity() != null ? source.getEntity().getHorizontalFacing() : Direction.NORTH;
        StagecoachWood wood = hasWood ? wood(StringArgumentType.getString(c, "wood")) : StagecoachWood.OAK;
        BlockState controller = StagecoachRegistry.BLOCKS.get(wood).getDefaultState().with(StagecoachBlock.FACING, facing);
        if (!force) {
            StagecoachStructure.Refusal blocked = StagecoachStructure.checkClear(world, pos, facing);
            if (blocked != null) throw REFUSED.create(blocked.message());
        }
        Text refusal = StagecoachService.placementRefusal(world, null, pos, controller);
        if (refusal != null && !(force && cfg.blockedDimensions.contains(world.getRegistryKey().getValue().toString()))) {
            throw REFUSED.create(refusal);
        }
        CoachRecord record = createStop(world, pos, controller, name);
        source.sendFeedback(() -> Stagecoach.text("command.created", record.id, record.name, pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    /** Ownerless, never drops an item; shared with import. */
    public static CoachRecord createStop(ServerWorld world, BlockPos pos, BlockState controller, String name) {
        StagecoachStructure.placeForced(world, pos, controller);
        StagecoachState network = StagecoachState.get(world.getServer());
        CoachRecord record = network.register(pos, world.getRegistryKey(), controller.get(StagecoachBlock.FACING),
                ((StagecoachBlock) controller.getBlock()).wood(), null, CreatedBy.ADMIN, name);
        if (world.getBlockEntity(pos) instanceof StagecoachBlockEntity be) {
            be.setCoachId(record.id);
            be.setDropsNothing(true);
        }
        ServerNet.knownStopsChanged();
        return record;
    }

    private static int list(CommandContext<ServerCommandSource> c, @Nullable RegistryKey<World> dimension, int page) {
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        Collection<CoachRecord> all = dimension == null ? network.coaches() : network.coaches(dimension);
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int shown = Math.min(page, pages);
        c.getSource().sendFeedback(() -> Stagecoach.text("command.list.header", all.size(), shown, pages).formatted(Formatting.GOLD), false);
        int i = 0;
        for (CoachRecord r : all) {
            if (i++ / PER_PAGE == shown - 1) c.getSource().sendFeedback(() -> line(c.getSource().getServer(), r), false);
        }
        if (pages > 1) {
            String base = "/" + Stagecoach.MOD_ID + " stop list " + (dimension == null ? "" : dimension.getValue() + " ");
            MutableText nav = Text.empty();
            if (shown > 1) nav.append(button(Stagecoach.text("command.list.previous"), base + (shown - 1))).append(" ");
            if (shown < pages) nav.append(button(Stagecoach.text("command.list.next"), base + (shown + 1)));
            c.getSource().sendFeedback(() -> nav, false);
        }
        return all.size();
    }

    private static Text line(MinecraftServer server, CoachRecord r) {
        MutableText flags = Text.empty();
        if (r.isPublic) flags.append(" ").append(Stagecoach.text("command.flag.public"));
        if (r.hidden) flags.append(" ").append(Stagecoach.text("command.flag.hidden"));
        if (r.locked) flags.append(" ").append(Stagecoach.text("command.flag.locked"));
        if (r.free) flags.append(" ").append(Stagecoach.text("command.flag.free"));
        Text text = Stagecoach.text("command.list.line", r.id, r.name, r.dimension.getValue().toString(), r.pos.getX(), r.pos.getY(),
                r.pos.getZ(), owner(server, r), flags.formatted(Formatting.AQUA));
        return button(text, "/" + Stagecoach.MOD_ID + " stop info " + r.id);
    }

    private static MutableText button(Text text, String command) {
        return text.copy().styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command).formatted(Formatting.GRAY))));
    }

    private static Text owner(MinecraftServer server, CoachRecord r) {
        if (r.owner == null) return Stagecoach.text("owner.server");
        if (server.getUserCache() == null) return Text.literal(r.owner.toString());
        return Text.literal(server.getUserCache().getByUuid(r.owner).map(GameProfile::getName).orElse(r.owner.toString()));
    }

    private static int info(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        ServerCommandSource s = c.getSource();
        s.sendFeedback(() -> Stagecoach.text("command.info.title", r.id, r.name).formatted(Formatting.GOLD), false);
        s.sendFeedback(() -> Stagecoach.text("command.info.where", r.dimension.getValue().toString(), r.pos.getX(), r.pos.getY(),
                r.pos.getZ(), r.facing.asString()), false);
        s.sendFeedback(() -> Stagecoach.text("command.info.owner", owner(s.getServer(), r), r.createdBy.name().toLowerCase(Locale.ROOT),
                r.woodOrOak().asString()), false);
        s.sendFeedback(() -> Stagecoach.text("command.info.flags", yesNo(r.isPublic), yesNo(r.hidden), yesNo(r.locked), yesNo(r.free)), false);
        s.sendFeedback(() -> Stagecoach.text("command.info.cost", String.valueOf(r.costMultiplier),
                r.scene == null ? Stagecoach.text("command.none") : Text.literal(r.scene.toString())), false);
        if (r.structureKey != null) s.sendFeedback(() -> Stagecoach.text("command.info.structure", r.structureKey), false);
        return 1;
    }

    private static Text yesNo(boolean v) {
        return Stagecoach.text(v ? "command.yes" : "command.no");
    }

    private static int rename(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        int max = StagecoachConfig.get().maxNameLength;
        String name = CoachNames.clean(StringArgumentType.getString(c, "newName"), max);
        if (name == null) throw BAD_NAME.create(max);
        StagecoachState.get(c.getSource().getServer()).rename(r, name);
        ServerNet.knownStopsChanged();
        c.getSource().sendFeedback(() -> Stagecoach.text("command.renamed", r.id, name), true);
        return 1;
    }

    private static int setOwner(CommandContext<ServerCommandSource> c, boolean toPlayer) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        if (!toPlayer) {
            network.setOwner(r, null);
            c.getSource().sendFeedback(() -> Stagecoach.text("command.owner_none", r.id, r.name), true);
            return 1;
        }
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(c, "player");
        if (profiles.size() != 1) throw ONE_PLAYER.create();
        GameProfile profile = profiles.iterator().next();
        network.setOwner(r, profile.getId());
        network.discover(profile.getId(), r.id);
        c.getSource().sendFeedback(() -> Stagecoach.text("command.owner_set", r.id, r.name, profile.getName()), true);
        return 1;
    }

    private static int setFlag(CommandContext<ServerCommandSource> c, String flag, boolean value) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        switch (flag) {
            case "public" -> r.isPublic = value;
            case "hidden" -> r.hidden = value;
            case "locked" -> r.locked = value;
            default -> r.free = value;
        }
        StagecoachState.get(c.getSource().getServer()).changed();
        ServerNet.knownStopsChanged();
        c.getSource().sendFeedback(() -> Stagecoach.text("command.flag_set", r.id, r.name, flag, value), true);
        return 1;
    }

    private static int cost(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        r.costMultiplier = DoubleArgumentType.getDouble(c, "multiplier");
        StagecoachState.get(c.getSource().getServer()).changed();
        c.getSource().sendFeedback(() -> Stagecoach.text("command.cost_set", r.id, r.name, String.valueOf(r.costMultiplier)), true);
        return 1;
    }

    private static int scene(CommandContext<ServerCommandSource> c, @Nullable Identifier scene) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        r.scene = scene;
        StagecoachState.get(c.getSource().getServer()).changed();
        c.getSource().sendFeedback(() -> Stagecoach.text("command.scene_set", r.id, r.name,
                scene == null ? Stagecoach.text("command.none") : Text.literal(scene.toString())), true);
        return 1;
    }

    /** Works on locked stops too; keepCoach leaves the blocks as an out-of-service decoration. */
    private static int remove(CommandContext<ServerCommandSource> c, boolean keepCoach) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        MinecraftServer server = c.getSource().getServer();
        ServerWorld world = server.getWorld(r.dimension);
        StagecoachState network = StagecoachState.get(server);
        if (world != null && world.getBlockEntity(r.pos) instanceof StagecoachBlockEntity be && be.getCoachId() == r.id) {
            if (keepCoach) be.makeDecoration();
            else StagecoachStructure.removeCoach(world, r.pos, world.getBlockState(r.pos));
        }
        network.remove(r.id);
        ServerNet.knownStopsChanged();
        c.getSource().sendFeedback(() -> Stagecoach.text(keepCoach ? "command.removed_kept" : "command.removed", r.id, r.name), true);
        return 1;
    }

    private static int teleport(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        CoachRecord r = resolve(c);
        ServerPlayerEntity player = c.getSource().getPlayerOrThrow();
        ServerWorld world = c.getSource().getServer().getWorld(r.dimension);
        if (world == null) throw NO_STOP.create(r.name);
        Vec3d spot = SafeSpot.near(world, r.pos, r.facing);
        player.teleport(world, spot.x, spot.y, spot.z, r.facing.asRotation(), 0);
        c.getSource().sendFeedback(() -> Stagecoach.text("command.teleported", r.name), false);
        return 1;
    }

    private static int discover(CommandContext<ServerCommandSource> c, boolean discover, boolean every) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "targets");
        StagecoachState network = StagecoachState.get(c.getSource().getServer());
        List<CoachRecord> stops = every ? new ArrayList<>(network.coaches()) : List.of(resolve(c));
        int changed = 0;
        for (ServerPlayerEntity player : targets) {
            for (CoachRecord r : stops) {
                if (discover ? StagecoachService.discoverByCommand(player, r) : network.forget(player.getUuid(), r.id)) changed++;
            }
            ServerNet.knownStops(player);
        }
        int n = changed;
        c.getSource().sendFeedback(() -> Stagecoach.text(discover ? "command.discovered" : "command.forgot", n, targets.size()), true);
        return n;
    }

    private static int send(CommandContext<ServerCommandSource> c, boolean free, boolean instant) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "targets");
        CoachRecord dest = resolve(c);
        JourneyOptions options = new JourneyOptions(free, instant, true, true, true);
        int started = 0;
        for (ServerPlayerEntity player : targets) {
            StagecoachService.Outcome outcome = StagecoachService.startJourney(player, dest, options);
            if (outcome.result().started()) {
                started++;
            } else {
                Text why = outcome.message() != null ? outcome.message() : Text.literal(outcome.result().name());
                c.getSource().sendFeedback(() -> Stagecoach.text("command.send_failed", player.getName(), why).formatted(Formatting.RED), false);
            }
        }
        int n = started;
        c.getSource().sendFeedback(() -> Stagecoach.text("command.sent", n, dest.name), true);
        return n;
    }

    private static int cancel(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        int n = 0;
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(c, "targets")) if (JourneyManager.cancelByAdmin(player)) n++;
        int cancelled = n;
        c.getSource().sendFeedback(() -> Stagecoach.text("command.cancelled", cancelled), true);
        return n;
    }

    private static int journeys(CommandContext<ServerCommandSource> c) {
        MinecraftServer server = c.getSource().getServer();
        Collection<Journey> all = StagecoachState.get(server).journeys();
        c.getSource().sendFeedback(() -> Stagecoach.text("command.journeys.header", all.size()).formatted(Formatting.GOLD), false);
        long now = server.getOverworld().getTime();
        for (Journey j : all) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(j.player);
            Text who = p != null ? p.getName() : Text.literal(j.player.toString());
            long left = Math.max(0, j.endTime() - now);
            c.getSource().sendFeedback(() -> Stagecoach.text("command.journeys.line", who, j.destName, TravelMath.clock(left)), false);
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
