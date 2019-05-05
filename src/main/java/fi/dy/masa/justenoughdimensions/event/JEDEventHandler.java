package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
import fi.dy.masa.justenoughdimensions.world.IWorldProviderJED;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

public class JEDEventHandler
{
    private static final Set<String> REDIRECTED_COMMANDS = new HashSet<>();

    public JEDEventHandler()
    {
        REDIRECTED_COMMANDS.clear();
        REDIRECTED_COMMANDS.add("defaultgamemode");
        REDIRECTED_COMMANDS.add("difficulty");
        REDIRECTED_COMMANDS.add("gamerule");
        REDIRECTED_COMMANDS.add("seed");
        REDIRECTED_COMMANDS.add("setworldspawn");
        REDIRECTED_COMMANDS.add("time");
        REDIRECTED_COMMANDS.add("weather");
        REDIRECTED_COMMANDS.add("worldborder");
    }

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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWorldLoad(WorldEvent.Load event)
    {
        World world = event.getWorld();
        int dimension = world.provider.getDimension();

        JustEnoughDimensions.logInfo("WorldEvent.Load - DIM: {}", dimension);

        if (world.isRemote == false)
        {
            overrideWorldInfoAndBiomeProvider(world);
            WorldFileUtils.createTemporaryWorldMarkerIfApplicable(world);

            // For the overworld the spawn point search happens from WorldEvent.CreateSpawnPosition
            if (world.provider.getDimension() != 0)
            {
                WorldUtils.findAndSetWorldSpawnIfApplicable(world);
                WorldUtils.placeSpawnStructureIfApplicable(world);
            }

            WorldUtils.centerWorldBorderIfApplicable(world);

            if (Configs.enableSeparateWorldBorders)
            {
                WorldBorderUtils.removeOverworldBorderListener(world);
                world.getWorldBorder().addListener(new JEDBorderListener(dimension));
            }
        }
        else
        {
            WorldUtils.overrideWorldProviderIfApplicable(world);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event)
    {
        if (event.getWorld().isRemote == false)
        {
            JustEnoughDimensions.logInfo("WorldEvent.Unload - DIM: {}", event.getWorld().provider.getDimension());
            WorldUtils.removeTemporaryWorldIfApplicable(event.getWorld());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWorldCreateSpawn(WorldEvent.CreateSpawnPosition event)
    {
        World world = event.getWorld();
        BlockPos origSpawn = world.getSpawnPoint();

        JustEnoughDimensions.logInfo("WorldEvent.CreateSpawnPosition - DIM: {}", world.provider.getDimension());

        overrideWorldInfoAndBiomeProvider(world);
        WorldFileUtils.createTemporaryWorldMarkerIfApplicable(world);

        // Find a proper spawn point for the overworld that isn't inside ground...
        // For other dimensions than the regular overworld, this is done after
        // (and only if) setting up the custom WorldInfo override for a newly
        // created dimension, see overrideBiomeProviderAndFindSpawn().
        if (world.provider.getDimension() == 0)
        {
            WorldUtils.findAndSetWorldSpawnIfApplicable(world);
            WorldUtils.placeSpawnStructureIfApplicable(world);
        }

        // The spawn point was set/moved
        if (origSpawn.equals(world.getSpawnPoint()) == false ||
            (world.provider instanceof IWorldProviderJED) && ((IWorldProviderJED) world.provider).getShouldSkipSpawnSearch())
        {
            if (event.getSettings().isBonusChestEnabled())
            {
                JustEnoughDimensions.logInfo("WorldEvent.CreateSpawnPosition - Generating a bonus chest");
                WorldUtils.createBonusChest(world);
            }

            JustEnoughDimensions.logInfo("WorldEvent.CreateSpawnPosition - Canceling the normal spawn point search, as JED set the world spawn");
            event.setCanceled(true);
        }
    }

    private static void overrideWorldInfoAndBiomeProvider(World world)
    {
        // Copying/handling template worlds needs to happen before WorldInfo overrides,
        // in case we need to apply custom values from the dimension config on top of any existing values.
        WorldFileUtils.copyTemplateWorldIfApplicable(world);

        if (Configs.enableOverrideWorldProvider)
        {
            WorldUtils.overrideWorldProviderIfApplicable(world);
        }

        if (Configs.enableSeparateWorldInfo)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfo(world);
        }

        if (Configs.enableOverrideBiomeProvider)
        {
            WorldUtils.overrideBiomeProvider(world);
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event)
    {
        WorldFileUtils.saveCustomWorldInfoToFile(event.getWorld());

        if (Configs.enableForcedGameModes && event.getWorld().provider.getDimension() == 0)
        {
            DataTracker.getInstance().writeToDisk();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        JustEnoughDimensions.logInfo("PlayerEvent.PlayerLoggedInEvent - DIM: {}", event.player.getEntityWorld().provider.getDimension());
        this.syncAndSetPlayerData(event.player);
        DataTracker.getInstance().playerLoginOrRespawn(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        EntityPlayer player = event.player;
        World world = player.getEntityWorld();
        final boolean wasDeath = event.isEndConquered() == false;

        JustEnoughDimensions.logInfo("PlayerEvent.PlayerRespawnEvent - DIM: {}, death?: {}", world.provider.getDimension(), wasDeath);

        this.syncAndSetPlayerData(player);
        DataTracker.getInstance().playerLoginOrRespawn(player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        JustEnoughDimensions.logInfo("PlayerEvent.PlayerChangedDimensionEvent - DIM: {}", event.player.getEntityWorld().provider.getDimension());
        this.syncAndSetPlayerData(event.player);
        DataTracker.getInstance().playerChangedDimension(event.player, event.fromDim, event.toDim);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntityLiving() instanceof EntityPlayerMP)
        {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

            // The code in NetHandlerPlayServer#processClientStatus() only checks the server setting for hardcore
            if (player.getEntityWorld().getWorldInfo().isHardcoreModeEnabled())
            {
                int dim = player.getEntityWorld().provider.getDimension();
                JustEnoughDimensions.logInfo("LivingDeathEvent: Player '{}' died in a hardcore mode dimension {}", player.getName(), dim);
                player.setGameType(GameType.SPECTATOR);
                player.getEntityWorld().getGameRules().setOrCreateGameRule("spectatorsGenerateChunks", "false");
                DataTracker.getInstance().playerDied(player);
            }
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
        if (Configs.enableInitialSpawnDimensionOverride &&
            (new File(event.getPlayerDirectory(), event.getPlayerUUID() + ".dat")).exists() == false)
        {
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(Configs.initialSpawnDimensionId);
            EntityPlayer player = event.getEntityPlayer();

            if (world != null && Configs.initialSpawnDimensionId != player.dimension)
            {
                // Set the player's initial spawn dimension, and move them to the world spawn
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

                DataTracker.getInstance().playerInitialSpawn(player);
            }
            else
            {
                JustEnoughDimensions.logger.warn("Player {} joined for the first time, but the currently set" +
                        " initial spawn dimension {} didn't exist", event.getEntityPlayer().getName(), Configs.initialSpawnDimensionId);
            }
        }
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event)
    {
        String command = event.getCommand().getName();

        if (Configs.enableCommandRedirecting && REDIRECTED_COMMANDS.contains(command) &&
            event.getSender().getEntityWorld().getWorldInfo() instanceof WorldInfoJED)
        {
            String newCommand = "jed " + command + " " + String.join(" ", event.getParameters());
            JustEnoughDimensions.logInfo("Redirecting a vanilla command '/{}' to the JED variant as '/{}'", command, newCommand);
            event.setCanceled(true);
            FMLCommonHandler.instance().getMinecraftServerInstance().getCommandManager().executeCommand(event.getSender(), newCommand);
        }
    }
}
