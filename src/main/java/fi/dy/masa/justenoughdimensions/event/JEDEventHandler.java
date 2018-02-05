package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.FMLOutboundHandler;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import fi.dy.masa.justenoughdimensions.network.DimensionSyncPacket;
import fi.dy.masa.justenoughdimensions.util.world.WorldBorderUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldFileUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldInfoUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldUtils;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;

public class JEDEventHandler
{
    @SubscribeEvent
    public void onConnectionCreated(FMLNetworkEvent.ServerConnectionFromClientEvent event)
    {
        JustEnoughDimensions.logInfo("FMLNetworkEvent.ServerConnectionFromClientEvent: Syncing dimension data to client");
        DimensionSyncPacket packet = new DimensionSyncPacket();
        packet.addDimensionData(DimensionConfig.instance().getRegisteredDimensions());

        FMLEmbeddedChannel channel = JustEnoughDimensions.channels.get(Side.SERVER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.DISPATCHER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(event.getManager().channel().attr(NetworkDispatcher.FML_DISPATCHER).get());
        channel.writeOutbound(packet);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        World world = event.getWorld();

        if (world.isRemote == false)
        {
            JustEnoughDimensions.logInfo("WorldEvent.Load - DIM: {}", world.provider.getDimension());

            overrideWorldInfoAndBiomeProvider(world);
            WorldUtils.findAndSetWorldSpawnIfApplicable(world);

            if (Configs.enableSeparateWorldBorders)
            {
                WorldBorderUtils.removeOverworldBorderListener(world);
                world.getWorldBorder().addListener(new JEDBorderListener(world.provider.getDimension()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldCreateSpawn(WorldEvent.CreateSpawnPosition event)
    {
        World world = event.getWorld();
        JustEnoughDimensions.logInfo("WorldEvent.CreateSpawnPosition - DIM: {}", world.provider.getDimension());

        overrideWorldInfoAndBiomeProvider(world);

        // Find a proper spawn point for the overworld that isn't inside ground...
        // For other dimensions than the regular overworld, this is done after
        // (and only if) setting up the custom WorldInfo override for a newly
        // created dimension, see overrideBiomeProviderAndFindSpawn().
        if (world.provider.getDimension() == 0)
        {
            WorldUtils.findAndSetWorldSpawn(world);

            if (event.getSettings().isBonusChestEnabled())
            {
                JustEnoughDimensions.logInfo("WorldEvent.CreateSpawnPosition - Generating a bonus chest");
                WorldUtils.createBonusChest(world);
            }

            event.setCanceled(true);
        }
    }

    private static void overrideWorldInfoAndBiomeProvider(World world)
    {
        if (Configs.enableSeparateWorldInfo)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfo(world);
        }

        JEDWorldProperties.applyJEDWorldPropertiesToWorldProvider(world);

        if (Configs.enableOverrideBiomeProvider)
        {
            WorldUtils.overrideBiomeProvider(world);
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event)
    {
        WorldFileUtils.saveCustomWorldInfoToFile(event.getWorld());

        if (Configs.enableForcedGamemodes && event.getWorld().provider.getDimension() == 0)
        {
            GamemodeTracker.getInstance().writeToDisk();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        JustEnoughDimensions.logInfo("PlayerEvent.PlayerLoggedInEvent - DIM: {}", event.player.getEntityWorld().provider.getDimension());
        this.syncAndSetPlayerData(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        JustEnoughDimensions.logInfo("PlayerEvent.PlayerRespawnEvent - DIM: {}", event.player.getEntityWorld().provider.getDimension());
        this.syncAndSetPlayerData(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        JustEnoughDimensions.logInfo("PlayerEvent.PlayerChangedDimensionEvent - DIM: {}", event.player.getEntityWorld().provider.getDimension());
        this.syncAndSetPlayerData(event.player);

        if (Configs.enableForcedGamemodes && event.player instanceof EntityPlayerMP)
        {
            GamemodeTracker.getInstance().playerChangedDimension((EntityPlayerMP) event.player, event.fromDim, event.toDim);
        }
    }

    private void syncAndSetPlayerData(EntityPlayer player)
    {
        WorldBorderUtils.sendWorldBorder(player);
        WorldUtils.syncWorldProviderProperties(player);
        WorldUtils.setupRespawnDimension(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityTravelToDimensionEvent(EntityTravelToDimensionEvent event)
    {
        if (DimensionManager.isDimensionRegistered(event.getDimension()) == false)
        {
            JustEnoughDimensions.logInfo("EntityTravelToDimensionEvent: Dimension {} is not registered, canceling the TP", event.getDimension());
            event.setCanceled(true);
            return;
        }

        final int dimFrom = event.getEntity().getEntityWorld().provider.getDimension();
        DimensionConfigEntry entryFrom = DimensionConfig.instance().getDimensionConfigFor(dimFrom);

        if (entryFrom != null && entryFrom.getDisableTeleportingFrom())
        {
            JustEnoughDimensions.logInfo("EntityTravelToDimensionEvent: Teleporting from DIM {} has been disabled " +
                                         "in the dimension config, canceling the TP", dimFrom);
            event.setCanceled(true);
            return;
        }

        DimensionConfigEntry entryTo = DimensionConfig.instance().getDimensionConfigFor(event.getDimension());

        if (entryTo != null && entryTo.getDisableTeleportingTo())
        {
            JustEnoughDimensions.logInfo("EntityTravelToDimensionEvent: Teleporting to DIM {} has been disabled " +
                                         "in the dimension config, canceling the TP", event.getDimension());
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public void onPlayerLoadingEvent(net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile event)
    {
        // No player file yet, so this is the player's first time joining this server/world
        if (Configs.enableInitialSpawnDimensionOverride && new File(event.getPlayerDirectory(), event.getPlayerUUID() + ".dat").exists() == false)
        {
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(Configs.initialSpawnDimensionId);

            if (world != null)
            {
                // Set the player's initial spawn dimension, and move them to the world spawn
                EntityPlayer player = event.getEntityPlayer();
                player.dimension = Configs.initialSpawnDimensionId;
                BlockPos pos;

                if ((world.getWorldInfo() instanceof DerivedWorldInfo) == false)
                {
                    pos = world.getSpawnPoint();
                }
                // When not using custom WorldInfo, try to find a suitable spawn location
                else
                {
                    pos = WorldUtils.findSuitableSpawnpoint(world);
                }

                JustEnoughDimensions.logInfo("Player {} joined for the first time, moving them to dimension {}, at {}",
                        player.getName(), Configs.initialSpawnDimensionId, pos);

                player.moveToBlockPosAndAngles(pos, 0f, 0f);
            }
            else
            {
                JustEnoughDimensions.logger.warn("Player {} joined for the first time, but the currently set" +
                        " initial spawn dimension {} didn't exist", event.getEntityPlayer().getName(), Configs.initialSpawnDimensionId);
            }
        }
    }
}
