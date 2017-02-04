package fi.dy.masa.justenoughdimensions.config;

import javax.annotation.Nonnull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

public class DimensionConfigEntry implements Comparable<DimensionConfigEntry>
{
    private final int id;
    private final String name;
    private final String suffix;
    private final boolean keepLoaded;
    private final Class<? extends WorldProvider> providerClass;
    private String dimensionTypeName = null;
    private boolean override;
    private boolean unregister;
    private String biome; // if != null, then use BiomeProviderSingle with this biome
    private JsonObject worldInfoJson;
    private JsonObject oneTimeWorldInfoJson;

    public DimensionConfigEntry(int id, String name, String suffix, boolean keepLoaded, @Nonnull Class<? extends WorldProvider> providerClass)
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

    public boolean getOverride()
    {
        return this.override;
    }

    public boolean getUnregister()
    {
        return this.unregister;
    }

    public Class<? extends WorldProvider> getProviderClass()
    {
        return this.providerClass;
    }

    public String getBiome()
    {
        return this.biome;
    }

    public void setDimensionTypeName(String typeName)
    {
        this.dimensionTypeName = typeName;
    }

    public void setOverride(boolean override)
    {
        this.override = override;
    }

    public void setUnregister(boolean unregister)
    {
        // Don't allow unregistering the overworld, or bad things will happen!
        this.unregister = unregister && this.id != 0;
    }

    public void setBiome(String biome)
    {
        this.biome = biome;
    }

    public DimensionConfigEntry setWorldInfoJson(JsonObject obj)
    {
        this.worldInfoJson = obj;
        return this;
    }

    public DimensionConfigEntry setOneTimeWorldInfoJson(JsonObject obj)
    {
        this.oneTimeWorldInfoJson = obj;
        return this;
    }

    public DimensionType registerDimensionType()
    {
        if (this.dimensionTypeName != null)
        {
            DimensionType type = null;
            try
            {
                type = DimensionType.valueOf(this.dimensionTypeName);
                JustEnoughDimensions.logInfo("Using a vanilla DimensionType (or some other existing one) '{}' for dim {}", type, this.id);
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
        ByteBufUtils.writeUTF8String(buf, this.name);
        ByteBufUtils.writeUTF8String(buf, this.suffix);
        ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        buf.writeBoolean(this.unregister);

        if (this.dimensionTypeName != null)
        {
            buf.writeByte(1);
            ByteBufUtils.writeUTF8String(buf, this.dimensionTypeName);
        }
        else
        {
            buf.writeByte(0);
        }
    }

    public static DimensionConfigEntry fromByteBuf(ByteBuf buf)
    {
        int id = buf.readInt();
        String name = ByteBufUtils.readUTF8String(buf);
        String suffix = ByteBufUtils.readUTF8String(buf);
        String providerClassName = ByteBufUtils.readUTF8String(buf);
        boolean unregister = buf.readBoolean();
        byte type = buf.readByte();
        String dimTypeName = type == 1 ? ByteBufUtils.readUTF8String(buf) : null;

        try
        {
            @SuppressWarnings("unchecked")
            Class<? extends WorldProvider> providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
            DimensionConfigEntry entry = new DimensionConfigEntry(id, name, suffix, false, providerClass);
            entry.setUnregister(unregister);
            entry.setDimensionTypeName(dimTypeName);
            return entry;
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.error("Failed to read dimension info from packet for dimension {}" +
                " - WorldProvider class {} not found", id, providerClassName);
            return null;
        }
    }

    public JsonObject toJson()
    {
        JsonObject jsonEntry = new JsonObject();
        jsonEntry.addProperty("dim", this.getId());

        if (this.override)
        {
            jsonEntry.addProperty("override", true);
        }

        if (this.unregister)
        {
            jsonEntry.addProperty("unregister", true);
        }

        if (this.biome != null)
        {
            jsonEntry.addProperty("biome", this.biome);
        }

        JsonObject worldType = new JsonObject();
        worldType.addProperty("name", this.name);
        worldType.addProperty("suffix", this.suffix);
        worldType.addProperty("keeploaded", this.keepLoaded);
        worldType.addProperty("worldprovider", getNameForWorldProvider(this.providerClass));
        jsonEntry.add("dimensiontype", worldType);

        if (this.dimensionTypeName != null)
        {
            jsonEntry.addProperty("vanilladimensiontype", this.dimensionTypeName);
        }

        this.copyJsonObject(jsonEntry, "worldinfo",         this.worldInfoJson);
        this.copyJsonObject(jsonEntry, "worldinfo_onetime", this.oneTimeWorldInfoJson);

        return jsonEntry;
    }

    private void copyJsonObject(JsonObject wrapper, String key, JsonObject obj)
    {
        if (obj != null)
        {
            try
            {
                // Serialize and deserialize as a way to make a copy
                Gson gson = new GsonBuilder().create();
                JsonParser parser = new JsonParser();
                JsonElement root = parser.parse(gson.toJson(obj));

                if (root != null && root.isJsonObject())
                {
                    wrapper.add(key, root.getAsJsonObject());
                }
                else
                {
                    JustEnoughDimensions.logger.error("Failed to convert a DimensionEntry into a JsonObject");
                }
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to convert a DimensionEntry into a JsonObject", e);
            }
        }
    }

    public String getDescription()
    {
        return String.format("{id: %d, name: \"%s\", suffix: \"%s\", keepLoaded: %s, WorldProvider: %s}",
                this.id, this.name, this.suffix, this.keepLoaded, getNameForWorldProvider(this.providerClass));
    }

    @Override
    public int compareTo(DimensionConfigEntry other)
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

        return this.getId() == ((DimensionConfigEntry) other).getId();
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
}
