package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.GameType;
import net.minecraftforge.common.DimensionManager;

public class GamemodeInventory
{
	public void swapPlayerInventory(EntityPlayerMP player, GameType oldtype, GameType type)
	{
	    // Get the inventory for the new gametype
		ItemStack[] newInventory = readFromDisk(player, type);
	    // Swap player inventory, get the old one
		ItemStack[] oldInventory = swapInventory(player, newInventory);
	    // Store the old inventory, clear the inventory assigned to the player
	    writeToDisk(player, oldtype, oldInventory, type);
	}
	
	private ItemStack[] swapInventory(EntityPlayerMP player, ItemStack[] newItems)
	{
		ItemStack[] oldItems = new ItemStack[player.inventory.getSizeInventory()];
	    for (int slotIdx = 0; slotIdx < player.inventory.getSizeInventory(); slotIdx++)
	    {
	        oldItems[slotIdx] = player.inventory.getStackInSlot(slotIdx);
	        player.inventory.setInventorySlotContents(slotIdx, newItems[slotIdx]);
	    }
	    return oldItems;
	}
	
	private ItemStack[] readFromDisk(EntityPlayerMP player, GameType type)
    {
		ItemStack[] items = new ItemStack[player.inventory.getSizeInventory()];
		for (int slotIdx = 0; slotIdx < player.inventory.getSizeInventory(); slotIdx++)
		{
			items[slotIdx] = ItemStack.EMPTY;
		}
		
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
                	NBTTagCompound nbt = CompressedStreamTools.readCompressed(in);
                	in.close();
                	
                	// get items
                	NBTTagList itemList = nbt.getTagList(type.toString(), 10);
                    if (itemList != null)
                    {
                    	readFromNBTTag(items, itemList);
                    }
                }
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to read GamemodeInventory for player " + player.getName());
        }
		return items;
    }
    
    public void readFromNBTTag(ItemStack[] items, NBTTagList tagList)
    {
        for (int i = 0; i < tagList.tagCount(); i++)
        {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            int b = tag.getShort("Slot");
            items[b] = new ItemStack(tag);
            //if (tag.hasKey("Quantity"))
            //{
            //    items[b].setCount(((NBTPrimitive) tag.getTag("Quantity")).getInt());
            //}
        }
    }
	
	private void writeToDisk(EntityPlayerMP player, GameType type, ItemStack[] newItems, GameType loadedtype)
    {
		try
        {
            File saveDir = DimensionManager.getCurrentSaveRootDirectory();

            if (saveDir != null)
            {
                File file = new File(new File(saveDir, Reference.MOD_ID), player.getUniqueID() + ".dat");

                // store items
                NBTTagCompound nbt = new NBTTagCompound();
        		NBTTagList invsave = writeToNBTTag(newItems);
                nbt.setTag(type.toString(), invsave);
                nbt.setTag(loadedtype.toString(), new NBTTagList());
            	
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
	
	private NBTTagList writeToNBTTag(ItemStack[] items)
    {
        NBTTagList tagList = new NBTTagList();
        for (int i = 0; i < items.length; i++)
        {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("Slot", (short) i);
            items[i].writeToNBT(tag);
            //tag.setByte("Quantity", (byte)items[i].getCount());
            tagList.appendTag(tag);
        }
        return tagList;
    }
}
