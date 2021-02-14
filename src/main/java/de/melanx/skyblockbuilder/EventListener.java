package de.melanx.skyblockbuilder;

import de.melanx.skyblockbuilder.commands.TeamCommand;
import de.melanx.skyblockbuilder.util.Registration;
import de.melanx.skyblockbuilder.util.TemplateLoader;
import de.melanx.skyblockbuilder.world.IslandPos;
import de.melanx.skyblockbuilder.world.VoidChunkGenerator;
import de.melanx.skyblockbuilder.world.data.SkyblockSavedData;
import net.minecraft.block.Blocks;
import net.minecraft.client.resources.ReloadListener;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EventListener {

    @SubscribeEvent
    public void resourcesReload(AddReloadListenerEvent event) {
        event.addListener(new ReloadListener<Object>() {
            @Nonnull
            @Override
            protected Object prepare(@Nonnull IResourceManager manager, @Nonnull IProfiler profilerIn) {
                return new Object();
            }

            @Override
            protected void apply(@Nonnull Object unused, @Nonnull IResourceManager manager, @Nonnull IProfiler profiler) {
                try {
                    TemplateLoader.loadSchematic(manager);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyblock")
                .then(TeamCommand.register()));
    }

    /*
     * Mainly taken from Botania
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        World world = event.getPlayer().world;
        if (VoidChunkGenerator.isSkyblock(world)) {
            SkyblockSavedData data = SkyblockSavedData.get((ServerWorld) world);
            if (!data.skyblocks.containsValue(Util.DUMMY_UUID)) {
                IslandPos islandPos = data.getSpawn();
                ((ServerWorld) world).func_241124_a__(islandPos.getCenter(), 0);
                spawnPlayer(event.getPlayer(), islandPos);
                SkyblockBuilder.LOGGER.info("Created the spawn island");
            }
        }
    }

    /*
     * Mainly taken from Botania
     */
    public static void spawnPlayer(PlayerEntity player, IslandPos islandPos) {
        BlockPos pos = islandPos.getCenter();

        if (player instanceof ServerPlayerEntity) {
            PlacementSettings settings = new PlacementSettings();
            TemplateLoader.TEMPLATE.func_237152_b_((IServerWorld) player.world, pos, settings, new Random());

            List<BlockPos> possibleSpawns = getPossibleSpawns(pos.toImmutable(), TemplateLoader.TEMPLATE);
            BlockPos playerPos = !possibleSpawns.isEmpty() ? possibleSpawns.get(new Random().nextInt(possibleSpawns.size())) : BlockPos.ZERO;
            ServerPlayerEntity teleportedPlayer = (ServerPlayerEntity) player;
            teleportedPlayer.rotationYaw = 0;
            teleportedPlayer.rotationPitch = 0;
            teleportedPlayer.setPositionAndUpdate(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
            teleportedPlayer.func_242111_a(teleportedPlayer.world.getDimensionKey(), playerPos, 0, true, false);

            for (BlockPos replace : possibleSpawns) {
                player.world.setBlockState(replace, Blocks.AIR.getDefaultState());
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static List<BlockPos> getPossibleSpawns(BlockPos center, Template template) {
        List<BlockPos> positions = new ArrayList<>();
        for (Template.Palette info : template.blocks) {
            for (Template.BlockInfo shit : info.func_237157_a_()) {
                if (shit.state == Registration.SPAWN_BLOCK.get().getDefaultState()) {
                    positions.add(center.add(shit.pos.toImmutable()));
                }
            }
        }
        return positions;
    }
}