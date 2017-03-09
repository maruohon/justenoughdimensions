package fi.dy.masa.justenoughdimensions.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.world.IWorldProviderJED;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;
import io.netty.buffer.ByteBuf;

public class MessageSyncWorldProviderProperties implements IMessage
{
    NBTTagCompound nbt;

    public MessageSyncWorldProviderProperties()
    {
    }

    public MessageSyncWorldProviderProperties(WorldInfoJED info)
    {
        this.nbt = info.getJEDTag();
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.nbt = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeTag(buf, this.nbt);
    }

    public static class Handler implements IMessageHandler<MessageSyncWorldProviderProperties, IMessage>
    {
        @Override
        public IMessage onMessage(final MessageSyncWorldProviderProperties message, MessageContext ctx)
        {
            if (ctx.side != Side.CLIENT)
            {
                JustEnoughDimensions.logger.error("Wrong side in MessageSyncWorldProviderProperties: " + ctx.side);
                return null;
            }

            final Minecraft mc = FMLClientHandler.instance().getClient();

            if (mc == null)
            {
                JustEnoughDimensions.logger.error("Minecraft was null in MessageSyncWorldProviderProperties");
                return null;
            }

            mc.addScheduledTask(new Runnable()
            {
                public void run()
                {
                    processMessage(message, mc.world);
                }
            });

            return null;
        }

        protected void processMessage(final MessageSyncWorldProviderProperties message, final World world)
        {
            if (world.provider instanceof IWorldProviderJED)
            {
                ((IWorldProviderJED) world.provider).setJEDPropertiesFromNBT(message.nbt);
                
                JustEnoughDimensions.logInfo("MessageSyncWorldProviderProperties - DIM: {}: Synced custom JED WorldProvider properties: {}",
                        world.provider.getDimension(), message.nbt);
            }
        }
    }
}
