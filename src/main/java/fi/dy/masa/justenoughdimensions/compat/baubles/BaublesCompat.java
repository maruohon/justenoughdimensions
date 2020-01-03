package fi.dy.masa.justenoughdimensions.compat.baubles;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import fi.dy.masa.justenoughdimensions.util.PlayerInventoryHandler;
import fi.dy.masa.justenoughdimensions.util.PlayerInventoryHandler.IInventoryView;
import fi.dy.masa.justenoughdimensions.util.PlayerInventoryHandler.InventoryHandler;

public class BaublesCompat
{
    @CapabilityInject(IBaublesItemHandler.class)
    public static <T> void initBaubles(Capability<T> capability)
    {
        PlayerInventoryHandler.INSTANCE.addHandler(new InventoryHandler("baubles", InventoryViewBaubles::new));
    }

    public static class InventoryViewBaubles implements IInventoryView
    {
        private final IBaublesItemHandler inv;

        public InventoryViewBaubles(EntityPlayer player)
        {
            this.inv = BaublesApi.getBaublesHandler(player);
        }

        @Override
        public int getSlotCount()
        {
            return this.inv != null ? this.inv.getSlots() : 0;
        }

        @Override
        public ItemStack getStack(int slot)
        {
            return this.inv.getStackInSlot(slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack)
        {
            this.inv.setStackInSlot(slot, stack);
        }
    }
}
