package fi.dy.masa.justenoughdimensions.compat.aether;

import com.gildedgames.the_aether.api.AetherAPI;
import com.gildedgames.the_aether.api.player.IPlayerAether;
import com.gildedgames.the_aether.api.player.util.IAccessoryInventory;
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
        private final IAccessoryInventory inv;

        public InventoryViewAetherLegacy(EntityPlayer player)
        {
            this.inv = AetherAPI.getInstance().get(player).getAccessoryInventory();
        }

        @Override
        public int getSlotCount()
        {
            return this.inv != null ? this.inv.getSizeInventory() : 0;
        }

        @Override
        public ItemStack getStack(int slot)
        {
            return this.inv.getStackInSlot(slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack)
        {
            this.inv.setInventorySlotContents(slot, stack);
        }
    }
}
