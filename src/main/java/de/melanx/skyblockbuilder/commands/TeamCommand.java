package de.melanx.skyblockbuilder.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.melanx.skyblockbuilder.util.NameGenerator;
import de.melanx.skyblockbuilder.util.Team;
import de.melanx.skyblockbuilder.util.TranslationUtil;
import de.melanx.skyblockbuilder.world.IslandPos;
import de.melanx.skyblockbuilder.world.data.SkyblockSavedData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;

import java.util.*;
import java.util.stream.Collectors;

public class TeamCommand {

    public static final SuggestionProvider<CommandSource> SUGGEST_TEAMS = (context, builder) -> {
        return ISuggestionProvider.suggest(SkyblockSavedData.get(context.getSource().asPlayer().getServerWorld())
                .getTeams().stream().map(Team::getName).filter(name -> !name.equalsIgnoreCase("spawn")).collect(Collectors.toSet()), builder);
    };

    public static ArgumentBuilder<CommandSource, ?> register() {
        return Commands.literal("teams").requires(source -> source.hasPermissionLevel(2))
                .then(Commands.literal("create")
                        .executes(context -> createTeam(context.getSource()))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> createTeam(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("join")
                        .then(Commands.argument("team", StringArgumentType.word()).suggests(SUGGEST_TEAMS)
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(context -> addToTeam(context.getSource(), StringArgumentType.getString(context, "team"), EntityArgument.getPlayers(context, "players"))))))
                .then(Commands.literal("leave")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> removeFromTeam(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("team", StringArgumentType.word()).suggests(SUGGEST_TEAMS)
                                .executes(context -> deleteTeam(context.getSource(), StringArgumentType.getString(context, "team")))))
                .then(Commands.literal("clear")
                        .executes(context -> deleteEmptyTeams(context.getSource())));
    }

    private static int deleteEmptyTeams(CommandSource source) {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        int i = 0;
        Iterator<Team> itr = data.getTeams().iterator();
        while (itr.hasNext()) {
            Team team = itr.next();
            if (team.isEmpty()) {
                itr.remove();
                i++;
            }
        }
        data.markDirty();

        source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandKey("clear.info"), i), true);
        return 1;
    }

    private static int createTeam(CommandSource source) {
        String team;
        Random rand = new Random();
        do {
            team = NameGenerator.randomName(rand);
        } while (SkyblockSavedData.get(source.getWorld()).teamExists(team));
        return createTeam(source, team);
    }

    private static int createTeam(CommandSource source, String name) {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        if (data.teamExists(name)) {
            source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandErrorKey("team_exist"), name), false);
            return 0;
        }

        Team team = data.createTeam(name);
        //noinspection ConstantConditions
        IslandPos islandPos = team.getIsland();
        source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandKey("teams.create.info"), name), false);
        return 1;
    }

    private static int deleteTeam(CommandSource source, String team) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        ServerPlayerEntity player = source.asPlayer();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        if (!data.teamExists(team)) {
            player.sendStatusMessage(new TranslationTextComponent(TranslationUtil.getCommandErrorKey("team_not_exist")).mergeStyle(TextFormatting.RED), false);
            return 0;
        }

        if (!data.deleteTeam(team)) {
            player.sendStatusMessage(new TranslationTextComponent("deleting_error", team).mergeStyle(TextFormatting.RED), false);
            return 0;
        }

        player.sendStatusMessage(new TranslationTextComponent(TranslationUtil.getCommandInfoKey("teams.deleting"), team).mergeStyle(TextFormatting.GREEN), false);
        return 1;
    }

    private static int addToTeam(CommandSource source, String team, Collection<ServerPlayerEntity> players) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        ServerPlayerEntity player = source.asPlayer();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        if (!data.teamExists(team)) {
            player.sendStatusMessage(new TranslationTextComponent(TranslationUtil.getCommandErrorKey("team_not_exist")).mergeStyle(TextFormatting.RED), false);
            return 0;
        }

        IslandPos island = data.getTeamIsland(team);
        ServerPlayerEntity added = null;
        int i = 0;
        for (ServerPlayerEntity addedPlayer : players) {
            if (!data.hasPlayerTeam(addedPlayer)) {
                data.addPlayerToTeam(team, addedPlayer);
                //noinspection ConstantConditions
                teleportToIsland(addedPlayer, island);
                if (i == 0) added = addedPlayer;
                i++;
            }
        }

        if (i == 0) {
            source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandErrorKey("no_player_added")).mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        if (i == 1) source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandInfoKey("teams.added_player_single"), added.getDisplayName().getString(), team).mergeStyle(TextFormatting.GREEN), true);
        else source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandInfoKey("teams.added_player_multiple"), i, team), true);
        return 1;
    }

    private static int removeFromTeam(CommandSource source, ServerPlayerEntity player) {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        Team team = data.getTeamFromPlayer(player);

        if (team == null) {
            source.sendFeedback(new TranslationTextComponent(TranslationUtil.getCommandErrorKey("player_no_team")).mergeStyle(TextFormatting.RED), false);
            return 0;
        }

        String teamName = team.getName();
        data.removePlayerFromTeam(player);
        IslandPos spawn = data.getSpawn();
        teleportToIsland(player, spawn);
        player.sendStatusMessage(new TranslationTextComponent(TranslationUtil.getCommandInfoKey("teams.left"), teamName).mergeStyle(TextFormatting.GREEN), false);
        return 1;
    }

    public static void teleportToIsland(ServerPlayerEntity player, IslandPos island) {
        ServerWorld world = player.getServerWorld();

        Set<BlockPos> possibleSpawns = SkyblockSavedData.getPossibleSpawns(island.getCenter());
        BlockPos spawn = new ArrayList<>(possibleSpawns).get(new Random().nextInt(possibleSpawns.size()));
        player.rotationYaw = 0;
        player.rotationPitch = 0;
        player.setPositionAndUpdate(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        player.func_242111_a(player.world.getDimensionKey(), spawn, 0, true, false);
    }
}
