package fi.dy.masa.justenoughdimensions.config;

import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.world.WorldProviderEndJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;
import io.netty.buffer.ByteBuf;

public class DimensionTypeEntry implements Comparable<DimensionTypeEntry>
{
    private int dimensionTypeId;
    private String name;
    private String suffix;
    private boolean keepLoaded;
    private Class<? extends WorldProvider> providerClass;
    private String dimensionTypeName;
    private boolean forceRegister;
    private boolean allowDifferentId = true;
    private boolean requireExactMatch;
    private static final Field field_DimensionType_clazz = ReflectionHelper.findField(DimensionType.class, "field_186077_g", "clazz");

    @SuppressWarnings("unchecked")
    @Nullable
    public static Class<? extends WorldProvider> getProviderClassFrom(DimensionType type)
    {
        try
        {
            return (Class<? extends WorldProvider>) field_DimensionType_clazz.get(type);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public DimensionTypeEntry(String existingDimensionTypeName)
    {
        this.dimensionTypeName = existingDimensionTypeName;
    }

    public DimensionTypeEntry(int dimTypeId, String name, String suffix, boolean keepLoaded, @Nonnull Class<? extends WorldProvider> providerClass)
    {
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
     * (except for the name and suffix, which are not checked) but a different ID can be used
     * instead of registering a new entry.
     * @param allowDifferentId
     * @return
     */
    public DimensionTypeEntry setAllowDifferentId(boolean allowDifferentId)
    {
        this.allowDifferentId = allowDifferentId;
        return this;
    }

    /**
     * Set whether or not an exact match of an existing DimensionType is required
     * to avoid registering a new entry.
     * @param requireExactMatch
     * @return
     */
    public DimensionTypeEntry setRequireExactMatch(boolean requireExactMatch)
    {
        this.requireExactMatch = requireExactMatch;
        return this;
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

    public DimensionType getOrRegisterDimensionType(int dimension)
    {
        if (this.forceRegister == false)
        {
            DimensionType type = null;

            // Try to find a suitable existing entry,
            // to try to avoid modifying the DimensionType enum unnecessarily.
            for (DimensionType tmp : DimensionType.values())
            {
                if (tmp.shouldLoadSpawn() == this.keepLoaded && getProviderClassFrom(tmp) == this.providerClass)
                {
                    if (this.requireExactMatch)
                    {
                        if (tmp.getId() == this.dimensionTypeId &&
                            tmp.getSuffix().equals(this.suffix) &&
                            tmp.getName().equals(this.name))
                        {
                            type = tmp;
                            break;
                        }
                    }
                    else if ((tmp.getId() == this.dimensionTypeId || this.allowDifferentId))
                    {
                        type = tmp;

                        // "Exact"/best match, stop searching
                        if (tmp.getId() == this.dimensionTypeId)
                        {
                            break;
                        }
                    }
                }
            }

            if (type != null)
            {
                JustEnoughDimensions.logInfo("Using an existing DimensionType '{}', for dimension {}", type, dimension);
                return type;
            }
        }

        return this.registerDimensionType(dimension);
    }

    private DimensionType registerDimensionType(int dimension)
    {
        if (this.dimensionTypeName != null)
        {
            DimensionType type = null;

            try
            {
                type = DimensionType.byName(this.dimensionTypeName);
                JustEnoughDimensions.logInfo("Using a vanilla DimensionType (or some other existing one) '{}' for dimension {}", type, dimension);
            }
            catch (IllegalArgumentException e)
            {
                type = DimensionType.OVERWORLD;
                JustEnoughDimensions.logger.warn("Failed to get a DimensionType by the name '{}' for dimension {}, falling back to {}",
                        this.dimensionTypeName, dimension, type);
            }

            return type;
        }
        else
        {
            JustEnoughDimensions.logInfo("Registering a new DimensionType with values '{}' for dimension {}", this.getDescription(), dimension);
            return DimensionType.register(this.name, this.suffix, this.dimensionTypeId, this.providerClass, this.keepLoaded);
        }
    }

    public void writeToByteBuf(ByteBuf buf)
    {
        if (this.dimensionTypeName != null)
        {
            buf.writeByte(1);
            ByteBufUtils.writeUTF8String(buf, this.dimensionTypeName);
        }
        else
        {
            buf.writeByte(0);
            buf.writeInt(this.dimensionTypeId);
            buf.writeBoolean(this.forceRegister);
            buf.writeBoolean(this.allowDifferentId);
            buf.writeBoolean(this.requireExactMatch);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        }
    }

    public static DimensionTypeEntry fromByteBuf(ByteBuf buf)
    {
        final byte type = buf.readByte();

        if (type == (byte) 1)
        {
            DimensionTypeEntry entry = new DimensionTypeEntry(ByteBufUtils.readUTF8String(buf));
            return entry;
        }
        else
        {
            final int dimTypeId = buf.readInt();
            boolean forceRegister = buf.readBoolean();
            boolean allowDifferentId = buf.readBoolean();
            boolean requireExactMatch = buf.readBoolean();
            String name = ByteBufUtils.readUTF8String(buf);
            String suffix = ByteBufUtils.readUTF8String(buf);
            String providerClassName = ByteBufUtils.readUTF8String(buf);

            try
            {
                @SuppressWarnings("unchecked")
                Class<? extends WorldProvider> providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
                DimensionTypeEntry entry = new DimensionTypeEntry(dimTypeId, name, suffix, false, providerClass);
                entry.setForceRegister(forceRegister);
                entry.setAllowDifferentId(allowDifferentId);
                entry.setRequireExactMatch(requireExactMatch);
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

    @Nonnull
    public static DimensionTypeEntry fromJson(int dimension, @Nonnull JsonObject objDimType)
    {
        String existing = JEDJsonUtils.getStringOrDefault(objDimType, "existing_dimensiontype", null, false);

        if (existing != null)
        {
            JustEnoughDimensions.logInfo("Using an existing DimensionType '{}' for dimension {}", existing, dimension);
            return new DimensionTypeEntry(existing);
        }

        int dimensionTypeId = JEDJsonUtils.getIntegerOrDefault(objDimType, "id", dimension);
        String name =   JEDJsonUtils.getStringOrDefault(objDimType, "name", "DIM" + dimension, false);
        String suffix = JEDJsonUtils.getStringOrDefault(objDimType, "suffix", "_dim" + dimension, false);

        boolean keepLoaded =        JEDJsonUtils.getBooleanOrDefault(objDimType, "keeploaded", false);
        boolean forceRegister =     JEDJsonUtils.getBooleanOrDefault(objDimType, "force_register", false);
        boolean allowDifferentId =  JEDJsonUtils.getBooleanOrDefault(objDimType, "allow_different_id", true);
        boolean requireExactMatch = JEDJsonUtils.getBooleanOrDefault(objDimType, "require_exact_match", false);

        Class<? extends WorldProvider> providerClass = WorldProviderSurfaceJED.class;
        String providerName = "";

        if (objDimType.has("worldprovider") && objDimType.get("worldprovider").isJsonPrimitive())
        {
            providerName = objDimType.get("worldprovider").getAsString();

            // Don't allow using the vanilla surface or hell providers for the vanilla end dimension,
            // because that will lead to a crash in the teleport code (null returned from getSpawnCoordinate() for
            // other vanilla providers than the End).
            if (dimension == 1)
            {
                if (providerName.equals("WorldProviderSurface") || providerName.equals("net.minecraft.world.WorldProviderSurface"))
                {
                    providerName = WorldProviderSurfaceJED.class.getSimpleName();
                    JustEnoughDimensions.logger.warn("Changing the provider for DIM1 to {} to prevent a vanilla crash", providerName);
                }
                else if (providerName.equals("WorldProviderHell") || providerName.equals("net.minecraft.world.WorldProviderHell"))
                {
                    providerName = WorldProviderHellJED.class.getSimpleName();
                    JustEnoughDimensions.logger.warn("Changing the provider for DIM1 to {} to prevent a vanilla crash", providerName);
                }
            }

            providerClass = getProviderClass(providerName);
        }

        if (providerClass == null)
        {
            providerClass = WorldProviderSurfaceJED.class;

            JustEnoughDimensions.logger.warn("Failed to get a WorldProvider for name '{}', using {} as a fall-back",
                    providerName, getNameForWorldProvider(providerClass));
        }

        DimensionTypeEntry entry = new DimensionTypeEntry(dimensionTypeId, name, suffix, keepLoaded, providerClass);
        entry.setForceRegister(forceRegister);
        entry.setAllowDifferentId(allowDifferentId);
        entry.setRequireExactMatch(requireExactMatch);

        return entry;
    }

    public JsonObject toJson()
    {
        JsonObject dimensionType = new JsonObject();

        if (this.dimensionTypeName != null)
        {
            dimensionType.addProperty("existing_dimensiontype", this.dimensionTypeName);
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

            if (this.requireExactMatch)
            {
                dimensionType.addProperty("require_exact_match", this.requireExactMatch);
            }
        }

        return dimensionType;
    }

    public String getDescription()
    {
        if (this.dimensionTypeName != null)
        {
            return String.format("{existing_dimensiontype=%s}", this.dimensionTypeName);
        }
        else
        {
            return String.format("{id=%d,name=\"%s\",suffix=\"%s\",keepLoaded=%s,WorldProvider:\"%s\",force_register=%s,allow_different_id=%s}",
                    this.dimensionTypeId, this.name, this.suffix, this.keepLoaded,
                    getNameForWorldProvider(this.providerClass), this.forceRegister, this.allowDifferentId);
        }
    }

    @Override
    public int compareTo(DimensionTypeEntry other)
    {
        if (this.providerClass == other.providerClass)
        {
            return 0;
        }

        return this.dimensionTypeId > other.dimensionTypeId ? 1 : -1;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + this.dimensionTypeId;
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

    @Nullable
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
