package fi.dy.masa.justenoughdimensions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DimensionSyncPacket
{
    private ByteBuf buffer = Unpooled.buffer();
    private List<DimensionConfigEntry> dimensions = new ArrayList<DimensionConfigEntry>();

    public void addDimensionData(List<DimensionConfigEntry> entries)
    {
        this.buffer.writeInt(entries.size());

        for (DimensionConfigEntry entry : entries)
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
            DimensionConfigEntry entry = DimensionConfigEntry.fromByteBuf(buf);

            if (entry != null)
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

        for (DimensionConfigEntry entry : this.dimensions)
        {
            registerDimension(entry.getId(), entry);

            if (entry.getUnregister() == false && entry.hasDimensionTypeEntry())
            {
                ids.add(String.valueOf(entry.getId()));
            }
        }

        JustEnoughDimensions.logInfo("DimensionSyncPacket: Registered dimensions: '" + String.join(", ", ids) + "'");
    }

    public static void registerDimension(int id, DimensionConfigEntry entry)
    {
        if (entry.getUnregister() || entry.hasDimensionTypeEntry() == false)
        {
            return;
        }

        if (DimensionManager.isDimensionRegistered(id))
        {
            DimensionType type = DimensionManager.getProviderType(id);

            if (type.createDimension().getClass() != entry.getDimensionTypeEntry().getProviderClass())
            {
                DimensionManager.unregisterDimension(id);
            }
        }

        if (DimensionManager.isDimensionRegistered(id) == false)
        {
            DimensionManager.registerDimension(id, entry.getDimensionTypeEntry().registerDimensionType());
        }
    }
}
