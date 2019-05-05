package fi.dy.masa.justenoughdimensions.util.world;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGeneratorBonusChest;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import fi.dy.masa.justenoughdimensions.config.DimensionTypeEntry;
import fi.dy.masa.justenoughdimensions.config.StructurePlacement;
import fi.dy.masa.justenoughdimensions.config.StructurePlacement.StructureType;
import fi.dy.masa.justenoughdimensions.event.DataTracker;
import fi.dy.masa.justenoughdimensions.network.MessageSyncWorldProperties;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.util.SpawnPointSearch;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;

public class WorldUtils
{
    private static final String JED_RESPAWN_DIM_TAG = "justenoughdimensions:respawndimension";
    //private static Field field_WorldProvider_terrainType;
    //private static Field field_WorldProvider_generatorSettings;
    private static Field field_World_provider = null;
    private static Field field_WorldProvider_biomeProvider = null;
    private static Field field_ChunkProviderServer_chunkGenerator = null;

    static
    {
        try
        {
            //field_WorldProvider_terrainType = ReflectionHelper.findField(WorldProvider.class, "field_76577_b", "terrainType");
            //field_WorldProvider_generatorSettings = ReflectionHelper.findField(WorldProvider.class, "field_82913_c", "generatorSettings");
            field_World_provider                     = ObfuscationReflectionHelper.findField(World.class, "field_73011_w"); // provider
            field_WorldProvider_biomeProvider        = ObfuscationReflectionHelper.findField(WorldProvider.class, "field_76578_c"); // biomeProvider
            field_ChunkProviderServer_chunkGenerator = ObfuscationReflectionHelper.findField(ChunkProviderServer.class, "field_186029_c"); // chunkGenerator
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.error("WorldUtils: Reflection failed!!", e);
        }
    }

    public static BlockPos getWorldSpawn(WorldServer world)
    {
        BlockPos spawn = world.getSpawnCoordinate();

        // Only the End has a spawn coordinate
        if (spawn == null)
        {
            spawn = world.getSpawnPoint();
        }

        return spawn;
    }

    public static int getLoadedChunkCount(WorldServer world)
    {
        return world.getChunkProvider().getLoadedChunkCount();
    }

    /**
     * Unloads all empty dimensions (with no chunks loaded)
     * @param tryUnloadChunks if true, then tries to first save and unload all non-player-loaded and non-force-loaded chunks
     * @return the number of dimensions successfully unloaded
     */
    public static int unloadEmptyDimensions(boolean tryUnloadChunks)
    {
        int count = 0;
        Integer[] dims = DimensionManager.getIDs();

        JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Trying to unload empty dimensions, tryUnloadChunks = {}", tryUnloadChunks);

        for (int dim : dims)
        {
            JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Trying to unload dimension {}", dim);
            WorldServer world = DimensionManager.getWorld(dim);

            if (world == null)
            {
                continue;
            }

            ChunkProviderServer chunkProviderServer = world.getChunkProvider();
            int loadedCountBefore = chunkProviderServer.getLoadedChunkCount();

            if (tryUnloadChunks && loadedCountBefore > 0)
            {
                JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Trying to unload chunks for " +
                                             "dimension {}, currently loaded chunks: {}", dim, loadedCountBefore);
                boolean disable = world.disableLevelSaving;
                world.disableLevelSaving = false;

                try
                {
                    // This also tries to unload all chunks that are not loaded by players
                    world.saveAllChunks(true, (IProgressUpdate) null);
                }
                catch (MinecraftException e)
                {
                    JustEnoughDimensions.logger.warn("WorldUtils.unloadEmptyDimensions(): Exception while "+
                                                     "trying to save chunks for dimension {}", dim, e);
                }

                // This would flush the chunks to disk from the AnvilChunkLoader. Probably not what we want to do.
                //world.saveChunkData();

                world.disableLevelSaving = disable;

                // This will unload the dimension, if it unloaded at least one chunk, and it has no loaded chunks anymore
                chunkProviderServer.tick();

                int loadedCountAfter = chunkProviderServer.getLoadedChunkCount();
                JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Unloaded {} chunks in dimension {}", loadedCountBefore - loadedCountAfter, dim);

                if (loadedCountAfter == 0)
                {
                    JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Likely unloaded dimension {}", dim);
                    count++;
                }
            }
            else if (chunkProviderServer.getLoadedChunkCount() == 0 &&
                world.provider.getDimensionType().shouldLoadSpawn() == false &&
                ForgeChunkManager.getPersistentChunksFor(world).size() == 0)
            {
                JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Unloading dimension {}", dim);
                DimensionManager.unloadWorld(world.provider.getDimension());
                count++;
            }
        }

        return count;
    }

    public static boolean removeTemporaryWorldIfApplicable(World world)
    {
        final int dimension = world.provider.getDimension();
        File worldDir = WorldFileUtils.getWorldDirectory(world);
        return removeTemporaryWorldIfApplicable(dimension, world, worldDir, false);
    }

    public static boolean removeTemporaryWorldIfApplicable(int dimension, @Nullable World world, File worldDir, boolean isServerStop)
    {
        DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);

        if (entry != null && entry.isTemporaryDimension() &&
            (dimension != 0 || isServerStop) &&
            worldDir != null && worldDir.exists() &&
            DataTracker.getInstance().getPlayerCountInDimension(dimension) == 0)
        {
            File jedDataDir = WorldFileUtils.getWorldJEDDataDirectory(worldDir);
            File markerFile = WorldFileUtils.getTemporaryDimensionMarkerFile(jedDataDir);

            if (markerFile.exists())
            {
                if (dimension == 0)
                {
                    JustEnoughDimensions.logInfo("Trying to remove a temporary world '{}'", worldDir.getAbsolutePath());
                }
                else
                {
                    JustEnoughDimensions.logInfo("Trying to remove a temporary dimension (DIM {}) from '{}'",
                            dimension, worldDir.getAbsolutePath());
                }

                try
                {
                    if (world != null && (world instanceof WorldServer))
                    {
                        ((WorldServer) world).flush();
                    }

                    FileUtils.deleteDirectory(worldDir);

                    return true;
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.warn("Exception while trying to remove a temporary dimension {}", dimension, e);
                }
            }
        }

        return false;
    }

    public static boolean tryDeleteDimension(int dimension, ICommandSender sender)
    {
        if (dimension != 0)
        {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            World world = server != null ? server.getWorld(dimension) : null;

            if (world == null)
            {
                JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Could not load dimension {} (to get the directory)", dimension);
                return false;
            }

            if (world.playerEntities.size() > 0)
            {
                JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: There are currently players in dimension {}, can't delete it", dimension);
                return false;
            }

            File dir = WorldFileUtils.getWorldDirectory(world);

            if (dir.exists() == false || dir.isDirectory() == false)
            {
                JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Failed to get the directory for dimension {}", dimension);
                return false;
            }

            if (world instanceof WorldServer)
            {
                WorldServer worldServer = (WorldServer) world;

                try
                {
                    worldServer.getChunkProvider().queueUnloadAll();
                    worldServer.saveAllChunks(true, null);
                    worldServer.flush();

                    DimensionManager.setWorld(dimension, null, worldServer.getMinecraftServer());
                    FileUtils.deleteDirectory(dir);
                    CommandJED.runBroadcastCommand(sender, "delete-dimension", Integer.valueOf(dimension));

                    JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Successfully deleted dimension {}", dimension);

                    return true;
                }
                catch (MinecraftException e)
                {
                    JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Exception while trying to unload dimension {}", dimension, e);
                }
                catch (IOException e)
                {
                    JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Exception while trying to delete the directory of dimension {}", dimension, e);
                }
            }
            else
            {
                JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Not a server world?!");
                return false;
            }
        }
        else
        {
            JustEnoughDimensions.logger.warn("WorldUtils.tryDeleteDimension: Can't delete dimension 0, it would delete the entire save");
        }

        return false;
    }

    public static void syncWorldProviderProperties(EntityPlayer player)
    {
        if (player instanceof EntityPlayerMP)
        {
            JustEnoughDimensions.logInfo("WorldUtils.syncWorldProviderProperties: Syncing WorldProvider properties " +
                                         "of dimension {} to player '{}'", player.getEntityWorld().provider.getDimension(), player.getName());
            PacketHandler.INSTANCE.sendTo(new MessageSyncWorldProperties(player.getEntityWorld()), (EntityPlayerMP) player);
        }
    }

    /*
    public static void overrideWorldProviderSettings(World world, WorldProvider provider)
    {
        WorldInfo info = world.getWorldInfo();

        if (info instanceof WorldInfoJED)
        {
            try
            {
                JustEnoughDimensions.logInfo("WorldUtils.overrideWorldProviderSettings(): Trying to override the "+
                                             "WorldType and generatorSettings for dimension {}", provider.getDimension());
                field_WorldProvider_terrainType.set(provider, info.getTerrainType());
                field_WorldProvider_generatorSettings.set(provider, info.getGeneratorOptions());
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("WorldUtils.overrideWorldProviderSettings(): Failed to override " +
                                                  "WorldProvider settings for dimension {}", provider.getDimension());
            }
        }
    }
    */

    public static void overrideWorldProviderIfApplicable(World world)
    {
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(world);

        if (props != null && props.overrideWorldProvider())
        {
            String newClassName = props.getWorldProviderOverrideClassName();
            Class<? extends WorldProvider> newProviderClass = DimensionTypeEntry.getProviderClass(newClassName);

            if (newProviderClass != null && newProviderClass != world.provider.getClass())
            {
                final int dim = world.provider.getDimension();
                String oldName = world.provider.getClass().getName();
                JustEnoughDimensions.logInfo("WorldUtils.overrideWorldProvider: Trying to override the WorldProvider of type '{}' in dimension {} with '{}'", oldName, dim, newClassName);

                try
                {
                    Constructor <? extends WorldProvider> constructor = newProviderClass.getConstructor();
                    WorldProvider newProvider = constructor.newInstance();

                    try
                    {
                        field_World_provider.set(world, newProvider);
                        world.provider.setWorld(world);
                        world.provider.setDimension(dim);

                        JustEnoughDimensions.logInfo("WorldUtils.overrideWorldProvider: Overrode the WorldProvider in dimension {} with '{}'", dim, newClassName);

                        reCreateChunkGenerator(world, dim == 0);
                    }
                    catch (Exception e)
                    {
                        JustEnoughDimensions.logger.error("WorldUtils.overrideWorldProvider: Failed to override the WorldProvider of dimension {}", dim);
                    }

                    return;
                }
                catch (Exception e)
                {
                }
            }

            JustEnoughDimensions.logger.warn("WorldUtils.overrideWorldProvider: Failed to create a WorldProvider from name '{}', or it was already that type", newClassName);
        }
    }

    public static void overrideBiomeProvider(World world)
    {
        // For WorldProviderSurfaceJED the BiomeProvider has already been set in WorldProviderSurfaceJED#init()
        if ((world.provider instanceof WorldProviderSurfaceJED) == false)
        {
            int dimension = world.provider.getDimension();
            DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);

            if (entry != null)
            {
                BiomeProvider biomeProvider = null;

                if (entry.getBiome() != null)
                {
                    Biome biome = Biome.REGISTRY.getObject(new ResourceLocation(entry.getBiome()));

                    if (biome != null && ((world.provider.getBiomeProvider() instanceof BiomeProviderSingle) == false ||
                        world.provider.getBiomeProvider().getBiome(BlockPos.ORIGIN) != biome))
                    {
                        biomeProvider = new BiomeProviderSingle(biome);

                        JustEnoughDimensions.logInfo("WorldUtils.overrideBiomeProvider: Overriding the BiomeProvider of dimension {} with '{}' using the biome '{}'",
                                dimension, biomeProvider.getClass().getName(), entry.getBiome());
                    }
                }
                else if (entry.getBiomeProvider() != null)
                {
                    JustEnoughDimensions.logInfo("WorldUtils.overrideBiomeProvider: Trying to create a BiomeProvider for dimension {} from the class name '{}'",
                            dimension, entry.getBiomeProvider());
                    biomeProvider = createBiomeProviderForName(entry.getBiomeProvider(), world);
                }
                else if (entry.shouldUseNormalBiomes() && world.provider.getBiomeProvider() instanceof BiomeProviderSingle)
                {
                    biomeProvider = new BiomeProvider(world.getWorldInfo());

                    JustEnoughDimensions.logInfo("WorldUtils.overrideBiomeProvider: Overriding the BiomeProvider of dimension {} with '{}'",
                            dimension, biomeProvider.getClass().getName());
                }

                if (biomeProvider != null)
                {
                    try
                    {
                        field_WorldProvider_biomeProvider.set(world.provider, biomeProvider);
                    }
                    catch (Exception e)
                    {
                        JustEnoughDimensions.logger.error("Failed to override the BiomeProvider of dimension {}", dimension);
                    }
                }
            }
        }
    }

    @Nullable
    public static BiomeProvider createBiomeProviderForName(String name, World world)
    {
        try
        {
            @SuppressWarnings("unchecked")
            Class<? extends BiomeProvider> clazz = (Class<? extends BiomeProvider>) Class.forName(name);
            BiomeProvider provider = clazz.getConstructor(WorldInfo.class).newInstance(world.getWorldInfo());

            try
            {
                // This is for Painted Biomes compatibility as of PB 0.6.0
                Method method = clazz.getDeclaredMethod("init", World.class);
                method.invoke(provider, world);
            }
            catch (Exception e)
            {
                // ignore
            }

            return provider;
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("WorldUtils.createBiomeProviderForName(): Failed to create a BiomeProvider from class name '{}' ({})", name, e.getClass().getName());
        }

        return null;
    }

    public static void reCreateChunkGenerator(World world, boolean generatorChangedForOverworld)
    {
        if (world instanceof WorldServer && world.getChunkProvider() instanceof ChunkProviderServer)
        {
            final int dimension = world.provider.getDimension();
            WorldInfo info = world.getWorldInfo();
            World overworld = DimensionManager.getWorld(0);

            if (dimension == 0 && generatorChangedForOverworld == false)
            {
                JustEnoughDimensions.logInfo("No need to re-create the ChunkProvider in dimension {}", dimension);
                return;
            }
            else if (dimension != 0 && overworld != null)
            {
                WorldInfo infoOverworld = overworld.getWorldInfo();

                if (infoOverworld.getTerrainType() == info.getTerrainType() &&
                    infoOverworld.isMapFeaturesEnabled() == info.isMapFeaturesEnabled() &&
                    infoOverworld.getGeneratorOptions().equals(info.getGeneratorOptions()) &&
                    infoOverworld.getSeed() == info.getSeed())
                {
                    JustEnoughDimensions.logInfo("No need to re-create the ChunkProvider in dimension {}", dimension);
                    return;
                }
            }

            // This sets the new WorldType, generatorOptions and creates the BiomeProvider based on the seed for the WorldProvider
            world.provider.setWorld(world);

            ChunkProviderServer chunkProviderServer = (ChunkProviderServer) world.getChunkProvider();
            IChunkGenerator newChunkGenerator = world.provider.createChunkGenerator();

            if (newChunkGenerator == null)
            {
                JustEnoughDimensions.logger.warn("Failed to re-create the ChunkProvider for dimension {}", dimension);
                return;
            }

            try
            {
                field_ChunkProviderServer_chunkGenerator.set(chunkProviderServer, newChunkGenerator);

                JustEnoughDimensions.logInfo("WorldUtils.reCreateChunkProvider: Re-created/overwrote the ChunkProvider " +
                                             "(of type '{}') in dimension {} with '{}'",
                        chunkProviderServer.chunkGenerator.getClass().getName(), dimension, newChunkGenerator.getClass().getName());
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to re-create the ChunkProvider for dimension {} with {}",
                        dimension, newChunkGenerator.getClass().getName(), e);
            }
        }
    }

    public static void centerWorldBorderIfApplicable(World world)
    {
        int dimension = world.provider.getDimension();
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(dimension);
        WorldBorder border = world.getWorldBorder();
        BlockPos spawn = world.getSpawnPoint();

        if (props != null && props.shouldWorldBorderBeCenteredOnSpawn() &&
            (spawn.getX() != border.getCenterX() || spawn.getZ() != border.getCenterZ()))
        {
            border.setCenter(spawn.getX(), spawn.getZ());
            JustEnoughDimensions.logInfo("WorldUtils.centerWorldBorderIfApplicable: Moved the WorldBorder of dimension {} to the spawn point @ {}", dimension, spawn);
        }
    }

    public static void findAndSetWorldSpawnIfApplicable(World world)
    {
        if (WorldFileUtils.jedLevelFileExists(world) == false)
        {
            findAndSetWorldSpawn(world);
        }
    }

    private static void findAndSetWorldSpawn(World world)
    {
        final int dimension = world.provider.getDimension();
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(dimension);
        SpawnPointSearch searchType = props != null ? props.getSpawnPointSearchType() : null;
        NBTTagCompound nbt = WorldInfoUtils.getWorldInfoTag(world, dimension, false, false);
        @Nullable BlockPos newSpawn = null;

        if (nbt.hasKey("SpawnX") && nbt.hasKey("SpawnZ"))
        {
            if (nbt.hasKey("SpawnY"))
            {
                newSpawn = new BlockPos(nbt.getInteger("SpawnX"), nbt.getInteger("SpawnY"), nbt.getInteger("SpawnZ"));
                JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: An exact spawn point {} defined in the " +
                                             "dimension config for dimension {}, skipping the search", newSpawn, dimension);
                generateFallbackSpawnBlockIfEnabled(world, newSpawn);
            }
            else
            {
                newSpawn = new BlockPos(nbt.getInteger("SpawnX"), 72, nbt.getInteger("SpawnZ"));
                JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: A spawn point XZ-location 'x = {}, z = {}' defined in the " +
                                             "dimension config for dimension {}, searching for a suitable y-location",
                                             newSpawn.getX(), newSpawn.getZ(), dimension);
                newSpawn = getSuitableSpawnBlockInColumn(world, newSpawn, true, true);
            }
        }
        else if ((dimension != 0 && Configs.enableSeparateWorldInfo &&
                  DimensionConfig.instance().useCustomWorldInfoFor(dimension)) ||
                 (searchType != null && searchType.getType() != SpawnPointSearch.Type.NONE))
        {
            JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Trying to find a world spawn for dimension {}...", dimension);
            newSpawn = findSuitableSpawnpoint(world, searchType);
        }

        if (newSpawn != null)
        {
            if (world.getSpawnPoint().equals(newSpawn) == false)
            {
                world.setSpawnPoint(newSpawn);
                JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Set the world spawnpoint of dimension {} to {}", dimension, newSpawn);
            }

            WorldBorder border = world.getWorldBorder();

            if (border.contains(newSpawn) == false)
            {
                border.setCenter(newSpawn.getX(), newSpawn.getZ());
                JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Moved the WorldBorder of dimension {} " +
                                             "to the world's spawn, because the spawn was outside the border", dimension);
            }
        }
    }

    @Nonnull
    public static BlockPos findSuitableSpawnpoint(World world)
    {
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(world.provider.getDimension());
        SpawnPointSearch searchType = props != null ? props.getSpawnPointSearchType() : null;

        return findSuitableSpawnpoint(world, searchType);
    }

    @Nonnull
    public static BlockPos findSuitableSpawnpoint(World world, @Nullable SpawnPointSearch searchType)
    {
        WorldProvider provider = world.provider;
        BlockPos pos;

        if (searchType != null)
        {
            JustEnoughDimensions.logInfo("WordlUtils.findSuitableSpawnpoint: Using a customized spawn point search type '{}' for DIM {}",
                    searchType.toString(), provider.getDimension());

            switch (searchType.getType())
            {
                case OVERWORLD:
                    return findOverworldSpawnpoint(world, searchType);
                case CAVERN:
                    return findCavernSpawnpoint(world, searchType);
                case NONE:
                    JustEnoughDimensions.logInfo("WorldUtils.findSuitableSpawnpoint: SpawnPointSearch.Type == NONE, using the existing spawn point in DIM {}", provider.getDimension());
                    return world.getSpawnPoint();
            }
        }

        // Likely end type dimensions
        if (provider.getDimensionType() == DimensionType.THE_END ||
            provider instanceof WorldProviderEnd)
        {
            pos = provider.getSpawnCoordinate();

            if (pos == null)
            {
                pos = getSuitableSpawnBlockInColumn(world, new BlockPos(0, 72, 0), true, true);
            }
        }
        // Likely nether type dimensions
        else if (provider.getDimensionType() == DimensionType.NETHER ||
                 provider.isNether() ||
                 provider instanceof WorldProviderHell ||
                 provider instanceof WorldProviderHellJED)
        {
            pos = findCavernSpawnpoint(world, searchType);
        }
        else if (world.getWorldInfo().getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES)
        {
            pos = BlockPos.ORIGIN.up(64);
        }
        // Mostly overworld type dimensions
        else
        {
            pos = findOverworldSpawnpoint(world, searchType);
        }

        generateFallbackSpawnBlockIfEnabled(world, pos);

        return pos;
    }

    @Nonnull
    private static BlockPos findCavernSpawnpoint(World world, @Nullable SpawnPointSearch searchType)
    {
        @Nullable final Integer yRangeMax = searchType != null ? searchType.getMaxY() : null;
        final int minY = searchType != null && searchType.getMinY() != null ? Math.max(1, searchType.getMinY()) : 30;
        Random random = new Random(world.getSeed());
        int x = 0;
        int z = 0;
        int iterations = 0;

        while (iterations < 200)
        {
            Chunk chunk = world.getChunk(x >> 4, z >> 4);
            int maxY = 120;

            if (yRangeMax != null)
            {
                maxY = yRangeMax.intValue();
            }

            BlockPos pos = new BlockPos(x, maxY, z);

            while (pos.getY() >= minY)
            {
                IBlockState stateBelow = chunk.getBlockState(pos.down(2));
                IBlockState state1 = chunk.getBlockState(pos.down(1));
                IBlockState state2 = chunk.getBlockState(pos);

                if (state1.getBlock().isAir(state1, world, pos) &&
                    state2.getBlock().isAir(state2, world, pos) &&
                    stateBelow.getMaterial().blocksMovement())
                {
                    return pos.down();
                }

                pos = pos.down();
            }

            x += random.nextInt(32) - random.nextInt(32);
            z += random.nextInt(32) - random.nextInt(32);
            iterations++;
        }

        BlockPos pos = new BlockPos(0, 72, 0);
        JustEnoughDimensions.logger.warn("Unable to find a cavern type spawn point for dimension {}, defaulting to x = {}, y = {}, z = {}",
                world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ());

        generateFallbackSpawnBlockIfEnabled(world, pos);

        return pos;
    }

    @Nonnull
    private static BlockPos findOverworldSpawnpoint(World world, @Nullable SpawnPointSearch searchType)
    {
        WorldProvider provider = world.provider;
        BiomeProvider biomeProvider = provider.getBiomeProvider();
        List<Biome> list = biomeProvider.getBiomesToSpawnIn();
        Random random = new Random(world.getSeed());
        int x = 8;
        int z = 8;
        final int minY = searchType != null && searchType.getMinY() != null ? Math.max(1, searchType.getMinY()) : 1;
        @Nullable final Integer yRangeMax = searchType != null ? searchType.getMaxY() : null;

        // This will not generate chunks, but only check the biome ID from the genBiomes.getInts() output
        BlockPos pos = biomeProvider.findBiomePosition(0, 0, 512, list, random);

        if (pos != null)
        {
            x = pos.getX();
            z = pos.getZ();
        }
        else
        {
            JustEnoughDimensions.logger.warn("Unable to find spawn biome for dimension {}", provider.getDimension());
        }

        int iterations = 0;

        // Note: This will generate chunks! Also note that the returned position might
        // still end up inside a tree or something, since decoration hasn't necessarily been done yet.
        while (iterations < 1000)
        {
            if (provider.canCoordinateBeSpawn(x, z))
            {
                Chunk chunk = world.getChunk(x >> 4, z >> 4);
                int maxY = chunk.getTopFilledSegment() + 15 + 1;

                if (yRangeMax != null)
                {
                    maxY = Math.min(yRangeMax.intValue(), maxY);
                }

                pos = new BlockPos(x, maxY, z);

                while (pos.getY() >= minY)
                {
                    if (isSuitableSpawnPosition(world, chunk, pos, false))
                    {
                        return pos;
                    }

                    pos = pos.down();
                }
            }

            x += random.nextInt(32) - random.nextInt(32);
            z += random.nextInt(32) - random.nextInt(32);

            iterations++;
        }

        pos = new BlockPos(x, 72, z);

        return getSuitableSpawnBlockInColumn(world, pos, true, true);
    }

    /**
     * Tries to find a suitable spawn position in the given XZ-column. If none are found, then the
     * original input position is returned.
     * @param world
     * @param originalPos
     * @param leanient if true, then leaves and foliage are allowed for the block below,
     * and fluids are allowed for the two blocks at the spawn location
     * @return
     */
    @Nonnull
    public static BlockPos getSuitableSpawnBlockInColumn(World world, BlockPos originalPos, boolean leanient)
    {
        return getSuitableSpawnBlockInColumn(world, originalPos, leanient, false);
    }

    @Nonnull
    public static BlockPos getSuitableSpawnBlockInColumn(World world, BlockPos originalPos, boolean leanient, boolean generateFallbackBlock)
    {
        Chunk chunk = world.getChunk(originalPos);
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(world.provider.getDimension());
        SpawnPointSearch searchType = props != null ? props.getSpawnPointSearchType() : null;
        @Nullable final Integer yRangeMax = searchType != null ? searchType.getMaxY() : null;
        final int minY = searchType != null && searchType.getMinY() != null ? Math.max(1, searchType.getMinY()) : 1;
        int maxY = chunk.getTopFilledSegment() + 15 + 1;

        if (yRangeMax != null)
        {
            maxY = MathHelper.clamp(maxY, minY, yRangeMax.intValue());
        }

        BlockPos posTop = new BlockPos(originalPos.getX(), maxY, originalPos.getZ());
        BlockPos pos = posTop;

        while (pos.getY() >= minY)
        {
            if (isSuitableSpawnPosition(world, chunk, pos, leanient))
            {
                return pos;
            }

            pos = pos.down();
        }

        if (generateFallbackBlock)
        {
            generateFallbackSpawnBlockIfEnabled(world, originalPos);
        }

        return originalPos;
    }

    /**
     * Check if the given position is suitable for the spawn point.
     * @param world
     * @param chunk
     * @param pos
     * @param leanient if true, then leaves and foliage are allowed for the block below,
     * and fluids are allowed for the two blocks at the spawn location
     * @return
     */
    private static boolean isSuitableSpawnPosition(World world, Chunk chunk, BlockPos pos, boolean leanient)
    {
        IBlockState state =    chunk.getBlockState(pos.down(1));
        Material materialUp1 = chunk.getBlockState(pos).getMaterial();
        Material materialUp2 = chunk.getBlockState(pos.up(1)).getMaterial();

        return state.getMaterial().blocksMovement() &&
               (leanient || state.getBlock().isLeaves(state, world, pos) == false) &&
               (leanient || state.getBlock().isFoliage(world, pos) == false) &&
               materialUp1.blocksMovement() == false && (leanient || materialUp1.isLiquid() == false) &&
               materialUp2.blocksMovement() == false && (leanient || materialUp2.isLiquid() == false);
    }

    private static void generateFallbackSpawnBlockIfEnabled(World world, BlockPos spawnPos)
    {
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(world.provider.getDimension());

        // Fallback - avoid falling to the void
        if (props != null && props.generateFallbackSpawnBlock() && world.isAirBlock(spawnPos.down()))
        {
            int dim = world.provider.getDimension();
            JustEnoughDimensions.logInfo("Didn't find suitable spawn location in dim {}, generating a glass block under the spawn point", dim);
            world.setBlockState(spawnPos.down(), Blocks.GLASS.getDefaultState());
        }
    }

    public static void createBonusChest(World world)
    {
        WorldInfo info = world.getWorldInfo();
        WorldGeneratorBonusChest gen = new WorldGeneratorBonusChest();

        for (int i = 0; i < 10; ++i)
        {
            int x = info.getSpawnX() + world.rand.nextInt(6) - world.rand.nextInt(6);
            int z = info.getSpawnZ() + world.rand.nextInt(6) - world.rand.nextInt(6);
            BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).up();

            if (gen.generate(world, world.rand, pos))
            {
                break;
            }
        }
    }

    private static void loadChunks(World world, int chunkX1, int chunkZ1, int chunkX2, int chunkZ2)
    {
        final int xStart = Math.min(chunkX1, chunkX2);
        final int zStart = Math.min(chunkZ1, chunkZ2);
        final int xEnd = Math.max(chunkX1, chunkX2);
        final int zEnd = Math.max(chunkZ1, chunkZ2);
        final int dimension = world.provider.getDimension();

        JustEnoughDimensions.logInfo("Attempting to load chunks [{},{}] to [{},{}] in dimension {}", xStart, zStart, xEnd, zEnd, dimension);

        for (int x = xStart; x <= xEnd; x++)
        {
            for (int z = zStart; z <= zEnd; z++)
            {
                if (world.isBlockLoaded(new BlockPos(x << 4, 0, z << 4)) == false)
                {
                    //JustEnoughDimensions.logInfo("Loading chunk [{},{}] in dimension {}", x, z, dimension);
                    world.getChunk(x, z);
                }
            }
        }
    }

    private static void loadChunks(World world, BlockPos pos, BlockPos size, int expand)
    {
        int cx1 = (pos.getX() - expand) >> 4;
        int cz1 = (pos.getZ() - expand) >> 4;
        int cx2 = (pos.getX() + size.getX() + expand) >> 4;
        int cz2 = (pos.getZ() + size.getZ() + expand) >> 4;

        WorldUtils.loadChunks(world, cx1, cz1, cx2, cz2);
    }

    public static void placeSpawnStructureIfApplicable(World world)
    {
        final int dimension = world.provider.getDimension();
        final boolean isDimensionInit = WorldFileUtils.jedLevelFileExists(world) == false;
        DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);

        if (isDimensionInit && entry != null)
        {
            JsonObject spawnStructureJson = entry.getSpawnStructureJson();

            if (spawnStructureJson != null)
            {
                StructurePlacement placement = StructurePlacement.fromJson(spawnStructureJson);

                if (placement != null)
                {
                    MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                    StructureType type = StructureType.fromFileName(placement.getFile().getName());
                    BlockPos pos = world.getSpawnPoint().add(placement.getOffset());
                    boolean success = false;

                    if (type == StructureType.STRUCTURE)
                    {
                        JustEnoughDimensions.logInfo("WorldUtils.placeSpawnStructure: Trying to place the spawn structure at {}", pos);
                        success = tryPlaceVanillaStructure(server, world, pos, placement);
                    }
                    else if (type == StructureType.SCHEMATIC)
                    {
                        JustEnoughDimensions.logInfo("WorldUtils.placeSpawnStructure: Trying to place the spawn schematic at {}", pos);
                        success = tryPlaceSchematic(server, world, pos, placement);
                    }
                    else if (type == StructureType.INVALID)
                    {
                        JustEnoughDimensions.logger.warn("WorldUtils.placeSpawnStructure: Invalid structure type '{}'", placement.getFile().getAbsolutePath());
                    }

                    if (success)
                    {
                        JustEnoughDimensions.logInfo("WorldUtils.placeSpawnStructure: Successfully placed the spawn structure in dimension {}", dimension);
                    }
                    else
                    {
                        JustEnoughDimensions.logger.warn("WorldUtils.placeSpawnStructure: Failed to place the spawn structure in dimension {}", dimension);
                    }
                }
                else
                {
                    JustEnoughDimensions.logger.warn("WorldUtils.placeSpawnStructure: Spawn structure defined but failed to load in dimension {}", dimension);
                }
            }
        }
    }

    private static boolean tryPlaceVanillaStructure(MinecraftServer server, World world, BlockPos pos, StructurePlacement placement)
    {
        File file = placement.getFile();

        if (file.exists() == false)
        {
            return false;
        }

        String name = file.getName();
        name = name.substring(0, name.lastIndexOf("."));
        Template template = getTemplateManager().getTemplate(server, new ResourceLocation(name));

        if (template != null)
        {
            PlacementSettings placementSettings = new PlacementSettings();
            placementSettings.setRotation(placement.getRotation());
            placementSettings.setMirror(placement.getMirror());
            placementSettings.setReplacedBlock(Blocks.STRUCTURE_VOID);

            if (placement.isCentered())
            {
                BlockPos size = Template.transformedBlockPos(placementSettings, template.getSize());
                pos = pos.add(-(size.getX() / 2), 0, -(size.getZ() / 2));
            }

            WorldUtils.loadChunks(world, pos, template.getSize(), placement.getLoadRangeAround());
            template.addBlocksToWorld(world, pos, placementSettings, 0x12);

            return true;
        }

        return false;
    }

    private static boolean tryPlaceSchematic(MinecraftServer server, World world, BlockPos pos, StructurePlacement placement)
    {
        File file = new File(StructurePlacement.getStructureDirectory(), placement.getFile().getName());
        Schematic schematic = Schematic.createFromFile(file);

        if (schematic != null)
        {
            PlacementSettings placementSettings = new PlacementSettings();
            placementSettings.setRotation(placement.getRotation());
            placementSettings.setMirror(placement.getMirror());
            placementSettings.setReplacedBlock(Blocks.STRUCTURE_VOID);
            placementSettings.setIgnoreEntities(false);

            if (placement.isCentered())
            {
                BlockPos size = Template.transformedBlockPos(placementSettings, schematic.getSize());
                pos = pos.add(-(size.getX() / 2), 0, -(size.getZ() / 2));
            }

            WorldUtils.loadChunks(world, pos, schematic.getSize(), placement.getLoadRangeAround());
            schematic.placeSchematicToWorld(world, pos, placementSettings, 2);

            return true;
        }

        return false;
    }

    private static TemplateManager getTemplateManager()
    {
        return new TemplateManager(StructurePlacement.getStructureDirectory().toString(), FMLCommonHandler.instance().getDataFixer());
    }

    /**
     * This will set the spawnDimension field on the player, if the current world has
     * JED world properties, and they say that respawning there should be allowed, but the
     * WorldProvider says it's not. This should support respawning in the same,
     * normally not respawnable dimension, even without a JED WorldProvider.
     * @param player
     */
    public static void setupRespawnDimension(EntityPlayer player)
    {
        World world = player.getEntityWorld();
        final int dim = world.provider.getDimension();
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(dim);

        if (world.provider.canRespawnHere() == false &&
            props != null &&
            props.canRespawnHere() != null &&
            props.canRespawnHere().booleanValue())
        {
            JustEnoughDimensions.logInfo("WorldUtils.setupRespawnDimension: Setting the respawn dimension of player '{}' to: {}", player.getName(), dim);
            player.setSpawnDimension(dim);
            player.addTag(JED_RESPAWN_DIM_TAG);
        }
        else if (player.getTags().contains(JED_RESPAWN_DIM_TAG))
        {
            JustEnoughDimensions.logInfo("WorldUtils.setupRespawnDimension: Removing the respawn dimension data from player '{}'", player.getName());
            player.setSpawnDimension(null);
            player.removeTag(JED_RESPAWN_DIM_TAG);
        }
    }

    public static boolean canBlockFreeze(World world, BlockPos pos, boolean noWaterAdj)
    {
        if (pos.getY() >= 0 && pos.getY() < 256 && world.getLightFor(EnumSkyBlock.BLOCK, pos) < 10)
        {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && state.getValue(BlockLiquid.LEVEL).intValue() == 0)
            {
                if (noWaterAdj == false)
                {
                    return true;
                }

                return isWater(world, pos.west()) == false ||
                       isWater(world, pos.east()) == false ||
                       isWater(world, pos.north()) == false ||
                       isWater(world, pos.south()) == false;
            }
        }

        return false;
    }

    public static boolean isWater(World world, BlockPos pos)
    {
        return world.getBlockState(pos).getMaterial() == Material.WATER;
    }

    public static boolean canSnowAt(World world, BlockPos pos)
    {
        if (pos.getY() >= 0 && pos.getY() < 256 && world.getLightFor(EnumSkyBlock.BLOCK, pos) < 10)
        {
            IBlockState state = world.getBlockState(pos);

            return state.getBlock().isAir(state, world, pos) && Blocks.SNOW_LAYER.canPlaceBlockAt(world, pos);
        }

        return false;
    }
}
