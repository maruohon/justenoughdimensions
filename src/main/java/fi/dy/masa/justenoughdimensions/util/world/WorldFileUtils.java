package fi.dy.masa.justenoughdimensions.util.world;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import com.google.common.io.Files;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;

public class WorldFileUtils
{
    private static final String JED_LEVEL_FILENAME = "jed_level.dat";
    private static final FileFilter FILE_FILTER_NO_LEVEL = new FileFilter()
    {
        public boolean accept(File name)
        {
            return name.getName().equals("level.dat") == false;
        }
    };

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

        if (dimensionDirName != null)
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
                    boolean worldUtilsPresent = Loader.isModLoaded("worldutils");

                    // For non-overworld templates, we can do block ID conversion if World Utils is present.
                    // For overworld templates, a level.dat is required to get the ID map.
                    // TODO 1.13: This restriction and ID conversion can be removed in 1.13 thanks to the per-chunk BlockState palette.
                    if ((dimension == 0 && hasLevelFile) || (dimension != 0 && worldUtilsPresent))
                    {
                        JustEnoughDimensions.logInfo("Copying a template world from '{}' to '{}'",
                                templateWorld.getAbsolutePath(), dimensionDir.getAbsolutePath());

                        FileUtils.copyDirectory(templateWorld, dimensionDir, FILE_FILTER_NO_LEVEL, true);

                        // TODO add block ID conversion stuff - Use a command from World Utils directly (to-be-implemented too...)?

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
                    else if (dimension != 0 && worldUtilsPresent == false)
                    {
                        // TODO 1.13: Remove
                        JustEnoughDimensions.logger.warn("Template world '{}' can't be block-ID-converted because the World Utils mod isn't present",
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

    /**
     * @param worldDir
     * @return true if the given world hasn't yet been created/initialized (no region directory)
     */
    private static boolean isNewWorld(File worldDir)
    {
        File regionDir = new File(worldDir, "region");
        return regionDir.exists() == false;
    }

    public static NBTTagCompound loadWorldInfoFromFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logInfo("WorldFileUtils.loadWorldInfoFromFile(): null worldDir");
            return null;
        }

        File levelFile = new File(worldDir, JED_LEVEL_FILENAME);

        if (levelFile.exists())
        {
            try
            {
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(new FileInputStream(levelFile));
                nbt = world.getMinecraftServer().getDataFixer().process(FixTypes.LEVEL, nbt.getCompoundTag("Data"));
                //FMLCommonHandler.instance().handleWorldDataLoad((SaveHandler) world.getSaveHandler(), info, nbt);
                JustEnoughDimensions.logInfo("WorldFileUtils.loadWorldInfoFromFile(): Read world info from file '{}'", levelFile.getPath());
                return nbt;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Exception reading " + levelFile.getPath(), e);
                return null;
            }

            //return SaveFormatOld.loadAndFix(fileLevel, world.getMinecraftServer().getDataFixer(), (SaveHandler) world.getSaveHandler());
        }

        JustEnoughDimensions.logInfo("WorldFileUtils.loadWorldInfoFromFile(): '{}' didn't exist for dimension {}",
                JED_LEVEL_FILENAME, world.provider.getDimension());
        return null;
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

        if (world.getSaveHandler() instanceof SaveHandler)
        {
            FMLCommonHandler.instance().handleWorldDataSave((SaveHandler) world.getSaveHandler(), info, rootTag);
        }

        try
        {
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
                return;
            }

            fileNew.renameTo(fileCurrent);

            if (fileNew.exists())
            {
                JustEnoughDimensions.logger.error("Failed to rename file '{}' to '{}'", fileNew.getAbsolutePath(), fileCurrent.getAbsolutePath());
                return;
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.error("WorldFileUtils.saveWorldInfoToFile(): Failed to save world "+
                                              "info to file for dimension {}", world.provider.getDimension(), e);
        }
    }

    public static void saveCustomWorldInfoToFile(World world)
    {
        int dimension = world.provider.getDimension();

        if (Configs.enableSeparateWorldInfo && world.isRemote == false &&
            DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            saveWorldInfoToFile(world, getWorldDirectory(world));
        }
    }
}
