package fi.dy.masa.justenoughdimensions.event;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.GameType;

public class GamemodeInventoryBaubles {
    
	private final String NBTTAG = "baubles.";
	
	public void swapPlayerInventory(EntityPlayerMP player, GameType oldtype, GameType type, NBTTagCompound nbt)
	{
		// copy baubles inventory to file for old gamemode
		NBTTagList baub_old = new NBTTagList();
		baub_old = getPlayerBaubles(player);
		nbt.setTag(NBTTAG + oldtype.toString(), baub_old);
		
		// move baubles inventory to player for new gamemode
		NBTTagList baub_new = nbt.getTagList(NBTTAG + type.toString(), 10);
		setPlayerBaubles(player, baub_new);
		nbt.setTag(NBTTAG + type.toString(), new NBTTagList());
	}
	
	private NBTTagList getPlayerBaubles(EntityPlayerMP player)
    {
		IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
		NBTTagList taglist = new NBTTagList();

        for (int i=0; i < baubles.getSlots(); i++)
        {
            ItemStack stack = baubles.getStackInSlot(i);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            stack.writeToNBT(tag);
            taglist.appendTag(tag);
        }
        return taglist;
    }
	
	private void setPlayerBaubles(EntityPlayerMP player, NBTTagList taglist)
    {
        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);

        for (int i=0; i < taglist.tagCount(); i++)
        {
            NBTTagCompound slot = (NBTTagCompound)taglist.get(i);
            baubles.setStackInSlot(slot.getInteger("Slot"), new ItemStack(slot));
        }
    }
}
