package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.world.util.WorldFileUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
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
		
		// if inventory is missing, try to import from dimension/playerdata
		if (!nbt.hasKey(NBTTAG + type.toString()))
		{
			nbt.setTag(NBTTAG + type.toString(), readFromDiskLegacy(player));
		}
		
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
	
	private NBTTagList readFromDiskLegacy(EntityPlayerMP player)
    {
		NBTTagList taglist = new NBTTagList();
		World world = player.getEntityWorld();
		
		// only access player.dat files in dimensions other than 0
		// these should not exist, except when migrating from a plugin like multiverse
		if (world.provider.getDimension() == 0)
		{
			return taglist;
		}
		
		try
        {
			// try to locate player.dat in the dimension/playerdata directory
	        File worldDir = WorldFileUtils.getWorldDirectory(world);
	        if (worldDir != null)
	        {
	            File playerFile = new File(new File(worldDir, "playerdata"), player.getUniqueID() + ".dat");
	            
	            if (playerFile.exists() && playerFile.isFile())
	            {
	            	// read player.dat file
	            	FileInputStream in = new FileInputStream(playerFile);
	            	NBTTagCompound nbt = CompressedStreamTools.readCompressed(in);
	            	in.close();
	            	
	            	// get inventory
	            	taglist = nbt.getTagList("Inventory", 10);
	            	JustEnoughDimensions.logger.warn("Importing legacy playerfile for player " + player.getName());
	            	
	            	// support old 1.7 format: translate numeric item ids to text based
	                for (int i=0; i < taglist.tagCount(); i++)
	                {
	                    // check if slot has a numeric id
	                	NBTTagCompound slot = (NBTTagCompound)taglist.get(i);
	                    if (slot.hasKey("id", 2) && slot.hasKey("Count", 1))
	                    {
	                    	// try to find item
	                    	short id = slot.getShort("id");
	                    	Item item = Item.getItemById(id);
	                    	
	                    	if (item != null)
	                    	{
		                    	// overwrite slot values with text based id, keeping other entries
	                    		ItemStack stack = new ItemStack(item, slot.getByte("Count"));
			                    stack.writeToNBT(slot);
			                    taglist.set(i, slot);
	                    	}
	                    	else
	                    	{
	                    		JustEnoughDimensions.logger.warn("Removing unknown item with id: " + id);
	                    	}
	                    }
	                }
	            }
	        }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to read legacy playerfile for player " + player.getName());
        }
		return taglist;
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
