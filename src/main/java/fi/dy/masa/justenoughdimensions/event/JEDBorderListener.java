package fi.dy.masa.justenoughdimensions.event;

import com.google.common.base.Predicates;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketWorldBorder;
import net.minecraft.world.World;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;

public class JEDBorderListener implements IBorderListener
{
    private final int dimension;

    public JEDBorderListener(int dimension)
    {
        this.dimension = dimension;
    }

    @Override
    public void onSizeChanged(WorldBorder border, double newSize)
    {
        this.sendPacketToPlayers(new SPacketWorldBorder(border, SPacketWorldBorder.Action.SET_SIZE));
    }

    @Override
    public void onTransitionStarted(WorldBorder border, double oldSize, double newSize, long time)
    {
        this.sendPacketToPlayers(new SPacketWorldBorder(border, SPacketWorldBorder.Action.LERP_SIZE));
    }

    @Override
    public void onCenterChanged(WorldBorder border, double x, double z)
    {
        this.sendPacketToPlayers(new SPacketWorldBorder(border, SPacketWorldBorder.Action.SET_CENTER));
    }

    @Override
    public void onWarningTimeChanged(WorldBorder border, int newTime)
    {
        this.sendPacketToPlayers(new SPacketWorldBorder(border, SPacketWorldBorder.Action.SET_WARNING_TIME));
    }

    @Override
    public void onWarningDistanceChanged(WorldBorder border, int newDistance)
    {
        this.sendPacketToPlayers(new SPacketWorldBorder(border, SPacketWorldBorder.Action.SET_WARNING_BLOCKS));
    }

    @Override
    public void onDamageAmountChanged(WorldBorder border, double newAmount)
    {
    }

    @Override
    public void onDamageBufferChanged(WorldBorder border, double newSize)
    {
    }

    private void sendPacketToPlayers(SPacketWorldBorder packet)
    {
        World world = DimensionManager.getWorld(this.dimension);

        if (world != null && world.isRemote == false)
        {
            for (EntityPlayerMP player : world.getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue()))
            {
                player.connection.sendPacket(packet);
            }
        }
    }
}
