package fi.dy.masa.justenoughdimensions.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import fi.dy.masa.justenoughdimensions.compat.trash_slot.TrashSlotCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;

public class PlayerInventoryHandler
{
    public static final PlayerInventoryHandler INSTANCE = new PlayerInventoryHandler();

    private final List<InventoryHandler> handlers = new ArrayList<>();

    private PlayerInventoryHandler()
    {
        this.handlers.add(new InventoryHandler("player_inv", InventoryViewPlayerInventory::new));
        this.handlers.add(new InventoryHandler("ender_chest", InventoryViewEnderChest::new));

        if(Loader.isModLoaded("trashslot"))
            this.handlers.add(new InventoryHandler("trash", TrashSlotCompat.InventoryViewTrashSlot::new));
    }

    public PlayerInventoryHandler addHandler(InventoryHandler handler)
    {
        this.handlers.add(handler);
        return this;
    }

    public boolean savePlayerInventories(EntityPlayer player, NBTTagCompound nbt)
    {
        boolean success = true;

        for (InventoryHandler handler : this.handlers)
        {
            success &= handler.writeInventory(player, nbt);
        }

        return success;
    }

    public boolean restorePlayerInventories(EntityPlayer player, @Nullable NBTTagCompound nbt)
    {
        boolean success = true;

        for (InventoryHandler handler : this.handlers)
        {
            success &= handler.readInventory(player, nbt);
        }

        return success;
    }

    public static class InventoryHandler
    {
        protected final String tagName;
        protected final Function<EntityPlayer, IInventoryView> inventoryRetriever;

        public InventoryHandler(String tagName, Function<EntityPlayer, IInventoryView> inventoryRetriever)
        {
            this.tagName = tagName;
            this.inventoryRetriever = inventoryRetriever;
        }

        public boolean readInventory(EntityPlayer player, @Nullable NBTTagCompound nbt)
        {
            IInventoryView inv = this.inventoryRetriever.apply(player);

            if (inv != null)
            {
                return this.readInventory(inv, nbt);
            }

            return false;
        }

        public boolean writeInventory(EntityPlayer player, NBTTagCompound nbt)
        {
            IInventoryView inv = this.inventoryRetriever.apply(player);

            if (inv != null)
            {
                return this.writeInventory(inv, nbt);
            }

            return false;
        }

        protected boolean readInventory(IInventoryView inv, @Nullable NBTTagCompound nbt)
        {
            final int invSize = inv.getSlotCount();

            // Clear the current inventory contents first
            for (int slot = 0; slot < invSize; ++slot)
            {
                inv.setStack(slot, ItemStack.EMPTY);
            }

            if (nbt != null && nbt.hasKey(this.tagName, Constants.NBT.TAG_LIST))
            {
                NBTTagList list = nbt.getTagList(this.tagName, Constants.NBT.TAG_COMPOUND);
                final int listSize = list.tagCount();

                for (int i = 0; i < listSize; ++i)
                {
                    NBTTagCompound tag = list.getCompoundTagAt(i);
                    int slot = tag.getInteger("Slot");
                    ItemStack stack = new ItemStack(tag);

                    if (slot >= 0 && slot < invSize && stack.isEmpty() == false)
                    {
                        inv.setStack(slot, stack);
                    }
                }

                return true;
            }

            return false;
        }

        protected boolean writeInventory(IInventoryView inv, NBTTagCompound nbt)
        {
            NBTTagList list = new NBTTagList();
            final int invSize = inv.getSlotCount();

            for (int slot = 0; slot < invSize; ++slot)
            {
                ItemStack stack = inv.getStack(slot);

                if (stack.isEmpty() == false)
                {
                    NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
                    tag.setInteger("Slot", slot);
                    list.appendTag(tag);
                }
            }

            nbt.setTag(this.tagName, list);

            return false;
        }
    }

    public interface IInventoryView
    {
        public int getSlotCount();

        public ItemStack getStack(int slot);

        public void setStack(int slot, ItemStack stack);
    }

    public static class InventoryViewPlayerInventory implements IInventoryView
    {
        private final EntityPlayer player;

        public InventoryViewPlayerInventory(EntityPlayer player)
        {
            this.player = player;
        }

        @Override
        public int getSlotCount()
        {
            return this.player.inventory.getSizeInventory();
        }

        @Override
        public ItemStack getStack(int slot)
        {
            return this.player.inventory.getStackInSlot(slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack)
        {
            this.player.inventory.setInventorySlotContents(slot, stack);
        }
    }

    public static class InventoryViewEnderChest implements IInventoryView
    {
        private final EntityPlayer player;

        public InventoryViewEnderChest(EntityPlayer player)
        {
            this.player = player;
        }

        @Override
        public int getSlotCount()
        {
            return this.player.getInventoryEnderChest().getSizeInventory();
        }

        @Override
        public ItemStack getStack(int slot)
        {
            return this.player.getInventoryEnderChest().getStackInSlot(slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack)
        {
            this.player.getInventoryEnderChest().setInventorySlotContents(slot, stack);
        }
    }
}
