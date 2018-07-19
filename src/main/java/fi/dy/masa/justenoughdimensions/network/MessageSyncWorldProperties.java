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
import net.minecraft.world.World;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.ColorType;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandlerClient;
import fi.dy.masa.justenoughdimensions.util.ClientUtils;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.world.IWorldProviderJED;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

public class MessageSyncWorldProperties implements IMessage
{
    private JsonObject jedProperties;
    private boolean hasJEDTag;
    private boolean isHardcore;

    public MessageSyncWorldProperties()
    {
    }

    public MessageSyncWorldProperties(World world)
    {
        this.isHardcore = world.getWorldInfo().isHardcoreModeEnabled();
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(world);

        if (props != null)
        {
            this.hasJEDTag = true;
            this.jedProperties = props.getJEDPropsForClientSync();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.isHardcore);
        // Has the JED World Properties object
        buf.writeBoolean(this.hasJEDTag);

        if (this.hasJEDTag)
        {
            try
            {
                DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(new ByteBufOutputStream(buf))));
                data.writeUTF(JEDJsonUtils.serialize(this.jedProperties));
                data.close();
            }
            catch (IOException e)
            {
                JustEnoughDimensions.logger.error("MessageSyncWorldProperties.toBytes(): Failed to write the JEDWorldProperties object to ByteBuf", e);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.isHardcore = buf.readBoolean();

        // Has the JED World Properties object
        if (buf.readBoolean())
        {
            try
            {
                DataInputStream data = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteBufInputStream(buf))));
                JsonElement element = JEDJsonUtils.deserialize(data.readUTF());
                data.close();

                if (element != null && element.isJsonObject())
                {
                    this.jedProperties = element.getAsJsonObject();
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
            world.getWorldInfo().setHardcore(message.isHardcore);

            if (world.provider instanceof IWorldProviderJED)
            {
                ((IWorldProviderJED) world.provider).setJEDPropertiesFromJson(message.jedProperties);
                
                JustEnoughDimensions.logInfo("MessageSyncWorldProperties - DIM: {}: Synced custom JED WorldProvider properties: {}",
                        world.provider.getDimension(), message.jedProperties);
            }
            else
            {
                JEDWorldProperties.createAndSetPropertiesForDimension(world.provider.getDimension(), message.jedProperties);

                if (ClientUtils.setRenderersFrom(world.provider, message.jedProperties))
                {
                    JustEnoughDimensions.logInfo("MessageSyncWorldProperties - DIM: {}: Set a customized sky/cloud/weather render type for a non-JED world",
                            world.provider.getDimension());
                }
            }

            JsonObject colorData = message.jedProperties != null ? JEDJsonUtils.getNestedObject(message.jedProperties, "Colors", false) : null;
            JEDEventHandlerClient.setColors(ColorType.FOLIAGE, JEDEventHandlerClient.getColorMap(colorData, ColorType.FOLIAGE));
            JEDEventHandlerClient.setColors(ColorType.GRASS,   JEDEventHandlerClient.getColorMap(colorData, ColorType.GRASS));
            JEDEventHandlerClient.setColors(ColorType.WATER,   JEDEventHandlerClient.getColorMap(colorData, ColorType.WATER));

            if (colorData != null)
            {
                JustEnoughDimensions.logInfo("MessageSyncWorldProperties - DIM: {}: Synced color data: '{}'",
                        world.provider.getDimension(), JEDJsonUtils.serialize(colorData));
            }
        }
    }
}
