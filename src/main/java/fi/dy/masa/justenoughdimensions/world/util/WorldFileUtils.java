package fi.dy.masa.justenoughdimensions.world.util;

import java.io.File;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

public class WorldFileUtils
{
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

    public static boolean levelFileExists(World world)
    {
        File worldDir = WorldFileUtils.getWorldDirectory(world);

        if (worldDir != null)
        {
            File levelFile = new File(worldDir, "level.dat");
            return levelFile.exists() && levelFile.isFile();
        }

        return false;
    }
}
