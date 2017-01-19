package fi.dy.masa.justenoughdimensions.world.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderJED;

public class WorldInfoUtils
{
    private static Field field_worldInfo = null;
    private static Field field_ChunkProviderServer_chunkGenerator = null;

    static
    {
        try
        {
            field_worldInfo = ReflectionHelper.findField(World.class, "field_72986_A", "worldInfo");
            field_ChunkProviderServer_chunkGenerator = ReflectionHelper.findField(ChunkProviderServer.class, "field_186029_c", "chunkGenerator");
        }
        catch (UnableToFindFieldException e)
        {
            JustEnoughDimensions.logger.error("WorldInfoUtils: Reflection failed!!", e);
        }
    }

    public static void loadAndSetCustomWorldInfo(World world, boolean tryFindSpawn)
    {
        int dimension = world.provider.getDimension();

        if (DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            JustEnoughDimensions.logInfo("Using custom WorldInfo for dimension {}", dimension);

            NBTTagCompound nbt = loadWorldInfoFromFile(world, WorldFileUtils.getWorldDirectory(world));
            NBTTagCompound playerNBT = world.getMinecraftServer().getPlayerList().getHostPlayerData();
            boolean needToFindSpawn = false;

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

                // Get the values from the overworld WorldInfo
                nbt = world.getWorldInfo().cloneNBTCompound(playerNBT);

                // Search for a proper suitable spawn position
                needToFindSpawn = true;
            }

            // Any tags/properties that are set in the dimensions.json take precedence over the level.dat
            nbt.merge(DimensionConfig.instance().getWorldInfoValues(dimension, nbt));

            setWorldInfo(world, new WorldInfoJED(nbt));

            if (tryFindSpawn && needToFindSpawn)
            {
                JustEnoughDimensions.logInfo("Trying to find a world spawn for dimension {}...", dimension);
                WorldUtils.findAndSetWorldSpawn(world, true);
                JustEnoughDimensions.logInfo("Set world spawnpoint to {}...", world.getSpawnPoint());
            }
        }
    }

    public static void saveCustomWorldInfoToFile(World world)
    {
        if (Configs.enableSeparateWorldInfo && world.isRemote == false &&
            DimensionConfig.instance().useCustomWorldInfoFor(world.provider.getDimension()))
        {
            saveWorldInfoToFile(world, WorldFileUtils.getWorldDirectory(world));
        }
    }

    private static NBTTagCompound loadWorldInfoFromFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logger.warn("loadWorldInfo(): No worldDir found");
            return null;
        }

        File levelFile = new File(worldDir, "level.dat");

        if (levelFile.exists())
        {
            try
            {
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(new FileInputStream(levelFile));
                nbt = world.getMinecraftServer().getDataFixer().process(FixTypes.LEVEL, nbt.getCompoundTag("Data"));
                //FMLCommonHandler.instance().handleWorldDataLoad((SaveHandler) world.getSaveHandler(), info, nbt);
                return nbt;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Exception reading " + levelFile.getName(), (Throwable) e);
                return null;
            }

            //return SaveFormatOld.loadAndFix(fileLevel, world.getMinecraftServer().getDataFixer(), (SaveHandler) world.getSaveHandler());
        }

        return null;
    }

    private static void setWorldInfo(World world, WorldInfoJED info)
    {
        if (field_worldInfo != null)
        {
            try
            {
                field_worldInfo.set(world, info);
                WorldBorderUtils.setWorldBorderValues(world);
                setChunkProvider(world);

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

    private static void saveWorldInfoToFile(World world, @Nullable File worldDir)
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

    private static void setChunkProvider(World world)
    {
        if (world instanceof WorldServer && world.getChunkProvider() instanceof ChunkProviderServer)
        {
            // This sets the new WorldType to the WorldProvider
            world.provider.registerWorld(world);

            // Always override the ChunkProvider when using overridden WorldInfo, otherwise
            // the ChunkProvider will be using the settings from the overworld, because
            // WorldEvent.Load obviously only happens after the world has been constructed...
            ChunkProviderServer chunkProviderServer = (ChunkProviderServer) world.getChunkProvider();
            IChunkGenerator newChunkProvider = world.provider.createChunkGenerator();

            if (newChunkProvider == null)
            {
                JustEnoughDimensions.logger.warn("Failed to re-create the ChunkProvider");
                return;
            }

            int dimension = world.provider.getDimension();
            JustEnoughDimensions.logInfo("Attempting to override the ChunkProvider (of type {}) in dimension {} with {}",
                    chunkProviderServer.chunkGenerator.getClass().getName(), dimension, newChunkProvider.getClass().getName());

            try
            {
                field_ChunkProviderServer_chunkGenerator.set(chunkProviderServer, newChunkProvider);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to override the ChunkProvider for dimension {} with {}",
                        dimension, newChunkProvider.getClass().getName(), e);
            }
        }
    }
}
