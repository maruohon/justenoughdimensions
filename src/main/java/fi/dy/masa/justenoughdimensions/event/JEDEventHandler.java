package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
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
import fi.dy.masa.justenoughdimensions.network.DimensionSyncPacket;
import fi.dy.masa.justenoughdimensions.world.util.WorldBorderUtils;
import fi.dy.masa.justenoughdimensions.world.util.WorldInfoUtils;
import fi.dy.masa.justenoughdimensions.world.util.WorldUtils;

public class JEDEventHandler
{
    private static final JEDEventHandler INSTANCE = new JEDEventHandler();

    public static JEDEventHandler instance()
    {
        return INSTANCE;
    }

    @SubscribeEvent
    public void onConnectionCreated(FMLNetworkEvent.ServerConnectionFromClientEvent event)
    {
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

            if (Configs.enableSeparateWorldInfo)
            {
                WorldInfoUtils.loadAndSetCustomWorldInfo(world);
                WorldUtils.overrideBiomeProviderAndFindSpawn(world, true);
            }

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

        if (Configs.enableSeparateWorldInfo)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfo(world);
            WorldUtils.overrideBiomeProviderAndFindSpawn(world, false);
        }

        // Find a proper spawn point for the overworld that isn't inside ground...
        // For other dimensions than the regular overworld, this is done after
        // (and only if) setting up the custom WorldInfo override for a newly
        // created dimension, see overrideBiomeProviderAndFindSpawn().
        if (world.provider.getDimension() == 0)
        {
            WorldUtils.findAndSetWorldSpawn(world, false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event)
    {
        WorldInfoUtils.saveCustomWorldInfoToFile(event.getWorld());

        if (Configs.enableForcedGamemodes && event.getWorld().provider.getDimension() == 0)
        {
            GamemodeTracker.getInstance().writeToDisk();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        WorldBorderUtils.sendWorldBorder(event.player);
        WorldUtils.syncWorldProviderProperties(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        WorldBorderUtils.sendWorldBorder(event.player);
        WorldUtils.syncWorldProviderProperties(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        WorldBorderUtils.sendWorldBorder(event.player);
        WorldUtils.syncWorldProviderProperties(event.player);

        if (Configs.enableForcedGamemodes && event.player instanceof EntityPlayerMP)
        {
            GamemodeTracker.getInstance().playerChangedDimension((EntityPlayerMP) event.player, event.fromDim, event.toDim);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityTravelToDimensionEvent(EntityTravelToDimensionEvent event)
    {
        if (DimensionManager.isDimensionRegistered(event.getDimension()) == false)
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLoadingEvent(net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile event)
    {
        // No player file yet, so this is the player's first time joining this server/world
        if (Configs.enableInitialSpawnDimensionOverride && new File(event.getPlayerDirectory(), event.getPlayerUUID() + ".dat").exists() == false)
        {
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().worldServerForDimension(Configs.initialSpawnDimensionId);

            if (world != null)
            {
                // Set the player's initial spawn dimension, and move them to the world spawn
                EntityPlayer player = event.getEntityPlayer();
                player.dimension = Configs.initialSpawnDimensionId;
                player.moveToBlockPosAndAngles(world.getSpawnPoint(), 0f, 0f);
            }
        }
    }
}
