package fi.dy.masa.justenoughdimensions.world.util;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketWorldBorder;
import net.minecraft.world.World;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.event.JEDBorderListener;

public class WorldBorderUtils
{
    private static Field field_WorldBorder_listeners = null;
    private static Field field_WorldServerMulti_borderListener = null;

    static
    {
        try
        {
            field_WorldBorder_listeners = ReflectionHelper.findField(WorldBorder.class, "field_177758_a", "listeners");
            field_WorldServerMulti_borderListener = ReflectionHelper.findField(WorldServerMulti.class, "borderListener");
        }
        catch (UnableToFindFieldException e)
        {
            JustEnoughDimensions.logger.error("WorldBorderUtils: Reflection failed!!", e);
        }
    }

    public static void sendWorldBorder(EntityPlayer player)
    {
        if (Configs.enableSeparateWorldBorders && player.getEntityWorld().isRemote == false && (player instanceof EntityPlayerMP))
        {
            ((EntityPlayerMP) player).connection.sendPacket(
                    new SPacketWorldBorder(player.getEntityWorld().getWorldBorder(), SPacketWorldBorder.Action.INITIALIZE));
        }
    }

    public static void removeDefaultBorderListeners()
    {
        World overworld = DimensionManager.getWorld(0);

        if (Configs.enableSeparateWorldBorders && overworld != null)
        {
            try
            {
                @SuppressWarnings("unchecked")
                List<IBorderListener> listeners = (List<IBorderListener>) field_WorldBorder_listeners.get(overworld.getWorldBorder());

                if (listeners != null)
                {
                    listeners.clear();
                    overworld.getWorldBorder().addListener(new JEDBorderListener(0));
                }
            }
            catch (IllegalArgumentException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
            catch (IllegalAccessException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
        }
    }

    public static void removeOverworldBorderListener(World world)
    {
        World overworld = DimensionManager.getWorld(0);

        if (Configs.enableSeparateWorldBorders && overworld != null && (world instanceof WorldServerMulti))
        {
            try
            {
                @SuppressWarnings("unchecked")
                List<IBorderListener> overworldListeners = (List<IBorderListener>) field_WorldBorder_listeners.get(overworld.getWorldBorder());
                IBorderListener listener = (IBorderListener) field_WorldServerMulti_borderListener.get(world);

                if (overworldListeners != null)
                {
                    overworldListeners.remove(listener);
                }
            }
            catch (IllegalArgumentException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
            catch (IllegalAccessException e)
            {
                JustEnoughDimensions.logger.warn("Failed to clear default WorldBorder listeners");
            }
        }
    }

    public static void setWorldBorderValues(World world)
    {
        WorldBorder border = world.getWorldBorder();
        WorldInfo info = world.getWorldInfo();

        border.setCenter(info.getBorderCenterX(), info.getBorderCenterZ());
        border.setDamageAmount(info.getBorderDamagePerBlock());
        border.setDamageBuffer(info.getBorderSafeZone());
        border.setWarningDistance(info.getBorderWarningDistance());
        border.setWarningTime(info.getBorderWarningTime());

        if (info.getBorderLerpTime() > 0L)
        {
            border.setTransition(info.getBorderSize(), info.getBorderLerpTarget(), info.getBorderLerpTime());
        }
        else
        {
            border.setTransition(info.getBorderSize());
        }
    }
}
