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
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.WorldInfoType;
import fi.dy.masa.justenoughdimensions.world.IWorldProviderJED;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

public class WorldInfoUtils
{
    private static Field field_worldInfo = null;

    static
    {
        try
        {
            field_worldInfo = ReflectionHelper.findField(World.class, "field_72986_A", "worldInfo");
        }
        catch (UnableToFindFieldException e)
        {
            JustEnoughDimensions.logger.error("WorldInfoUtils: Reflection failed!!", e);
        }
    }

    /**
     * Overrides the current WorldInfo with WorldInfoJED.
     * @param world
     * @param dimension
     */
    public static void loadAndSetCustomWorldInfo(World world)
    {
        int dimension = world.provider.getDimension();

        // The WorldInfoJED check is necessary (or at least avoids setting things twice)
        // for the case where WorldEvent.CreateSpawnPosition has ran before WorldEvent.LOAD
        // and already set it, OR when using JED WorldProviders, when the
        // WorldInfo override first happens from WorldProvider#setDimension() and
        // then again from WorldEvent.Load.
        if ((world.getWorldInfo() instanceof WorldInfoJED) == false &&
            DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            JustEnoughDimensions.logInfo("WorldInfoUtils.loadAndSetCustomWorldInfo(): Overriding the existing " +
                                         "WorldInfo with WorldInfoJED for dimension {}", dimension);
            setWorldInfo(world, createCustomWorldInfoFor(world, dimension));
        }
    }

    /**
     * Creates a new WorldInfoJED object for the given dimension.
     * @param world
     * @param dimension
     * @return the created WorldInfoJED instance
     */
    private static WorldInfoJED createCustomWorldInfoFor(World world, int dimension)
    {
        NBTTagCompound nbt = loadWorldInfoFromFile(world, WorldFileUtils.getWorldDirectory(world));
        NBTTagCompound playerNBT = world.getWorldInfo().getPlayerNBTTagCompound();
        boolean isDimensionInit = false;

        // No level.dat exists for this dimension yet, inherit the values from the overworld
        if (nbt == null)
        {
            // Get the values from the overworld WorldInfo
            nbt = world.getWorldInfo().cloneNBTCompound(playerNBT);

            // Search for a proper suitable spawn position
            isDimensionInit = true;
        }

        // Any tags/properties that are set in the dimensions.json take precedence over the level.dat
        DimensionConfig.instance().setWorldInfoValues(dimension, nbt, WorldInfoType.REGULAR);

        // On the first time this dimension loads (at least with custom WorldInfo),
        // set the worldinfo_onetime values
        if (isDimensionInit)
        {
            DimensionConfig.instance().setWorldInfoValues(dimension, nbt, WorldInfoType.ONE_TIME);
        }

        return new WorldInfoJED(nbt);
    }

    public static void saveCustomWorldInfoToFile(World world)
    {
        int dimension = world.provider.getDimension();

        if (Configs.enableSeparateWorldInfo && world.isRemote == false && dimension != 0 &&
            DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            saveWorldInfoToFile(world, WorldFileUtils.getWorldDirectory(world));
        }
    }

    private static NBTTagCompound loadWorldInfoFromFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logInfo("WorldInfoUtils.loadWorldInfoFromFile(): null worldDir");
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
                JustEnoughDimensions.logInfo("WorldInfoUtils.loadWorldInfoFromFile(): Read world info from file '{}'", levelFile.getPath());
                return nbt;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Exception reading " + levelFile.getPath(), e);
                return null;
            }

            //return SaveFormatOld.loadAndFix(fileLevel, world.getMinecraftServer().getDataFixer(), (SaveHandler) world.getSaveHandler());
        }

        JustEnoughDimensions.logInfo("WorldInfoUtils.loadWorldInfoFromFile(): level.dat didn't exist for dimension {}", world.provider.getDimension());
        return null;
    }

    private static void setWorldInfo(World world, WorldInfoJED info)
    {
        if (field_worldInfo != null)
        {
            try
            {
                field_worldInfo.set(world, info);

                if (world.provider instanceof IWorldProviderJED)
                {
                    JustEnoughDimensions.logInfo("Setting JED properties in the WorldProvider for dimension {}", world.provider.getDimension());

                    ((IWorldProviderJED) world.provider).setJEDPropertiesFromWorldProperties(info);

                    // This sets the WorldType (the terrainType field) and the generatorSettings field
                    // from the newly overridden WorldInfoJED
                    world.provider.setWorld(world);
                }

                WorldBorderUtils.setWorldBorderValues(world);

                // Override/re-create the ChunkProvider when using overridden WorldInfo,
                // and NOT using one of the JED WorldProviders.
                // Otherwise the ChunkProvider would be using the settings from the overworld, because
                // WorldEvent.Load (where the WorldInfo gets overridden) only happens after
                // the world has been constructed and the ChunkProvider set.

                // However, for JED WorldProviders the WorldInfo, and more importantly,
                // the terrainType and generatorSettings in the WorldProvider get set/overridden earlier
                // than WorldEvent.Load, via the WorldProvider#setDimension() method, which gets called from
                // the WorldServer constructor. Therefore the ChunkProvider for JED WorldProviders
                // already has the correct settings available to it when it gets created in the first place.
                if ((world.provider instanceof IWorldProviderJED) == false)
                {
                    WorldUtils.reCreateChunkProvider(world);
                }
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("WorldInfoUtils.setWorldInfo(): Failed to override WorldInfo for dimension {}",
                        world.provider.getDimension());
            }
        }
    }

    private static void saveWorldInfoToFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logger.warn("WorldInfoUtils.saveWorldInfoToFile(): No worldDir found");
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
                JustEnoughDimensions.logger.error("Failed to rename {} to {}", fileCurrent.getPath(), fileOld.getPath());
                return;
            }

            fileNew.renameTo(fileCurrent);

            if (fileNew.exists())
            {
                JustEnoughDimensions.logger.error("Failed to rename {} to {}", fileNew.getPath(), fileCurrent.getPath());
                return;
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.error("WorldInfoUtils.saveWorldInfoToFile(): Failed to save world "+
                                              "info to file for dimension {}", world.provider.getDimension(), e);
        }
    }
}
