package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.nbt.NBTTagCompound;

public interface IWorldProviderJED
{
    /**
     *  Set JED-specific WorldProvider properties on the client side from a synced NBT tag
     * @param tag
     */
    public void setJEDPropertiesFromNBT(NBTTagCompound tag);
}
