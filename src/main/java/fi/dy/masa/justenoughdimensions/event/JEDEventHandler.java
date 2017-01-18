package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketWorldBorder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
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
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.network.DimensionSyncPacket;
import fi.dy.masa.justenoughdimensions.network.MessageSyncWorldProviderProperties;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderJED;

public class JEDEventHandler
{
    private static final JEDEventHandler INSTANCE = new JEDEventHandler();
    private Field field_worldInfo = null;
    private Field field_WorldBorder_listeners = null;
    private Field field_WorldServerMulti_borderListener = null;
    private Field field_WorldProvider_biomeProvider = null;
    private Field field_ChunkProviderServer_chunkGenerator = null;

    public JEDEventHandler()
    {
        try
        {
            this.field_worldInfo = ReflectionHelper.findField(World.class, "field_72986_A", "worldInfo");
            this.field_WorldBorder_listeners = ReflectionHelper.findField(WorldBorder.class, "field_177758_a", "listeners");
            this.field_WorldServerMulti_borderListener = ReflectionHelper.findField(WorldServerMulti.class, "borderListener");
            this.field_WorldProvider_biomeProvider = ReflectionHelper.findField(WorldProvider.class, "field_76578_c", "biomeProvider");
            this.field_ChunkProviderServer_chunkGenerator = ReflectionHelper.findField(ChunkProviderServer.class, "field_186029_c", "chunkGenerator");
        }
        catch (UnableToFindFieldException e)
        {
            JustEnoughDimensions.logger.error("JEDEventHandler: Reflection failed!!", e);
        }
    }

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
            if (Configs.enableSeparateWorldInfo)
            {
                this.loadAndSetCustomWorldInfo(world);
            }

            if (Configs.enableSeparateWorldBorders)
            {
                this.removeOverworldBorderListener(world);
                world.getWorldBorder().addListener(new JEDBorderListener(world.provider.getDimension()));
            }

            if (Configs.enableOverrideBiomeProvider)
            {
                this.overrideBiomeProvider(world);
            }
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event)
    {
        this.saveCustomWorldInfoToFile(event.getWorld());

        if (Configs.enableForcedGamemodes && event.getWorld().provider.getDimension() == 0)
        {
            GamemodeTracker.getInstance().writeToDisk();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        this.sendWorldBorder(event.player);
        this.syncWorldProviderProperties(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        this.sendWorldBorder(event.player);
        this.syncWorldProviderProperties(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        this.sendWorldBorder(event.player);
        this.syncWorldProviderProperties(event.player);

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

    private void syncWorldProviderProperties(EntityPlayer player)
    {
        World world = player.getEntityWorld();

        if (world.getWorldInfo() instanceof WorldInfoJED && player instanceof EntityPlayerMP)
        {
            PacketHandler.INSTANCE.sendTo(new MessageSyncWorldProviderProperties((WorldInfoJED) world.getWorldInfo()), (EntityPlayerMP) player);
        }
    }

    private void overrideBiomeProvider(World world)
    {
        int dimension = world.provider.getDimension();
        String biomeName = DimensionConfig.instance().getBiomeFor(dimension);
        Biome biome = biomeName != null ? Biome.REGISTRY.getObject(new ResourceLocation(biomeName)) : null;

        if (biome != null)
        {
            JustEnoughDimensions.logInfo("Overriding the BiomeProvider for dimension {} with BiomeProviderSingle" +
                " using the biome '{}' ('{}')", dimension, biomeName, biome.getBiomeName());

            BiomeProvider provider = new BiomeProviderSingle(biome);
            try
            {
                this.field_WorldProvider_biomeProvider.set(world.provider, provider);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to override the BiomeProvider of dimension {}", dimension);
            }
        }
    }

    private void sendWorldBorder(EntityPlayer player)
    {
        if (Configs.enableSeparateWorldBorders && player.getEntityWorld().isRemote == false && (player instanceof EntityPlayerMP))
        {
            ((EntityPlayerMP) player).connection.sendPacket(
                    new SPacketWorldBorder(player.getEntityWorld().getWorldBorder(), SPacketWorldBorder.Action.INITIALIZE));
        }
    }

    public void removeDefaultBorderListeners()
    {
        World overworld = DimensionManager.getWorld(0);

        if (Configs.enableSeparateWorldBorders && overworld != null)
        {
            try
            {
                @SuppressWarnings("unchecked")
                List<IBorderListener> listeners = (List<IBorderListener>) this.field_WorldBorder_listeners.get(overworld.getWorldBorder());

                if (listeners != null)
                {
                    listeners.clear();
                    overworld.getWorldBorder().addListener(new JEDBorderListener(0));
                }
            }
            catch (IllegalArgumentException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
            catch (IllegalAccessException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
        }
    }

    private void removeOverworldBorderListener(World world)
    {
        World overworld = DimensionManager.getWorld(0);

        if (Configs.enableSeparateWorldBorders && overworld != null && (world instanceof WorldServerMulti))
        {
            try
            {
                @SuppressWarnings("unchecked")
                List<IBorderListener> overworldListeners = (List<IBorderListener>) this.field_WorldBorder_listeners.get(overworld.getWorldBorder());
                IBorderListener listener = (IBorderListener) this.field_WorldServerMulti_borderListener.get(world);

                if (overworldListeners != null)
                {
                    overworldListeners.remove(listener);
                }
            }
            catch (IllegalArgumentException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
            catch (IllegalAccessException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
        }
    }

    private void loadAndSetCustomWorldInfo(World world)
    {
        int dimension = world.provider.getDimension();

        if (DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            JustEnoughDimensions.logInfo("Using custom WorldInfo for dimension {}", dimension);

            NBTTagCompound nbt = this.loadWorldInfoFromFile(world, this.getWorldDirectory(world));
            NBTTagCompound playerNBT = world.getMinecraftServer().getPlayerList().getHostPlayerData();
            boolean findSpawn = false;

            // No level.dat exists for this dimension yet, inherit the values from the overworld
            if (nbt == null)
            {
                if (playerNBT == null)
                {
                    playerNBT = new NBTTagCompound();
                }

                // This is just to set the dimension field in WorldInfo, so that
                // the crash report shows the correct dimension ID... maybe
                playerNBT.setInteger("Dimension", dimension);

                nbt = world.getWorldInfo().cloneNBTCompound(playerNBT);

                // Set the initialized status to false, which causes a new spawn point to be searched for
                // for this dimension, instead of using the exact same location as the overworld, see WorldServer#initialize().
                nbt.setBoolean("initialized", false);
                findSpawn = true;
            }

            // Any tags/properties that are set in the dimensions.json take precedence over the level.dat
            nbt.merge(DimensionConfig.instance().getWorldInfoValues(dimension, nbt));

            this.setWorldInfo(world, new WorldInfoJED(nbt));

            if (findSpawn)
            {
                world.initialize(new WorldSettings(world.getWorldInfo()));
            }
        }
    }

    private NBTTagCompound loadWorldInfoFromFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logger.warn("loadWorldInfo(): No worldDir found");
            return null;
        }

        /*if ((world.getSaveHandler() instanceof SaveHandler) == false)
        {
            JustEnoughDimensions.logger.error("loadWorldInfo(): Invalid SaveHandler");
            return null;
        }*/

        File fileLevel = new File(worldDir, "level.dat");

        if (fileLevel.exists())
        {
            try
            {
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(new FileInputStream(fileLevel));
                nbt = world.getMinecraftServer().getDataFixer().process(FixTypes.LEVEL, nbt.getCompoundTag("Data"));
                //FMLCommonHandler.instance().handleWorldDataLoad((SaveHandler) world.getSaveHandler(), info, nbt);

                //JustEnoughDimensions.logger.info("Loaded custom WorldInfo from {}", fileLevel.getName());
                return nbt;
            }
            //catch (net.minecraftforge.fml.common.StartupQuery.AbortedException e) { throw e; }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Exception reading " + fileLevel.getName(), (Throwable) e);
                return null;
            }

            //return SaveFormatOld.loadAndFix(fileLevel, world.getMinecraftServer().getDataFixer(), (SaveHandler) world.getSaveHandler());
        }

        return null;
    }

    private void saveCustomWorldInfoToFile(World world)
    {
        if (Configs.enableSeparateWorldInfo && world.isRemote == false &&
            DimensionConfig.instance().useCustomWorldInfoFor(world.provider.getDimension()))
        {
            this.saveWorldInfoToFile(world, this.getWorldDirectory(world));
        }
    }

    private void saveWorldInfoToFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logger.error("saveWorldInfo(): No worldDir found");
            return;
        }

        WorldInfo info = world.getWorldInfo();
        info.setBorderSize(world.getWorldBorder().getDiameter());
        info.getBorderCenterX(world.getWorldBorder().getCenterX());
        info.getBorderCenterZ(world.getWorldBorder().getCenterZ());
        info.setBorderSafeZone(world.getWorldBorder().getDamageBuffer());
        info.setBorderDamagePerBlock(world.getWorldBorder().getDamageAmount());
        info.setBorderWarningDistance(world.getWorldBorder().getWarningDistance());
        info.setBorderWarningTime(world.getWorldBorder().getWarningTime());
        info.setBorderLerpTarget(world.getWorldBorder().getTargetSize());
        info.setBorderLerpTime(world.getWorldBorder().getTimeUntilTarget());

        NBTTagCompound rootTag = new NBTTagCompound();
        NBTTagCompound playerNBT = world.getMinecraftServer().getPlayerList().getHostPlayerData();
        if (playerNBT == null)
        {
            playerNBT = new NBTTagCompound();
        }

        // This is just to set the dimension field in WorldInfo, so that
        // the crash report shows the correct dimension ID... maybe
        playerNBT.setInteger("Dimension", world.provider.getDimension());

        rootTag.setTag("Data", info.cloneNBTCompound(playerNBT));

        if (world.getSaveHandler() instanceof SaveHandler)
        {
            FMLCommonHandler.instance().handleWorldDataSave((SaveHandler) world.getSaveHandler(), info, rootTag);
        }

        try
        {
            File fileNew = new File(worldDir, "level.dat_new");
            File fileOld = new File(worldDir, "level.dat_old");
            File fileCurrent = new File(worldDir, "level.dat");
            CompressedStreamTools.writeCompressed(rootTag, new FileOutputStream(fileNew));

            if (fileOld.exists())
            {
                fileOld.delete();
            }

            fileCurrent.renameTo(fileOld);

            if (fileCurrent.exists())
            {
                JustEnoughDimensions.logger.error("Failed to rename {} to {}", fileCurrent.getName(), fileOld.getName());
                return;
            }

            fileNew.renameTo(fileCurrent);

            if (fileNew.exists())
            {
                JustEnoughDimensions.logger.error("Failed to rename {} to {}", fileNew.getName(), fileCurrent.getName());
                return;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private File getWorldDirectory(World world)
    {
        IChunkProvider chunkProvider = world.getChunkProvider();

        if (chunkProvider instanceof ChunkProviderServer)
        {
            ChunkProviderServer chunkProviderServer = (ChunkProviderServer) chunkProvider;
            IChunkLoader chunkLoader = chunkProviderServer.chunkLoader;

            if (chunkLoader instanceof AnvilChunkLoader)
            {
                return ((AnvilChunkLoader) chunkLoader).chunkSaveLocation;
            }
        }

        return null;
    }

    private void setWorldInfo(World world, WorldInfoJED info)
    {
        if (this.field_worldInfo != null)
        {
            try
            {
                this.field_worldInfo.set(world, info);
                this.setWorldBorderValues(world);
                this.setChunkProvider(world);

                if (world.provider instanceof WorldProviderJED)
                {
                    ((WorldProviderJED) world.provider).setJEDPropertiesFromWorldInfo(info);
                }
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("JEDEventHandler: Failed to override WorldInfo for dimension {}", world.provider.getDimension());
            }
        }
    }

    private void setWorldBorderValues(World world)
    {
        WorldBorder border = world.getWorldBorder();
        WorldInfo info = world.getWorldInfo();

        border.setCenter(info.getBorderCenterX(), info.getBorderCenterZ());
        border.setDamageAmount(info.getBorderDamagePerBlock());
        border.setDamageBuffer(info.getBorderSafeZone());
        border.setWarningDistance(info.getBorderWarningDistance());
        border.setWarningTime(info.getBorderWarningTime());

        if (info.getBorderLerpTime() > 0L)
        {
            border.setTransition(info.getBorderSize(), info.getBorderLerpTarget(), info.getBorderLerpTime());
        }
        else
        {
            border.setTransition(info.getBorderSize());
        }
    }

    private void setChunkProvider(World world)
    {
        World overworld = DimensionManager.getWorld(0);

        // Don't override unless the WorldType has been changed from the default
        if (overworld == null || world.getWorldInfo().getTerrainType().getName().equals(overworld.getWorldInfo().getTerrainType().getName()))
        {
            return;
        }

        if (world instanceof WorldServer)
        {
            // This sets the new WorldType to the WorldProvider
            world.provider.registerWorld(world);

            IChunkGenerator newChunkProvider = world.provider.createChunkGenerator();

            if (newChunkProvider == null)
            {
                JustEnoughDimensions.logger.warn("Failed to re-create the ChunkProvider");
                return;
            }

            int dimension = world.provider.getDimension();
            JustEnoughDimensions.logInfo("Attempting to override the ChunkProvider (of type {}) in dimension {} with {}",
                    ((ChunkProviderServer) world.getChunkProvider()).chunkGenerator.getClass().getName(),
                    dimension, newChunkProvider.getClass().getName());

            try
            {
                this.field_ChunkProviderServer_chunkGenerator.set((ChunkProviderServer) world.getChunkProvider(), newChunkProvider);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to override the ChunkProvider for dimension {} with {}",
                        dimension, newChunkProvider.getClass().getName(), e);
            }
        }
    }
}
