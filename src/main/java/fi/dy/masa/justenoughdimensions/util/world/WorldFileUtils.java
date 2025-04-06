package fi.dy.masa.justenoughdimensions.util.world;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import com.google.common.io.Files;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraft.world.storage.WorldInfo;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import fi.dy.masa.justenoughdimensions.reference.Reference;

public class WorldFileUtils
{
    private static final String JED_LEVEL_FILENAME = "jed_level.dat";
    private static final FileFilter FILE_FILTER_NO_LEVEL = (file) -> file.getName().equals("level.dat") == false;

    @Nullable
    public static File getWorldDirectory(World world)
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

            return null;
        }
        // If this method gets called before ChunkProviderServer has been set yet,
        // then we mimic the vanilla code in AnvilSaveHandler#getChunkLoader() to get the directory.
        else
        {
            return getWorldDirectoryDirectly(world, true);
        }
    }

    private static File getWorldDirectoryDirectly(World world, boolean mkDirs)
    {
        File mainWorldDir = world.getSaveHandler().getWorldDirectory();
        File dimensionDir = mainWorldDir;
        String dimensionDirName = world.provider.getSaveFolder();

        // Don't append the dimension directory, if the directory from the SaveHandler already points there.
        // This isn't the case in vanilla/Forge, but some other server mods (bukkit hybrid things?)
        // seem to use separate instances for each dimension.
        if (dimensionDirName != null && mainWorldDir.toString().contains(dimensionDirName) == false)
        {
            dimensionDir = new File(mainWorldDir, dimensionDirName);

            if (mkDirs)
            {
                dimensionDir.mkdirs();
            }
        }

        return dimensionDir;
    }

    public static boolean jedLevelFileExists(World world)
    {
        File worldDir = WorldFileUtils.getWorldDirectory(world);

        if (worldDir != null)
        {
            File levelFile = new File(worldDir, JED_LEVEL_FILENAME);
            return levelFile.exists() && levelFile.isFile();
        }

        return false;
    }

    /**
     * 
     * @param world
     * @return true if worldinfo_onetime should be applied even though the jed_level.dat file already exists
     */
    public static void copyTemplateWorldIfApplicable(World world)
    {
        final int dimension = world.provider.getDimension();

        // This method can't be used for the overworld.
        // For the overworld the template copying needs to happen before the server is started.
        if (dimension != 0)
        {
            File dimensionDir = getWorldDirectoryDirectly(world, false);
            copyTemplateWorldIfApplicable(dimension, dimensionDir);
        }
    }

    /**
     * 
     * @param dimension
     * @param dimensionDir
     * @return true if worldinfo_onetime should be applied even though the jed_level.dat file already exists
     */
    public static void copyTemplateWorldIfApplicable(final int dimension, File dimensionDir)
    {
        DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);

        // For the overworld we need to detect a new world being created differently,
        // because the save directory gets created earlier.
        if (entry != null && entry.getWorldTemplate() != null && isNewWorld(dimensionDir))
        {
            File templateWorld = new File(new File(Configs.getConfigDir(), "world_templates"), entry.getWorldTemplate());

            if (templateWorld.exists() && templateWorld.isDirectory() && templateWorld.canRead())
            {
                try
                {
                    File levelFile = new File(templateWorld, "level.dat");
                    boolean hasLevelFile = levelFile.exists() && levelFile.isFile() && levelFile.canRead();

                    // For overworld templates, a level.dat is required to get the ID map.
                    if (dimension != 0 || hasLevelFile)
                    {
                        JustEnoughDimensions.logInfo("Copying a template world from '{}' to '{}'",
                                templateWorld.getAbsolutePath(), dimensionDir.getAbsolutePath());

                        FileUtils.copyDirectory(templateWorld, dimensionDir, FILE_FILTER_NO_LEVEL, true);

                        if (hasLevelFile && dimension == 0)
                        {
                            Files.copy(levelFile, new File(dimensionDir, "level.dat"));
                        }
                    }
                    else if (dimension == 0 && hasLevelFile == false)
                    {
                        // TODO 1.13: Remove
                        JustEnoughDimensions.logger.warn("Template world '{}' doesn't have the level.dat file (required for the ID map)",
                                templateWorld.getAbsolutePath());
                    }
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.warn("Failed to copy a template world from '{}' to '{}'",
                            templateWorld.getAbsolutePath(), dimensionDir.getAbsolutePath());
                }
            }
            else
            {
                JustEnoughDimensions.logger.warn("Template world '{}' doesn't exist or is not readable",
                        templateWorld.getAbsolutePath());
            }
        }
    }

    public static void createTemporaryWorldMarkerIfApplicable(World world)
    {
        final int dimension = world.provider.getDimension();
        DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);

        if (entry != null && entry.isTemporaryDimension())
        {
            File worldDir = getWorldDirectoryDirectly(world, false);

            if (isNewWorld(worldDir))
            {
                File jedDataDir = getWorldJEDDataDirectory(worldDir);
                File markerFile = getTemporaryDimensionMarkerFile(jedDataDir);

                if (markerFile.exists() == false)
                {
                    try
                    {
                        JustEnoughDimensions.logInfo("Creating a temporary dimension marker file '{}'", markerFile.getAbsolutePath());
                        jedDataDir.mkdirs();
                        markerFile.createNewFile();
                    }
                    catch (Exception e)
                    {
                        JustEnoughDimensions.logger.warn("Failed to create a temporary dimension marker file '{}'",
                                markerFile.getAbsolutePath(), e);
                    }
                }
            }
        }
    }

    public static File getWorldJEDDataDirectory(File worldDir)
    {
        return new File(new File(worldDir, "data"), Reference.MOD_ID);
    }

    public static File getTemporaryDimensionMarkerFile(File jedDataDir)
    {
        return new File(jedDataDir, "jed_temporary_dimension.txt");
    }

    /**
     * @param worldDir
     * @return true if the given world hasn't yet been created/initialized (no region directory)
     */
    private static boolean isNewWorld(File worldDir)
    {
        File regionDir = new File(worldDir, "region");
        return regionDir.exists() == false;
    }

    public static NBTTagCompound loadWorldInfoNbtFromFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logInfo("WorldFileUtils.loadWorldInfoNbtFromFile(): null worldDir");
            return null;
        }

        File levelFile = new File(worldDir, JED_LEVEL_FILENAME);

        if (levelFile.exists())
        {
            try
            {
                JustEnoughDimensions.logInfo("WorldFileUtils.loadWorldInfoNbtFromFile(): Reading WorldInfo NBT from file '{}'",
                                             levelFile.getAbsolutePath());
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(new FileInputStream(levelFile));
                nbt = world.getMinecraftServer().getDataFixer().process(FixTypes.LEVEL, nbt.getCompoundTag("Data"));
                return nbt;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("WorldFileUtils.loadWorldInfoNbtFromFile(): Exception reading NBT from file '{}'",
                                                 levelFile.getAbsolutePath(), e);
            }
        }

        JustEnoughDimensions.logInfo("WorldFileUtils.loadWorldInfoNbtFromFile(): '{}' didn't exist for dimension {}",
                JED_LEVEL_FILENAME, world.provider.getDimension());
        return null;
    }

    @Nullable
    public static NBTTagCompound readNbtFromFile(File file)
    {
        if (file.exists())
        {
            try (FileInputStream is = new FileInputStream(file))
            {
                JustEnoughDimensions.logInfo("WorldFileUtils.readNbtFromFile(): Reading NBT from file '{}'",
                                             file.getAbsolutePath());
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(is);
                return nbt;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("WorldFileUtils.readNbtFromFile(): Exception reading NBT from file '{}'",
                                                 file.getAbsolutePath(), e);
            }
        }

        return null;
    }

    public static boolean writeNbtToFile(File file, NBTTagCompound nbt)
    {
        try (FileOutputStream os = new FileOutputStream(file))
        {
            JustEnoughDimensions.logInfo("WorldFileUtils.writeNbtToFile(): Writing NBT to file '{}'",
                                         file.getAbsolutePath());
            CompressedStreamTools.writeCompressed(nbt, os);
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("WorldFileUtils.writeNbtToFile(): Exception writing NBT to file '{}'",
                                             file.getAbsolutePath(), e);
            return false;
        }

        return true;
    }

    public static void overrideValuesInWorldInfo(File levelFile, MinecraftServer server, Consumer<NBTTagCompound> dataModifier)
    {
        JustEnoughDimensions.logInfo("WorldFileUtils.overrideValuesInWorldInfo(): Attempting to override values in file '{}'",
                                     levelFile.getAbsolutePath());

        NBTTagCompound nbt = readNbtFromFile(levelFile);

        if (nbt == null)
        {
            return;
        }

        try
        {
            NBTTagCompound worldInfoTag = server.getDataFixer().process(FixTypes.LEVEL, nbt.getCompoundTag("Data"));
            dataModifier.accept(worldInfoTag);

            nbt.setTag("Data", worldInfoTag);

            if (writeNbtToFile(levelFile, nbt))
            {
                JustEnoughDimensions.logInfo("WorldFileUtils.overrideValuesInWorldInfo(): Successfully overrode values in file '{}'",
                                             levelFile.getAbsolutePath());
                return;
            }
        }
        catch (Exception ignore) {}

        JustEnoughDimensions.logger.warn("WorldFileUtils.overrideValuesInWorldInfo(): Failed to override values in file '{}'",
                                         levelFile.getAbsolutePath());
    }

    private static void saveWorldInfoToFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logger.warn("WorldFileUtils.saveWorldInfoToFile(): No worldDir found");
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

        ThreadedFileIOBase.getThreadedIOInstance().queueIO(() ->
        {
            try
            {
                /*
                if (world.getSaveHandler() instanceof SaveHandler)
                {
                    FMLCommonHandler.instance().handleWorldDataSave((SaveHandler) world.getSaveHandler(), info, rootTag);
                }
                */

                File fileNew = new File(worldDir, JED_LEVEL_FILENAME + "_new");
                File fileOld = new File(worldDir, JED_LEVEL_FILENAME + "_old");
                File fileCurrent = new File(worldDir, JED_LEVEL_FILENAME);
                CompressedStreamTools.writeCompressed(rootTag, new FileOutputStream(fileNew));

                if (fileOld.exists())
                {
                    fileOld.delete();
                }

                fileCurrent.renameTo(fileOld);

                if (fileCurrent.exists())
                {
                    JustEnoughDimensions.logger.error("Failed to rename file '{}' to '{}'", fileCurrent.getAbsolutePath(), fileOld.getAbsolutePath());
                    return false;
                }

                fileNew.renameTo(fileCurrent);

                if (fileNew.exists())
                {
                    JustEnoughDimensions.logger.error("Failed to rename file '{}' to '{}'", fileNew.getAbsolutePath(), fileCurrent.getAbsolutePath());
                    return false;
                }
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("WorldFileUtils.saveWorldInfoToFile(): Failed to save world "+
                                                  "info to file for dimension {}", world.provider.getDimension(), e);
            }

            return false;
        });
    }

    public static void saveCustomWorldInfoToFile(World world)
    {
        if (Configs.enableSeparateWorldInfo && world.isRemote == false)
        {
            saveWorldInfoToFile(world, getWorldDirectory(world));
        }
    }
}
