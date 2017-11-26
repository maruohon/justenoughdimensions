package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.GameType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;

public class GamemodeInventory
{
	private final String NBTTAG = "main.";
	
	private GamemodeInventoryBaubles baubles;
	
	public GamemodeInventory()
	{
		if (Loader.isModLoaded("baubles"))
		{
			baubles = new GamemodeInventoryBaubles();
		}
	}
	
	public void swapPlayerInventory(EntityPlayerMP player, GameType oldtype, GameType type)
	{
		// read player inventory file
		NBTTagCompound nbt = readFromDisk(player);
		
		// copy main inventory to file for old gamemode
		NBTTagList main_old = new NBTTagList();
		player.inventory.writeToNBT(main_old);
		nbt.setTag(NBTTAG + oldtype.toString(), main_old);
		
		// move main inventory to player for new gamemode
		NBTTagList main_new = nbt.getTagList(NBTTAG + type.toString(), 10);
		player.inventory.readFromNBT(main_new);
		nbt.setTag(NBTTAG + type.toString(), new NBTTagList());
		
		// handle baubles
		if (baubles != null)
		{
			baubles.swapPlayerInventory(player, oldtype, type, nbt);
		}
		
		// write player inventory file
		writeToDisk(player, nbt);
	}
	
	private NBTTagCompound readFromDisk(EntityPlayerMP player)
    {
		NBTTagCompound nbt = new NBTTagCompound();
		try
        {
            File saveDir = DimensionManager.getCurrentSaveRootDirectory();

            if (saveDir != null)
            {
                File file = new File(new File(saveDir, Reference.MOD_ID), player.getUniqueID() + ".dat");

                if (file.exists() && file.isFile())
                {
                	// read player inventory file
                	FileInputStream in = new FileInputStream(file);
                	nbt = CompressedStreamTools.readCompressed(in);
                	in.close();
                }
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to read GamemodeInventory for player " + player.getName());
        }
		return nbt;
    }
	
	private void writeToDisk(EntityPlayerMP player, NBTTagCompound nbt)
    {
		try
        {
            File saveDir = DimensionManager.getCurrentSaveRootDirectory();

            if (saveDir != null)
            {
                File file = new File(new File(saveDir, Reference.MOD_ID), player.getUniqueID() + ".dat");
            	
                // write player inventory file
                FileOutputStream out = new FileOutputStream(file);
                CompressedStreamTools.writeCompressed(nbt, out);
                out.close();
            }
        }
        catch (Exception e)
        {
        	JustEnoughDimensions.logger.warn("Failed to write GamemodeInventory for player " + player.getName());
        }
    }
}
