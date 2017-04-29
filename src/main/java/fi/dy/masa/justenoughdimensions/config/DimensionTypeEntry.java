package fi.dy.masa.justenoughdimensions.config;

import javax.annotation.Nonnull;
import com.google.gson.JsonObject;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.world.WorldProviderEndJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;
import io.netty.buffer.ByteBuf;

public class DimensionTypeEntry implements Comparable<DimensionTypeEntry>
{
    private final int id;
    private final String name;
    private final String suffix;
    private final boolean keepLoaded;
    private final Class<? extends WorldProvider> providerClass;
    private final String dimensionTypeName;

    public DimensionTypeEntry(int id, String vanillaDimensionTypeName)
    {
        this.id = id;
        this.dimensionTypeName = vanillaDimensionTypeName;
        this.name = null;
        this.suffix = null;
        this.keepLoaded = false;
        this.providerClass = null;
    }

    public DimensionTypeEntry(int id, String name, String suffix, boolean keepLoaded, @Nonnull Class<? extends WorldProvider> providerClass)
    {
        this.id = id;
        this.name = name;
        this.suffix = suffix;
        this.keepLoaded = id == 0 ? true : keepLoaded;
        this.providerClass = providerClass;
        this.dimensionTypeName = null;
    }

    public int getId()
    {
        return this.id;
    }

    public Class<? extends WorldProvider> getProviderClass()
    {
        return this.providerClass;
    }

    public DimensionType registerDimensionType()
    {
        if (this.dimensionTypeName != null)
        {
            DimensionType type = null;
            try
            {
                JustEnoughDimensions.logInfo("Using a vanilla DimensionType (or some other existing one) '{}' for dim {}", type, this.id);
                type = DimensionType.valueOf(this.dimensionTypeName);
            }
            catch (Exception e) { }

            if (type == null)
            {
                type = DimensionType.OVERWORLD;
                JustEnoughDimensions.logger.warn("Failed to get a DimensionType by the name '{}' for dim {}, falling back to DimensionType.OVERWORLD",
                        this.dimensionTypeName, this.id);
            }

            return type;
        }
        else
        {
            JustEnoughDimensions.logInfo("Registering a DimensionType with values: {}", this.getDescription());
            return DimensionType.register(this.name, this.suffix, this.id, this.providerClass, this.keepLoaded);
        }
    }

    public void writeToByteBuf(ByteBuf buf)
    {
        buf.writeInt(this.id);

        if (this.dimensionTypeName != null)
        {
            buf.writeByte(1);
            ByteBufUtils.writeUTF8String(buf, this.dimensionTypeName);
        }
        else
        {
            buf.writeByte(0);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        }
    }

    public static DimensionTypeEntry fromByteBuf(ByteBuf buf)
    {
        int id = buf.readInt();
        byte type = buf.readByte();

        if (type == (byte) 1)
        {
            DimensionTypeEntry entry = new DimensionTypeEntry(id, ByteBufUtils.readUTF8String(buf));
            return entry;
        }
        else
        {
            String name = ByteBufUtils.readUTF8String(buf);
            String suffix = ByteBufUtils.readUTF8String(buf);
            String providerClassName = ByteBufUtils.readUTF8String(buf);

            try
            {
                @SuppressWarnings("unchecked")
                Class<? extends WorldProvider> providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
                DimensionTypeEntry entry = new DimensionTypeEntry(id, name, suffix, false, providerClass);
                return entry;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to read dimension info from packet for dimension {}" +
                    " - WorldProvider class {} not found", id, providerClassName);
                return null;
            }
        }

    }

    public JsonObject toJson()
    {
        JsonObject dimensionType = new JsonObject();

        if (this.dimensionTypeName != null)
        {
            dimensionType.addProperty("vanilladimensiontype", this.dimensionTypeName);
        }
        else
        {
            dimensionType.addProperty("id", this.id);
            dimensionType.addProperty("name", this.name);
            dimensionType.addProperty("suffix", this.suffix);
            dimensionType.addProperty("keeploaded", this.keepLoaded);
            dimensionType.addProperty("worldprovider", getNameForWorldProvider(this.providerClass));
        }

        return dimensionType;
    }

    public String getDescription()
    {
        return String.format("{id: %d, name: \"%s\", suffix: \"%s\", keepLoaded: %s, WorldProvider: %s}",
                this.id, this.name, this.suffix, this.keepLoaded, getNameForWorldProvider(this.providerClass));
    }

    @Override
    public int compareTo(DimensionTypeEntry other)
    {
        if (this.getId() == other.getId())
        {
            return 0;
        }

        return this.getId() > other.getId() ? 1 : -1;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) { return true; }
        if (other == null) { return false; }
        if (getClass() != other.getClass()) { return false; }

        return this.getId() == ((DimensionTypeEntry) other).getId();
    }

    public static String getNameForWorldProvider(@Nonnull Class<? extends WorldProvider> clazz)
    {
        String provName = clazz.getName();

        // These ones are supported by their simple class names in this code
        if (provName.startsWith("net.minecraft.world.") ||
            provName.equals(WorldProviderSurfaceJED.class.getName()) ||
            provName.equals(WorldProviderHellJED.class.getName()) ||
            provName.equals(WorldProviderEndJED.class.getName()))
        {
            return clazz.getSimpleName();
        }

        return provName;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends WorldProvider> getProviderClass(String providerClassName)
    {
        Class<? extends WorldProvider> providerClass;

        if (providerClassName.equals("WorldProviderSurfaceJED"))
        {
            providerClass = WorldProviderSurfaceJED.class;
        }
        else if (providerClassName.equals("WorldProviderHellJED"))
        {
            providerClass = WorldProviderHellJED.class;
        }
        else if (providerClassName.equals("WorldProviderEndJED"))
        {
            providerClass = WorldProviderEndJED.class;
        }
        else if (providerClassName.equals("WorldProviderSurface"))
        {
            providerClass = WorldProviderSurface.class;
        }
        else if (providerClassName.equals("WorldProviderHell"))
        {
            providerClass = WorldProviderHell.class;
        }
        else if (providerClassName.equals("WorldProviderEnd"))
        {
            providerClass = WorldProviderEnd.class;
        }
        else
        {
            try
            {
                providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to get a WorldProvider class for '{}'", providerClassName);
                e.printStackTrace();
                return null;
            }
        }

        return providerClass;
    }
}
