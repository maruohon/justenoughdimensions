package fi.dy.masa.justenoughdimensions.world.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants;
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
        final int dimension = world.provider.getDimension();

        if (DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            // The DerivedWorldInfo check is necessary for the case where WorldEvent.CreateSpawnPosition
            // has ran before WorldEvent.Load and already set it, OR when using one of the JED WorldProviders,
            // in which case the WorldInfo override first happens from WorldProvider#setDimension() and
            // then would happen again from WorldEvent.Load.
            // It's also necessary with Sponge, which uses their own WorldProperties stuff in place of the vanilla WorldInfo.
            if (world.getWorldInfo().getClass() == DerivedWorldInfo.class)
            {
                JustEnoughDimensions.logInfo("WorldInfoUtils.loadAndSetCustomWorldInfo(): Overriding the existing " +
                                             "DerivedWorldInfo with WorldInfoJED in dimension {}", dimension);

                WorldInfoJED info = createCustomWorldInfoFor(world, dimension);
                setWorldInfo(world, info);
            }
            // We might be running under Sponge here, so instead of overriding the WorldInfo instance, try to apply
            // the configured properties to the existing one...
            else if ((world.getWorldInfo() instanceof WorldInfoJED) == false)
            {
                JustEnoughDimensions.logInfo("WorldInfoUtils.loadAndSetCustomWorldInfo(): Applying WorldInfo properties " +
                        "to an existing WorldInfo instance of type '{}' in dimension {}", world.getWorldInfo().getClass().getName(), dimension);

                applyValuesToExistingWorldInfo(world, dimension);
                applyChangesFromNewWorldInfo(world);
            }
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
        NBTTagCompound nbt = getWorldInfoTag(world, dimension, true, true);
        return new WorldInfoJED(nbt);
    }

    /**
     * Returns the NBTTagCompound for creating a new WorldInfo instance for the given World.
     * If both <i>readFromDisk</i> and <i>inheritFromOverworld</i> are false, then the returned tag
     * will only contain the values defined in the "worldinfo" objects in the dimension config.
     * @param world
     * @param dimension
     * @param readFromDisk
     * @param inheritFromOverworld
     * @return
     */
    private static NBTTagCompound getWorldInfoTag(World world, int dimension, boolean readFromDisk, boolean inheritFromOverworld)
    {
        NBTTagCompound nbt = readFromDisk ? loadWorldInfoFromFile(world, WorldFileUtils.getWorldDirectory(world)) : null;
        final boolean isDimensionInit = readFromDisk ? nbt == null : levelFileExists(world) == false;

        // No level.dat exists for this dimension yet
        if (nbt == null)
        {
            if (inheritFromOverworld)
            {
                // Get the values from the overworld WorldInfo
                NBTTagCompound playerNBT = world.getWorldInfo().getPlayerNBTTagCompound();
                nbt = world.getWorldInfo().cloneNBTCompound(playerNBT);
            }
            else
            {
                nbt = new NBTTagCompound();
            }
        }

        // Any tags/properties that are set in the dimensions.json take precedence over the level.dat
        DimensionConfig.instance().setWorldInfoValues(dimension, nbt, WorldInfoType.REGULAR);

        // On the first time this dimension loads (at least with custom WorldInfo),
        // set the worldinfo_onetime values
        if (isDimensionInit)
        {
            DimensionConfig.instance().setWorldInfoValues(dimension, nbt, WorldInfoType.ONE_TIME);
        }

        return nbt;
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

    private static boolean levelFileExists(World world)
    {
        File worldDir = WorldFileUtils.getWorldDirectory(world);

        if (worldDir != null)
        {
            File levelFile = new File(worldDir, "level.dat");
            return levelFile.exists() && levelFile.isFile();
        }

        return false;
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
                applyChangesFromNewWorldInfo(world);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("WorldInfoUtils.setWorldInfo(): Failed to override WorldInfo for dimension {}",
                        world.provider.getDimension());
            }
        }
    }

    private static void applyChangesFromNewWorldInfo(World world)
    {
        if (world.provider instanceof IWorldProviderJED)
        {
            // This sets the WorldType (the terrainType field) and the generatorSettings field
            // from the newly overridden or updated WorldInfo
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

    /**
     * Apply the settings from the "worldinfo" objects in the dimension config to an existing WorldInfo instance.
     * This is mainly meant for compatibility with Sponge or other mods that have and need their own versions of WorldInfo.
     * @param world
     * @param dimension
     */
    private static void applyValuesToExistingWorldInfo(World world, int dimension)
    {
        // Get just the values from the config
        NBTTagCompound nbt = getWorldInfoTag(world, dimension, false, false);
        WorldInfo info = world.getWorldInfo();

        // These don't have setters, so they need to be smuggled in via WorldSettings
        if (nbt.hasKey("RandomSeed", Constants.NBT.TAG_LONG) || nbt.hasKey("generatorOptions", Constants.NBT.TAG_STRING))
        {
            final long seed = nbt.hasKey("RandomSeed", Constants.NBT.TAG_LONG) ? nbt.getLong("RandomSeed") : info.getSeed();
            GameType gameType = nbt.hasKey("GameType", Constants.NBT.TAG_INT) ? GameType.getByID(nbt.getInteger("GameType")) : info.getGameType();
            boolean mapFeatures = nbt.hasKey("MapFeatures", Constants.NBT.TAG_BYTE) ? nbt.getBoolean("MapFeatures") : info.isMapFeaturesEnabled();
            boolean hardcore = nbt.hasKey("hardcore", Constants.NBT.TAG_BYTE) ? nbt.getBoolean("hardcore") : info.isHardcoreModeEnabled();
            WorldType worldType = nbt.hasKey("generatorName", Constants.NBT.TAG_STRING) ? getWorldType(nbt) : info.getTerrainType();

            WorldSettings settings = new WorldSettings(seed, gameType, mapFeatures, hardcore, worldType);
            settings.setGeneratorOptions(nbt.getString("generatorOptions"));

            info.populateFromWorldSettings(settings);
        }

        if (nbt.hasKey("generatorName", Constants.NBT.TAG_STRING) && nbt.hasKey("generatorOptions", Constants.NBT.TAG_STRING) == false)
        {
            info.setTerrainType(getWorldType(nbt));
        }

        if (nbt.hasKey("GameType", Constants.NBT.TAG_INT))          { info.setGameType(GameType.getByID(nbt.getInteger("GameType"))); }
        if (nbt.hasKey("MapFeatures", Constants.NBT.TAG_BYTE))      { info.setMapFeaturesEnabled(nbt.getBoolean("MapFeatures")); }
        if (nbt.hasKey("Time", Constants.NBT.TAG_LONG))             { info.setWorldTotalTime(nbt.getLong("Time")); }
        if (nbt.hasKey("DayTime", Constants.NBT.TAG_LONG))          { info.setWorldTime(nbt.getLong("DayTime")); }
        if (nbt.hasKey("LevelName", Constants.NBT.TAG_STRING))      { info.setWorldName(nbt.getString("LevelName")); }
        if (nbt.hasKey("clearWeatherTime", Constants.NBT.TAG_INT))  { info.setCleanWeatherTime(nbt.getInteger("clearWeatherTime")); }
        if (nbt.hasKey("rainTime", Constants.NBT.TAG_INT))          { info.setRainTime(nbt.getInteger("rainTime")); }
        if (nbt.hasKey("raining", Constants.NBT.TAG_BYTE))          { info.setRaining(nbt.getBoolean("raining")); }
        if (nbt.hasKey("thunderTime", Constants.NBT.TAG_INT))       { info.setThunderTime(nbt.getInteger("tunherTime"));}
        if (nbt.hasKey("thundering", Constants.NBT.TAG_BYTE))       { info.setThundering(nbt.getBoolean("tundering")); }
        if (nbt.hasKey("hardcore", Constants.NBT.TAG_BYTE))         { info.setHardcore(nbt.getBoolean("hardcore")); }
        if (nbt.hasKey("allowCommands", Constants.NBT.TAG_BYTE))    { info.setAllowCommands(nbt.getBoolean("allowCommands")); }
        if (nbt.hasKey("Difficulty", Constants.NBT.TAG_BYTE))       { info.setDifficulty(EnumDifficulty.getDifficultyEnum(nbt.getByte("Difficulty"))); }
        if (nbt.hasKey("DifficultyLocked", Constants.NBT.TAG_BYTE)) { info.setDifficultyLocked(nbt.getBoolean("DifficultyLocked")); }

        if (nbt.hasKey("BorderCenterX", Constants.NBT.TAG_DOUBLE))          { info.getBorderCenterX(nbt.getDouble("BorderCenterX")); }
        if (nbt.hasKey("BorderCenterZ", Constants.NBT.TAG_DOUBLE))          { info.getBorderCenterZ(nbt.getDouble("BorderCenterZ")); }
        if (nbt.hasKey("BorderSize", Constants.NBT.TAG_DOUBLE))             { info.setBorderSize(nbt.getDouble("BorderSize")); }
        if (nbt.hasKey("BorderSizeLerpTime", Constants.NBT.TAG_LONG))       { info.setBorderLerpTime(nbt.getLong("BorderSizeLerpTime")); }
        if (nbt.hasKey("BorderSizeLerpTarget", Constants.NBT.TAG_DOUBLE))   { info.setBorderLerpTarget(nbt.getDouble("BorderSizeLerpTarget")); }
        if (nbt.hasKey("BorderSafeZone", Constants.NBT.TAG_DOUBLE))         { info.setBorderSafeZone(nbt.getDouble("BorderSafeZone")); }
        if (nbt.hasKey("BorderDamagePerBlock", Constants.NBT.TAG_DOUBLE))   { info.setBorderDamagePerBlock(nbt.getDouble("BorderDamagePerBlock")); }
        if (nbt.hasKey("BorderWarningBlocks", Constants.NBT.TAG_INT))       { info.setBorderWarningDistance(nbt.getInteger("BorderWarningBlocks")); }
        if (nbt.hasKey("BorderWarningTime", Constants.NBT.TAG_INT))         { info.setBorderWarningTime(nbt.getInteger("BorderWarningTime")); }

        if (nbt.hasKey("SpawnX", Constants.NBT.TAG_INT) &&
            nbt.hasKey("SpawnY", Constants.NBT.TAG_INT) &&
            nbt.hasKey("SpawnZ", Constants.NBT.TAG_INT))
        {
            info.setSpawn(new BlockPos(nbt.getInteger("SpawnX"), nbt.getInteger("SpawnY"), nbt.getInteger("SpawnZ")));
        }

        if (nbt.hasKey("GameRules", Constants.NBT.TAG_COMPOUND))
        {
            info.getGameRulesInstance().readFromNBT(nbt.getCompoundTag("GameRules"));
        }
    }

    private static WorldType getWorldType(NBTTagCompound nbt)
    {
        String name = nbt.getString("generatorName");
        WorldType worldType = WorldType.parseWorldType(name);

        if (worldType == null)
        {
            worldType = WorldType.DEFAULT;
        }
        else if (worldType.isVersioned())
        {
            int version = 0;

            if (nbt.hasKey("generatorVersion", Constants.NBT.TAG_ANY_NUMERIC))
            {
                version = nbt.getInteger("generatorVersion");
            }

            worldType = worldType.getWorldTypeForGeneratorVersion(version);
        }

        return worldType;
    }
}
