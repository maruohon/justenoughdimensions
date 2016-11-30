package fi.dy.masa.justenoughdimensions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.DimensionEntry;
import io.netty.buffer.ByteBuf;

public class MessageSyncDimensions implements IMessage
{
    private List<DimensionEntry> dimensions = new ArrayList<DimensionEntry>();

    public MessageSyncDimensions()
    {
    }

    public MessageSyncDimensions(List<DimensionEntry> entries)
    {
        this.dimensions = entries;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        int count = buf.readInt();
        this.dimensions = new ArrayList<DimensionEntry>();

        for (int i = 0 ; i < count ; i++)
        {
            int id = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            String suffix = ByteBufUtils.readUTF8String(buf);
            String providerClassName = ByteBufUtils.readUTF8String(buf);

            try
            {
                @SuppressWarnings("unchecked")
                Class<? extends WorldProvider> providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
                this.dimensions.add(new DimensionEntry(id, name, suffix, false, providerClass));
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to read dimension info from packet for dimension {}" +
                    " - WorldProvider class {} not found", id, providerClassName);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.dimensions.size());

        for (DimensionEntry entry : this.dimensions)
        {
            entry.writeToByteBuf(buf);
        }
    }

    public static class Handler implements IMessageHandler<MessageSyncDimensions, IMessage>
    {
        @Override
        public IMessage onMessage(final MessageSyncDimensions message, MessageContext ctx)
        {
            if (ctx.side != Side.CLIENT)
            {
                JustEnoughDimensions.logger.error("Wrong side in MessageSyncDimensions: " + ctx.side);
                return null;
            }

            Minecraft mc = FMLClientHandler.instance().getClient();
            if (mc == null)
            {
                JustEnoughDimensions.logger.error("Minecraft was null in MessageSyncDimensions");
                return null;
            }

            mc.addScheduledTask(new Runnable()
            {
                public void run()
                {
                    processMessage(message);
                }
            });

            return null;
        }

        protected void processMessage(final MessageSyncDimensions message)
        {
            List<String> ids = new ArrayList<String>();

            for (DimensionEntry dim : message.dimensions)
            {
                int id = dim.getId();
                ids.add(String.valueOf(id));

                if (DimensionManager.isDimensionRegistered(id) == false)
                {
                    DimensionManager.registerDimension(id, dim.registerDimensionType());
                }
            }

            JustEnoughDimensions.logger.info("DimensionSyncPacket: Registered dimensions: " + String.join(", ", ids));
        }
    }
}
