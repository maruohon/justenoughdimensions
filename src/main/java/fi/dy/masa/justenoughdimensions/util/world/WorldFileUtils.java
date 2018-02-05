package fi.dy.masa.justenoughdimensions.util.world;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.annotation.Nullable;
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
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;

public class WorldFileUtils
{
    private static final String JED_LEVEL_FILENAME = "jed_level.dat";

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
            File mainWorldDir = world.getSaveHandler().getWorldDirectory();
            String dimensionDir = world.provider.getSaveFolder();

            if (dimensionDir != null)
            {
                mainWorldDir = new File(mainWorldDir, dimensionDir);
                mainWorldDir.mkdirs();
            }

            return mainWorldDir;
        }
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

    public static NBTTagCompound loadWorldInfoFromFile(World world, @Nullable File worldDir)
    {
        if (worldDir == null)
        {
            JustEnoughDimensions.logInfo("WorldInfoUtils.loadWorldInfoFromFile(): null worldDir");
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

        JustEnoughDimensions.logInfo("WorldInfoUtils.loadWorldInfoFromFile(): '{}' didn't exist for dimension {}",
                JED_LEVEL_FILENAME, world.provider.getDimension());
        return null;
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

    public static void saveCustomWorldInfoToFile(World world)
    {
        int dimension = world.provider.getDimension();

        if (Configs.enableSeparateWorldInfo && world.isRemote == false &&
            DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            saveWorldInfoToFile(world, WorldFileUtils.getWorldDirectory(world));
        }
    }
}
