package fi.dy.masa.justenoughdimensions.compat.trash_slot;

import fi.dy.masa.justenoughdimensions.util.PlayerInventoryHandler.IInventoryView;
import net.blay09.mods.trashslot.TrashHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Compatibility Class for Trash-Slot
 */
public class TrashSlotCompat
{
    public static class InventoryViewTrashSlot implements IInventoryView
    {
        private final EntityPlayer player;

        public InventoryViewTrashSlot(EntityPlayer player)
        {
            this.player = player;
        }

        @Override
        public int getSlotCount()
        {
            return 1;
        }

        @Override
        public ItemStack getStack(int slot)
        {
            return TrashHelper.getTrashItem(this.player);
        }

        @Override
        public void setStack(int slot, ItemStack stack)
        {
            TrashHelper.setTrashItem(this.player, stack);
        }
    }
}
