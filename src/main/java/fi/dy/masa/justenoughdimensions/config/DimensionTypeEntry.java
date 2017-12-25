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
    private final int dimension;
    private final int dimensionTypeId;
    private final String name;
    private final String suffix;
    private final boolean keepLoaded;
    private final Class<? extends WorldProvider> providerClass;
    private final String dimensionTypeName;
    private boolean forceRegister;
    private boolean allowDifferentId = true;

    public DimensionTypeEntry(int dimension, int dimTypeId, String vanillaDimensionTypeName)
    {
        this.dimension = dimension;
        this.dimensionTypeId = dimTypeId;
        this.dimensionTypeName = vanillaDimensionTypeName;
        this.name = null;
        this.suffix = null;
        this.keepLoaded = false;
        this.providerClass = null;
    }

    public DimensionTypeEntry(int dimension, int dimTypeId, String name, String suffix, boolean keepLoaded, @Nonnull Class<? extends WorldProvider> providerClass)
    {
        this.dimension = dimension;
        this.dimensionTypeId = dimTypeId;
        this.name = name;
        this.suffix = suffix;
        this.keepLoaded = dimTypeId == 0 ? true : keepLoaded;
        this.providerClass = providerClass;
        this.dimensionTypeName = null;
    }

    /**
     * Set whether or not a new DimensionType should always be registered,
     * or can an existing similar DimensionType be used.
     * @param forceRegister
     * @return
     */
    public DimensionTypeEntry setForceRegister(boolean forceRegister)
    {
        this.forceRegister = forceRegister;
        return this;
    }

    /**
     * Set whether or not an existing DimensionType with identical other values,
     * (except for the name, which is not checked) but a different ID can be used
     * instead of registering a new entry.
     * @param allowDifferentId
     * @return
     */
    public DimensionTypeEntry setAllowDifferentId(boolean allowDifferentId)
    {
        this.allowDifferentId = allowDifferentId;
        return this;
    }

    public int getDimension()
    {
        return this.dimension;
    }

    public int getDimensionTypeId()
    {
        return this.dimensionTypeId;
    }

    public String getDimensionTypeName()
    {
        return this.name;
    }

    public String getDimensionTypeSuffix()
    {
        return this.suffix;
    }

    public boolean getDimensionTypeKeepLoaded()
    {
        return this.keepLoaded;
    }

    public Class<? extends WorldProvider> getProviderClass()
    {
        return this.providerClass;
    }

    public DimensionType getOrRegisterDimensionType()
    {
        DimensionType type = null;

        if (this.forceRegister == false)
        {
            // Try to find a suitable existing entry,
            // to try to avoid modifying the DimensionType enum unnecessarily.
            for (DimensionType tmp : DimensionType.values())
            {
                if (tmp.shouldLoadSpawn() == this.keepLoaded &&
                    //tmp.getSuffix().equals(this.suffix) &&
                    (this.allowDifferentId || tmp.getId() == this.dimensionTypeId) &&
                    tmp.createDimension().getClass() == this.providerClass)
                {
                    JustEnoughDimensions.logInfo("Using an existing DimensionType {}, for dimension {}", tmp, this.dimension);
                    type = tmp;
                    break;
                }
            }
        }

        return type != null ? type : this.registerDimensionType();
    }

    private DimensionType registerDimensionType()
    {
        if (this.dimensionTypeName != null)
        {
            DimensionType type = null;

            try
            {
                type = DimensionType.valueOf(this.dimensionTypeName);
                JustEnoughDimensions.logInfo("Using a vanilla DimensionType (or some other existing one) '{}' for dimension {}", type, this.dimension);
            }
            catch (Exception e) { }

            if (type == null)
            {
                type = DimensionType.OVERWORLD;
                JustEnoughDimensions.logger.warn("Failed to get a DimensionType by the name '{}' for dimension {}, falling back to {}",
                        this.dimensionTypeName, this.dimension, type);
            }

            return type;
        }
        else
        {
            JustEnoughDimensions.logInfo("Registering a new DimensionType with values '{}' for dimension {}", this.getDescription(), this.dimension);
            return DimensionType.register(this.name, this.suffix, this.dimensionTypeId, this.providerClass, this.keepLoaded);
        }
    }

    public void writeToByteBuf(ByteBuf buf)
    {
        buf.writeInt(this.dimension);
        buf.writeInt(this.dimensionTypeId);

        if (this.dimensionTypeName != null)
        {
            buf.writeByte(1);
            ByteBufUtils.writeUTF8String(buf, this.dimensionTypeName);
        }
        else
        {
            buf.writeByte(0);
            buf.writeBoolean(this.forceRegister);
            buf.writeBoolean(this.allowDifferentId);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        }
    }

    public static DimensionTypeEntry fromByteBuf(ByteBuf buf)
    {
        final int dimension = buf.readInt();
        final int dimTypeId = buf.readInt();
        final byte type = buf.readByte();

        if (type == (byte) 1)
        {
            DimensionTypeEntry entry = new DimensionTypeEntry(dimension, dimTypeId, ByteBufUtils.readUTF8String(buf));
            return entry;
        }
        else
        {
            boolean forceRegister = buf.readBoolean();
            boolean allowDifferentId = buf.readBoolean();
            String name = ByteBufUtils.readUTF8String(buf);
            String suffix = ByteBufUtils.readUTF8String(buf);
            String providerClassName = ByteBufUtils.readUTF8String(buf);

            try
            {
                @SuppressWarnings("unchecked")
                Class<? extends WorldProvider> providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
                DimensionTypeEntry entry = new DimensionTypeEntry(dimension, dimTypeId, name, suffix, false, providerClass);
                entry.setForceRegister(forceRegister).setAllowDifferentId(allowDifferentId);
                return entry;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to read dimension info from packet for dimension {}" +
                    " - WorldProvider class {} not found", dimTypeId, providerClassName);
                return null;
            }
        }

    }

    public JsonObject toJson()
    {
        JsonObject dimensionType = new JsonObject();

        if (this.dimensionTypeName != null)
        {
            dimensionType.addProperty("vanilla_dimensiontype", this.dimensionTypeName);
        }
        else
        {
            dimensionType.addProperty("id", this.dimensionTypeId);
            dimensionType.addProperty("name", this.name);
            dimensionType.addProperty("suffix", this.suffix);
            dimensionType.addProperty("keeploaded", this.keepLoaded);
            dimensionType.addProperty("worldprovider", getNameForWorldProvider(this.providerClass));

            // only include these when they are not at the default value
            if (this.forceRegister)
            {
                dimensionType.addProperty("force_register", this.forceRegister);
            }

            if (this.allowDifferentId == false)
            {
                dimensionType.addProperty("allow_different_id", this.allowDifferentId);
            }
        }

        return dimensionType;
    }

    public String getDescription()
    {
        return String.format("{id=%d,name=\"%s\",suffix=\"%s\",keepLoaded=%s,WorldProvider:\"%s\",force_register=%s,allow_different_id=%s}",
                this.dimensionTypeId, this.name, this.suffix, this.keepLoaded,
                getNameForWorldProvider(this.providerClass), this.forceRegister, this.allowDifferentId);
    }

    @Override
    public int compareTo(DimensionTypeEntry other)
    {
        if (this.providerClass == other.providerClass)
        {
            return 0;
        }

        return this.dimension > other.dimension ? 1 : -1;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + this.dimension;
        return result;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) { return true; }
        if (other == null) { return false; }
        if (getClass() != other.getClass()) { return false; }

        return this.providerClass == ((DimensionTypeEntry) other).providerClass;
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
                JustEnoughDimensions.logger.error("Failed to get a WorldProvider class for name '{}'", providerClassName, e);
                return null;
            }
        }

        return providerClass;
    }
}
