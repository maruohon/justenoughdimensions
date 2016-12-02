package fi.dy.masa.justenoughdimensions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.DimensionEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DimensionSyncPacket
{
    private ByteBuf buffer = Unpooled.buffer();
    private List<DimensionEntry> dimensions = new ArrayList<DimensionEntry>();

    public void addDimensionData(List<DimensionEntry> entries)
    {
        this.buffer.writeInt(entries.size());

        for (DimensionEntry entry : entries)
        {
            entry.writeToByteBuf(this.buffer);
        }
    }

    public void consumePacket(ByteBuf buf)
    {
        int count = buf.readInt();
        this.dimensions.clear();

        for (int i = 0 ; i < count ; i++)
        {
            DimensionEntry entry = DimensionEntry.fromByteBuf(buf);

            if (entry != null && entry.getUnregister() == false)
            {
                this.dimensions.add(entry);
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

        JustEnoughDimensions.logInfo("DimensionSyncPacket: Registered dimensions: " + String.join(", ", ids));
    }

    public static void registerDimension(int id, DimensionEntry entry)
    {
        if (entry.getUnregister())
        {
            return;
        }

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
