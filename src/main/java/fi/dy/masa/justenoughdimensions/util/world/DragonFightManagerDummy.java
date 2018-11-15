package fi.dy.masa.justenoughdimensions.util.world;

import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraft.world.end.DragonFightManager;

public class DragonFightManagerDummy extends DragonFightManager
{
    public DragonFightManagerDummy(WorldServer worldIn, NBTTagCompound compound)
    {
        super(worldIn, compound);
    }

    @Override
    public void addPlayer(EntityPlayerMP player)
    {
        // NO-OP
    }

    @Override
    public void removePlayer(EntityPlayerMP player)
    {
        // NO-OP
    }

    @Override
    public void dragonUpdate(EntityDragon dragonIn)
    {
        // NO-OP
    }

    @Override
    public void processDragonDeath(EntityDragon dragon)
    {
        // NO-OP
    }

    @Override
    public void respawnDragon()
    {
        // NO-OP
    }

    @Override
    public void onCrystalDestroyed(EntityEnderCrystal crystal, DamageSource dmgSrc)
    {
        // NO-OP
    }

    @Override
    public void resetSpikeCrystals()
    {
        // NO-OP
    }

    @Override
    public void tick()
    {
        // NO-OP
    }
}
