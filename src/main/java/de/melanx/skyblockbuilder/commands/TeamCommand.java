package de.melanx.skyblockbuilder.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.melanx.skyblockbuilder.ConfigHandler;
import de.melanx.skyblockbuilder.commands.operator.ManageCommand;
import de.melanx.skyblockbuilder.events.SkyblockHooks;
import de.melanx.skyblockbuilder.util.Team;
import de.melanx.skyblockbuilder.util.TemplateLoader;
import de.melanx.skyblockbuilder.world.data.SkyblockSavedData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.Event;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

public class TeamCommand {

    public static final SuggestionProvider<CommandSource> SUGGEST_POSITIONS = (context, builder) -> {
        Team team = SkyblockSavedData.get(context.getSource().getWorld()).getTeamFromPlayer(context.getSource().asPlayer());
        if (team != null) {
            Set<BlockPos> possibleSpawns = team.getPossibleSpawns();
            possibleSpawns.forEach(spawn -> builder.suggest(String.format("%s %s %s", spawn.getX(), spawn.getY(), spawn.getZ())));
        }

        return BlockPosArgument.blockPos().listSuggestions(context, builder);
    };

    public static ArgumentBuilder<CommandSource, ?> register() {
        return Commands.literal("team")
                // Let plays add/remove spawn points
                .then(Commands.literal("spawns")
                        .then(Commands.literal("add")
                                .executes(context -> addSpawn(context.getSource(), new BlockPos(context.getSource().getPos())))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> addSpawn(context.getSource(), BlockPosArgument.getBlockPos(context, "pos")))))
                        .then(Commands.literal("remove")
                                .executes(context -> removeSpawn(context.getSource(), new BlockPos(context.getSource().getPos())))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).suggests(SUGGEST_POSITIONS)
                                        .executes(context -> removeSpawn(context.getSource(), BlockPosArgument.getBlockPos(context, "pos")))))
                        .then(Commands.literal("reset")
                                .executes(context -> resetSpawns(context.getSource(), null))
                                .then(Commands.argument("team", StringArgumentType.word()).suggests(ManageCommand.SUGGEST_TEAMS)
                                        .executes(context -> resetSpawns(context.getSource(), StringArgumentType.getString(context, "team"))))))

                // Renaming a team
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> renameTeam(context.getSource(), StringArgumentType.getString(context, "name"), null))
                                .then(Commands.argument("team", StringArgumentType.word()).suggests(ManageCommand.SUGGEST_TEAMS)
                                        .executes(context -> renameTeam(context.getSource(), StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "team"))))))

                // Toggle permission to visit the teams island
                .then(Commands.literal("allowVisit")
                        .executes(context -> showVisitInformation(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> toggleAllowVisit(context.getSource(), BoolArgumentType.getBool(context, "enabled")))));
    }

    private static int showVisitInformation(CommandSource source) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        Team team = data.getTeamFromPlayer(source.asPlayer());
        if (team == null) {
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.user_has_no_team").mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        boolean enabled = team.allowsVisits();
        source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.info.visit_status", new TranslationTextComponent("skyblockbuilder.command.argument." + (enabled ? "enabled" : "disabled"))).mergeStyle(TextFormatting.GOLD), true);
        return 1;
    }

    private static int toggleAllowVisit(CommandSource source, boolean enabled) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);
        ServerPlayerEntity player = source.asPlayer();

        Team team = data.getTeamFromPlayer(player);
        if (team == null) {
            source.sendFeedback(new TranslationTextComponent("Currently you aren't in a team.").mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        Pair<Event.Result, Boolean> result = SkyblockHooks.onToggleVisits(player, team, enabled);
        if (result.getLeft() == Event.Result.DENY) {
            source.sendFeedback(new TranslationTextComponent("You can not " + (result.getRight() ? "enable" : "disable") + " team visits.").mergeStyle(TextFormatting.RED), true);
            return 0;
        } else {
            team.setAllowVisit(result.getRight());
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.info.toggle_visit", new TranslationTextComponent("skyblockbuilder.command.argument." + (enabled ? "enabled" : "disabled"))).mergeStyle(TextFormatting.GOLD), true);
            return 1;
        }
    }

    private static int addSpawn(CommandSource source, BlockPos pos) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        // check for overworld
        if (world != source.getServer().func_241755_D_()) {
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.wrong_position").mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        ServerPlayerEntity player = source.asPlayer();
        Team team = data.getTeamFromPlayer(player);

        if (team == null) {
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.user_has_no_team").mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        Pair<Event.Result, BlockPos> result = SkyblockHooks.onAddSpawn(player, team, pos);
        switch (result.getLeft()) {
            case DENY:
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.denied.create_spawn").mergeStyle(TextFormatting.RED), true);
                return 0;
            case DEFAULT:
                if (!ConfigHandler.selfManageTeam.get() && !source.hasPermissionLevel(2)) {
                    source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.disabled.modify_spawns").mergeStyle(TextFormatting.RED), true);
                    return 0;
                }
                BlockPos templateSize = TemplateLoader.TEMPLATE.getSize();
                BlockPos center = team.getIsland().getCenter().toMutable();
                center.add(templateSize.getX() / 2, templateSize.getY() / 2, templateSize.getZ() / 2);
                if (!pos.withinDistance(center, ConfigHandler.modifySpawnRange.get())) {
                    source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.position_too_far_away").mergeStyle(TextFormatting.RED), true);
                    return 0;
                }
                break;
            case ALLOW:
                break;
        }

        team.addPossibleSpawn(pos);
        source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.success.spawn_added", pos.getX(), pos.getY(), pos.getZ()).mergeStyle(TextFormatting.GOLD), true);
        return 1;
    }

    private static int removeSpawn(CommandSource source, BlockPos pos) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        // check for overworld
        if (world != source.getServer().func_241755_D_()) {
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.wrong_position").mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        ServerPlayerEntity player = source.asPlayer();
        Team team = data.getTeamFromPlayer(player);

        if (team == null) {
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.user_has_no_team").mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        switch (SkyblockHooks.onRemoveSpawn(player, team, pos)) {
            case DENY:
                source.sendFeedback(new TranslationTextComponent("You can't remove this spawn point. " + (team.getPossibleSpawns().size() <= 1 ? "There are too less spawn points left." : "")).mergeStyle(TextFormatting.RED), true);
                return 0;
            case DEFAULT:
                if (!ConfigHandler.selfManageTeam.get() && !source.hasPermissionLevel(2)) {
                    source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.disabled.modify_spawns").mergeStyle(TextFormatting.RED), true);
                    return 0;
                }
            case ALLOW:
                break;
        }

        if (!team.removePossibleSpawn(pos)) {
            source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.remove_spawn0",
                    (team.getPossibleSpawns().size() <= 1
                            ? new StringTextComponent(" ").append(new TranslationTextComponent("skyblockbuilder.command.error.remove_spawn1"))
                            : "")
            ).mergeStyle(TextFormatting.RED), true);
            return 0;
        }

        source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.success.spawn_removed", pos.getX(), pos.getY(), pos.getZ()).mergeStyle(TextFormatting.GOLD), true);
        return 1;
    }

    private static int resetSpawns(CommandSource source, String name) {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        Team team;

        ServerPlayerEntity player = null;
        if (name == null) {
            if (!(source.getEntity() instanceof ServerPlayerEntity)) {
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.user_no_player").mergeStyle(TextFormatting.RED), true);
                return 0;
            }

            player = (ServerPlayerEntity) source.getEntity();
            team = data.getTeamFromPlayer(player);

            if (team == null) {
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.user_has_no_team").mergeStyle(TextFormatting.RED), true);
                return 0;
            }
        } else {
            team = data.getTeam(name);

            if (team == null) {
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.team_not_exist").mergeStyle(TextFormatting.RED), true);
                return 0;
            }
        }

        Event.Result result = SkyblockHooks.onResetSpawns(player, team);
        switch (result) {
            case DENY:
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.denied.reset_spawns").mergeStyle(TextFormatting.GOLD), true);
                return 0;
            case DEFAULT:
                if (!ConfigHandler.selfManageTeam.get() && !source.hasPermissionLevel(2)) {
                    source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.disabled.modify_spawns").mergeStyle(TextFormatting.RED), true);
                    return 0;
                }
                break;
            case ALLOW:
                break;
        }

        team.setPossibleSpawns(SkyblockSavedData.initialPossibleSpawns(team.getIsland().getCenter()));
        source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.success.reset_spawns").mergeStyle(TextFormatting.GOLD), true);
        return 1;
    }

    private static int renameTeam(CommandSource source, String newName, String oldName) throws CommandSyntaxException {
        ServerWorld world = source.getWorld();
        SkyblockSavedData data = SkyblockSavedData.get(world);

        // Rename oldName to newName
        if (oldName != null) {
            Team team = data.getTeam(oldName);
            if (team == null) {
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.team_not_exist").mergeStyle(TextFormatting.RED), true);
                return 0;
            }

            Pair<Event.Result, String> result = SkyblockHooks.onRename(null, team, newName);
            switch (result.getLeft()) {
                case DENY:
                    source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.denied_rename_team").mergeStyle(TextFormatting.RED), true);
                    return 0;
                case DEFAULT:
                    if (!source.hasPermissionLevel(2)) {
                        source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.disabled.rename_team").mergeStyle(TextFormatting.RED), true);
                        return 0;
                    }
                    break;
                case ALLOW:
                    break;
            }

            data.renameTeam(team, result.getRight());
        } else { // Get team from command user
            ServerPlayerEntity player = source.asPlayer();
            Team team = data.getTeamFromPlayer(player);

            if (team == null) {
                source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.user_has_no_team").mergeStyle(TextFormatting.RED), true);
                return 0;
            }

            Pair<Event.Result, String> result = SkyblockHooks.onRename(player, team, newName);
            switch (result.getLeft()) {
                case DENY:
                    source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.error.denied_rename_team").mergeStyle(TextFormatting.RED), true);
                    return 0;
                case DEFAULT:
                case ALLOW:
                    break;
            }

            data.renameTeam(team, result.getRight());
        }

        source.sendFeedback(new TranslationTextComponent("skyblockbuilder.command.success.rename_team", newName).mergeStyle(TextFormatting.GOLD), true);
        return 1;
    }
}
