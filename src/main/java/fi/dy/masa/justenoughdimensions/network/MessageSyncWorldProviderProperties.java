package fi.dy.masa.justenoughdimensions.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandlerClient;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandlerClient.ColorType;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.world.IWorldProviderJED;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;
import fi.dy.masa.justenoughdimensions.world.util.WorldUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

public class MessageSyncWorldProviderProperties implements IMessage
{
    private int dimension;
    private NBTTagCompound nbt;
    private JsonObject colorData;

    public MessageSyncWorldProviderProperties()
    {
    }

    public MessageSyncWorldProviderProperties(int dimension, WorldInfoJED info)
    {
        this.dimension = dimension;
        this.nbt = info.getJEDTag();

        DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);

        if (entry != null)
        {
            this.colorData = entry.getColorData();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.dimension);
        ByteBufUtils.writeTag(buf, this.nbt);

        if (this.colorData != null)
        {
            try
            {
                buf.writeByte(1);
                DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(new ByteBufOutputStream(buf))));
                data.writeUTF(JEDJsonUtils.serialize(this.colorData));
                data.close();
            }
            catch (IOException e)
            {
                JustEnoughDimensions.logger.error("MessageSyncWorldProviderProperties.toBytes(): Failed to write the color object to ByteBuf", e);
            }
        }
        else
        {
            buf.writeByte(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.dimension = buf.readInt();
        this.nbt = ByteBufUtils.readTag(buf);

        if (buf.readByte() != 0)
        {
            try
            {
                DataInputStream data = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteBufInputStream(buf))));
                JsonElement element = JEDJsonUtils.deserialize(data.readUTF());
                data.close();

                if (element != null && element.isJsonObject())
                {
                    this.colorData = element.getAsJsonObject();
                }
            }
            catch (IOException e)
            {
                JustEnoughDimensions.logger.error("MessageSyncWorldProviderProperties.fromBytes(): Failed to read from ByteBuf", e);
            }
        }
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
            else
            {
                if (WorldUtils.setRenderersOnNonJEDWorld(world, message.nbt))
                {
                    JustEnoughDimensions.logInfo("MessageSyncWorldProviderProperties - DIM: {}: Set a customized sky render type for a non-JED world",
                            world.provider.getDimension());
                }
            }

            if (message.colorData != null)
            {
                JEDEventHandlerClient.setColors(message.dimension, ColorType.FOLIAGE, DimensionConfig.getColorMap(message.colorData, "FoliageColors"));
                JEDEventHandlerClient.setColors(message.dimension, ColorType.GRASS,   DimensionConfig.getColorMap(message.colorData, "GrassColors"));
                JEDEventHandlerClient.setColors(message.dimension, ColorType.WATER,   DimensionConfig.getColorMap(message.colorData, "WaterColors"));
            }
        }
    }
}
