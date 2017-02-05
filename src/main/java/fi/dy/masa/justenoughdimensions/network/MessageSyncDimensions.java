package fi.dy.masa.justenoughdimensions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import io.netty.buffer.ByteBuf;

public class MessageSyncDimensions implements IMessage
{
    private List<DimensionConfigEntry> dimensions = new ArrayList<DimensionConfigEntry>();

    public MessageSyncDimensions()
    {
    }

    public MessageSyncDimensions(List<DimensionConfigEntry> entries)
    {
        this.dimensions.addAll(entries);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        int count = buf.readInt();
        this.dimensions.clear();

        for (int i = 0 ; i < count ; i++)
        {
            DimensionConfigEntry entry = DimensionConfigEntry.fromByteBuf(buf);

            if (entry != null)
            {
                this.dimensions.add(entry);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.dimensions.size());

        for (DimensionConfigEntry entry : this.dimensions)
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

            for (DimensionConfigEntry entry : message.dimensions)
            {
                DimensionSyncPacket.registerDimension(entry.getId(), entry);

                if (entry.getUnregister() == false && entry.hasDimensionTypeEntry())
                {
                    ids.add(String.valueOf(entry.getId()));
                }
            }

            JustEnoughDimensions.logInfo("DimensionSyncPacket: Registered dimensions: '" + String.join(", ", ids) + "'");
        }
    }
}
