package fi.dy.masa.justenoughdimensions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.DimensionEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DimensionSyncPacket
{
    private ByteBuf buffer = Unpooled.buffer();
    private List<DimensionEntry> dimensions;

    public void addDimensionData(List<DimensionEntry> entries)
    {
        this.buffer.writeInt(entries.size());

        for (DimensionEntry entry : entries)
        {
            entry.writeToByteBuf(this.buffer);
        }
    }

    public void consumePacket(ByteBuf data)
    {
        int count = data.readInt();
        this.dimensions = new ArrayList<DimensionEntry>();

        for (int i = 0 ; i < count ; i++)
        {
            int id = data.readInt();
            String name = ByteBufUtils.readUTF8String(data);
            String suffix = ByteBufUtils.readUTF8String(data);
            String providerClassName = ByteBufUtils.readUTF8String(data);

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

    public ByteBuf getData()
    {
        return this.buffer;
    }

    public void execute()
    {
        List<String> ids = new ArrayList<String>();

        for (DimensionEntry entry : this.dimensions)
        {
            int id = entry.getId();
            ids.add(String.valueOf(id));
            registerDimension(id, entry);
        }

        JustEnoughDimensions.logger.info("DimensionSyncPacket: Registered dimensions: " + String.join(", ", ids));
    }

    public static void registerDimension(int id, DimensionEntry entry)
    {
        if (DimensionManager.isDimensionRegistered(id))
        {
            DimensionType type = DimensionManager.getProviderType(id);

            if (type.createDimension().getClass() != entry.getProviderClass())
            {
                DimensionManager.unregisterDimension(id);
            }
        }

        if (DimensionManager.isDimensionRegistered(id) == false)
        {
            DimensionManager.registerDimension(id, entry.registerDimensionType());
        }
    }
}
