package fi.dy.masa.justenoughdimensions.util.world;

import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
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
            field_worldInfo = ObfuscationReflectionHelper.findField(World.class, "field_72986_A"); // worldInfo
        }
        catch (Exception e)
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
        if ((world.provider instanceof IWorldProviderJED) && ((IWorldProviderJED) world.provider).getWorldInfoHasBeenSet())
        {
            return;
        }

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
            // We might be running under Sponge here or this may be for
            // the overworld (when applying WorldInfo _value_ overrides),
            // so instead of overriding the WorldInfo instance, try to apply
            // the configured properties to the existing WorldInfo.
            else if ((world.getWorldInfo() instanceof WorldInfoJED) == false)
            {
                JustEnoughDimensions.logInfo("WorldInfoUtils.loadAndSetCustomWorldInfo(): Applying WorldInfo properties " +
                        "to an existing WorldInfo instance of type '{}' in dimension {}", world.getWorldInfo().getClass().getName(), dimension);

                // In case of the overworld, the generator setting changes need to be "detected" here,
                // because normally they are detected by comparing to the overworld's values.
                boolean generatorChanged = applyValuesToExistingWorldInfo(world, dimension);
                applyChangesFromNewWorldInfo(world, generatorChanged);
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
    public static NBTTagCompound getWorldInfoTag(World world, int dimension, boolean readFromDisk, boolean inheritFromOverworld)
    {
        NBTTagCompound nbt = readFromDisk ? WorldFileUtils.loadWorldInfoFromFile(world, WorldFileUtils.getWorldDirectory(world)) : null;
        final boolean isDimensionInit = readFromDisk ? nbt == null : WorldFileUtils.jedLevelFileExists(world) == false;

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

    private static void setWorldInfo(World world, WorldInfoJED info)
    {
        if (field_worldInfo != null)
        {
            try
            {
                field_worldInfo.set(world, info);
                // This setWorldInfo() method is NOT called for the overworld, thus the generator changes
                // compared to the overworld can be detected without the force option in this case.
                applyChangesFromNewWorldInfo(world, false);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("WorldInfoUtils.setWorldInfo(): Failed to override WorldInfo for dimension {}",
                        world.provider.getDimension());
            }
        }
    }

    private static void applyChangesFromNewWorldInfo(World world, boolean generatorChanged)
    {
        // This sets the WorldType (the terrainType field) and the generatorSettings field,
        // and creates the BiomeProvider based on the world seed from the newly overridden or updated WorldInfo
        world.provider.setWorld(world);

        WorldBorderUtils.setWorldBorderValues(world);

        // Override/re-create the ChunkProvider when using overridden WorldInfo,
        // and NOT using one of the JED WorldProviders.
        // Otherwise the ChunkProvider would be using the settings from the overworld, because
        // WorldEvent.Load (where the WorldInfo gets overridden) only happens after
        // the world has been constructed and the ChunkProvider set.

        // However, for JED WorldProviders the WorldInfo, and more importantly,
        // the terrainType, generatorSettings and the world seed in the WorldProvider get set/overridden earlier
        // than WorldEvent.Load, via the WorldProvider#setDimension() method, which gets called from
        // the WorldServer constructor. Therefore the ChunkGenerator for JED WorldProviders
        // already has the correct settings available to it when it gets created in the first place.
        if ((world.provider instanceof IWorldProviderJED) == false)
        {
            WorldUtils.reCreateChunkGenerator(world, generatorChanged);
        }
    }

    /**
     * Apply the settings from the "worldinfo" objects in the dimension config to an existing WorldInfo instance.
     * This is mainly meant for compatibility with Sponge or other mods that have and need their own versions of WorldInfo.
     * @param world
     * @param dimension
     * @return true if the generatorName or generatorOptions changed, and the ChunkGenerator needs to be re-created
     */
    private static boolean applyValuesToExistingWorldInfo(World world, int dimension)
    {
        // Get just the values from the config
        NBTTagCompound nbt = getWorldInfoTag(world, dimension, false, false);
        WorldInfo info = world.getWorldInfo();
        final boolean hasGeneratorOptions = nbt.hasKey("generatorOptions", Constants.NBT.TAG_STRING);
        final boolean hasSeed = nbt.hasKey("RandomSeed", Constants.NBT.TAG_LONG);
        boolean generatorChanged = hasGeneratorOptions || hasSeed;

        // These don't have setters, so they need to be smuggled in via WorldSettings
        if (hasSeed || hasGeneratorOptions)
        {
            final long seed = hasSeed ? nbt.getLong("RandomSeed") : info.getSeed();
            GameType gameType = nbt.hasKey("GameType", Constants.NBT.TAG_INT) ? GameType.getByID(nbt.getInteger("GameType")) : info.getGameType();
            boolean mapFeatures = nbt.hasKey("MapFeatures", Constants.NBT.TAG_BYTE) ? nbt.getBoolean("MapFeatures") : info.isMapFeaturesEnabled();
            boolean hardcore = nbt.hasKey("hardcore", Constants.NBT.TAG_BYTE) ? nbt.getBoolean("hardcore") : info.isHardcoreModeEnabled();
            WorldType worldType = nbt.hasKey("generatorName", Constants.NBT.TAG_STRING) ? getWorldType(nbt) : info.getTerrainType();

            WorldSettings settings = new WorldSettings(seed, gameType, mapFeatures, hardcore, worldType);
            String generatorOptions = hasGeneratorOptions ? nbt.getString("generatorOptions") : info.getGeneratorOptions();
            settings.setGeneratorOptions(generatorOptions);

            // We need to cache and restore this, the populateFromWorldSettings() call will reset it to false
            boolean allowCommands = info.areCommandsAllowed();
            info.populateFromWorldSettings(settings);
            info.setAllowCommands(allowCommands);
        }

        if (nbt.hasKey("generatorName", Constants.NBT.TAG_STRING) && (hasGeneratorOptions == false && hasSeed == false))
        {
            WorldType worldTypeOld = info.getTerrainType();
            WorldType worldTypeNew = getWorldType(nbt);

            if (worldTypeNew.equals(worldTypeOld) == false)
            {
                info.setTerrainType(worldTypeNew);
                generatorChanged = true;
            }
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
        if (nbt.hasKey("Difficulty", Constants.NBT.TAG_BYTE))       { info.setDifficulty(EnumDifficulty.byId(nbt.getByte("Difficulty"))); }
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

        return generatorChanged;
    }

    private static WorldType getWorldType(NBTTagCompound nbt)
    {
        String name = nbt.getString("generatorName");
        WorldType worldType = WorldType.byName(name);

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
