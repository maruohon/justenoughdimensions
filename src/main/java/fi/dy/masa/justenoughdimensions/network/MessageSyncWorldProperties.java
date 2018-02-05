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
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.ColorType;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandlerClient;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldUtils;
import fi.dy.masa.justenoughdimensions.world.IWorldProviderJED;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

public class MessageSyncWorldProperties implements IMessage
{
    private NBTTagCompound nbt;
    private JsonObject colorData;
    private boolean hasJEDTag;

    public MessageSyncWorldProperties()
    {
    }

    public MessageSyncWorldProperties(World world)
    {
        int dimension = world.provider.getDimension();
        DimensionConfigEntry entry = DimensionConfig.instance().getDimensionConfigFor(dimension);
        JEDWorldProperties props = JEDWorldProperties.getProperties(world);

        if (props != null)
        {
            this.hasJEDTag = true;
            this.nbt = props.getJEDTagForClientSync();
        }

        if (entry != null)
        {
            this.colorData = entry.getColorData();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        // Has the JED NBT tag
        buf.writeBoolean(this.hasJEDTag);

        if (this.hasJEDTag)
        {
            ByteBufUtils.writeTag(buf, this.nbt);
        }

        if (this.colorData != null)
        {
            // Has color data
            buf.writeBoolean(true);

            try
            {
                DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(new ByteBufOutputStream(buf))));
                data.writeUTF(JEDJsonUtils.serialize(this.colorData));
                data.close();
            }
            catch (IOException e)
            {
                JustEnoughDimensions.logger.error("MessageSyncWorldProperties.toBytes(): Failed to write the color object to ByteBuf", e);
            }
        }
        else
        {
            // No color data
            buf.writeBoolean(false);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        // Has the JED NBT tag
        if (buf.readBoolean())
        {
            this.nbt = ByteBufUtils.readTag(buf);
        }

        // Has color data
        if (buf.readBoolean())
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
                JustEnoughDimensions.logger.error("MessageSyncWorldProperties.fromBytes(): Failed to read from ByteBuf", e);
            }
        }
    }

    public static class Handler implements IMessageHandler<MessageSyncWorldProperties, IMessage>
    {
        @Override
        public IMessage onMessage(final MessageSyncWorldProperties message, MessageContext ctx)
        {
            if (ctx.side != Side.CLIENT)
            {
                JustEnoughDimensions.logger.error("Wrong side in MessageSyncWorldProperties: " + ctx.side);
                return null;
            }

            final Minecraft mc = FMLClientHandler.instance().getClient();

            if (mc == null)
            {
                JustEnoughDimensions.logger.error("Minecraft was null in MessageSyncWorldProperties");
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

        protected void processMessage(final MessageSyncWorldProperties message, final World world)
        {
            if (world.provider instanceof IWorldProviderJED)
            {
                ((IWorldProviderJED) world.provider).setJEDPropertiesFromNBT(message.nbt);
                
                JustEnoughDimensions.logInfo("MessageSyncWorldProperties - DIM: {}: Synced custom JED WorldProvider properties: {}",
                        world.provider.getDimension(), message.nbt);
            }
            else if (message.nbt != null && WorldUtils.setRenderersOnNonJEDWorld(world, message.nbt))
            {
                JustEnoughDimensions.logInfo("MessageSyncWorldProperties - DIM: {}: Set a customized sky render type for a non-JED world",
                        world.provider.getDimension());
            }

            JEDEventHandlerClient.setColors(ColorType.FOLIAGE, JEDEventHandlerClient.getColorMap(message.colorData, ColorType.FOLIAGE));
            JEDEventHandlerClient.setColors(ColorType.GRASS,   JEDEventHandlerClient.getColorMap(message.colorData, ColorType.GRASS));
            JEDEventHandlerClient.setColors(ColorType.WATER,   JEDEventHandlerClient.getColorMap(message.colorData, ColorType.WATER));

            if (message.colorData != null)
            {
                JustEnoughDimensions.logInfo("MessageSyncWorldProperties - DIM: {}: Synced color data: '{}'",
                        world.provider.getDimension(), JEDJsonUtils.serialize(message.colorData));
            }
        }
    }
}
