package fi.dy.masa.justenoughdimensions.compat.aether;

import com.gildedgames.the_aether.api.AetherAPI;
import com.gildedgames.the_aether.api.player.IPlayerAether;
import fi.dy.masa.justenoughdimensions.util.PlayerInventoryHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

/**
 * Compatibility Class for The Aether Legacy
 */
public class AetherLegacyCompat {
    @CapabilityInject(IPlayerAether.class)
    public static <T> void setAccessoryInventory(Capability<T> capability)
    {
        PlayerInventoryHandler.INSTANCE.addHandler(new PlayerInventoryHandler.InventoryHandler("aether_legacy", InventoryViewAetherLegacy::new));
    }

    public static class InventoryViewAetherLegacy implements PlayerInventoryHandler.IInventoryView
    {
        private final IPlayerAether inv;

        public InventoryViewAetherLegacy(EntityPlayer player)
        {
            this.inv = AetherAPI.getInstance().get(player);
        }

        @Override
        public int getSlotCount()
        {
            return this.inv != null ? this.inv.getAccessoryInventory().getSizeInventory() : 0;
        }

        @Override
        public ItemStack getStack(int slot)
        {
            return this.inv.getAccessoryInventory().getStackInSlot(slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack)
        {
            this.inv.getAccessoryInventory().setInventorySlotContents(slot, stack);
        }
    }
}
