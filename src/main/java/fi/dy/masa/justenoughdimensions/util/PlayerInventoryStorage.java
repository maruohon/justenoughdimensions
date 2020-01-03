package fi.dy.masa.justenoughdimensions.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.ThreadedFileIOBase;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;

public class PlayerInventoryStorage
{
    public static final PlayerInventoryStorage INSTANCE = new PlayerInventoryStorage();
    public static final String DEFAULT_PLAYER_INVENTORY_GROUP = "__default";

    @Nullable private File playerFileDirRoot;

    public void setWorldDir(@Nullable File worldDir)
    {
        if (worldDir != null)
        {
            this.playerFileDirRoot = new File(new File(worldDir, Reference.MOD_ID), "player_inventories");
        }
    }

    public void readPlayerInventoryDataForGroup(EntityPlayer player, String group)
    {
        if (this.playerFileDirRoot != null)
        {
            NBTTagCompound nbt = null;
            File dir = new File(this.playerFileDirRoot, group);

            if (dir.exists() && dir.isDirectory())
            {
                File file = new File(dir, player.getUniqueID().toString() + ".dat");

                if (file.exists() && file.isFile() && file.canRead())
                {
                    try
                    {
                        FileInputStream is = new FileInputStream(file);
                        nbt = CompressedStreamTools.readCompressed(is);
                        is.close();
                    }
                    catch (Exception e)
                    {
                        JustEnoughDimensions.logger.warn("Failed to read player inventory data from file '{}'", file.getAbsolutePath());
                    }
                }
            }

            // Always at least clear the inventory, even if there is no saved inventory yet for this group
            PlayerInventoryHandler.INSTANCE.restorePlayerInventories(player, nbt);
        }
    }

    public void writePlayerInventoryDataForGroup(EntityPlayer player, String group)
    {
        if (this.playerFileDirRoot != null)
        {
            File dir = new File(this.playerFileDirRoot, group);

            if (dir.exists() == false && dir.mkdirs() == false)
            {
                JustEnoughDimensions.logger.warn("Failed to create directory for player inventory group: '{}'", dir.getAbsolutePath());
            }

            if (dir.exists() && dir.isDirectory())
            {
                File fileTmp = new File(dir, player.getUniqueID().toString() + ".dat.tmp");
                File fileReal = new File(dir, player.getUniqueID().toString() + ".dat");
                NBTTagCompound nbt = new NBTTagCompound();
                PlayerInventoryHandler.INSTANCE.savePlayerInventories(player, nbt);

                ThreadedFileIOBase.getThreadedIOInstance().queueIO(() ->
                {
                    try
                    {
                        FileOutputStream os = new FileOutputStream(fileTmp);
                        CompressedStreamTools.writeCompressed(nbt, os);
                        os.close();

                        if (fileReal.exists())
                        {
                            fileReal.delete();
                        }

                        fileTmp.renameTo(fileReal);
                    }
                    catch (Exception e)
                    {
                        JustEnoughDimensions.logger.warn("Failed to write player inventory data to file '{}'", fileTmp.getAbsolutePath(), e);
                    }

                    return false;
                });
            }
        }
    }
}
