package fi.dy.masa.justenoughdimensions.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;
import io.netty.buffer.ByteBuf;

public class DimensionConfigEntry implements Comparable<DimensionConfigEntry>
{
    private final int dimension;
    private boolean override;
    private boolean unregister;
    private boolean disableTeleportingFrom;
    private boolean disableTeleportingTo;
    private boolean isTemporaryDimension;
    private boolean normalBiomes;
    private boolean shouldLoadOnStart;
    @Nullable private String biome; // if != null, then use BiomeProviderSingle with this biome
    @Nullable private String biomeProvider; // if != null, then try to construct a BiomeProvider by that class name
    @Nullable private String worldTemplate;
    @Nullable private JsonObject jedTag;
    @Nullable private JsonObject worldInfoJson;
    @Nullable private JsonObject oneTimeWorldInfoJson;
    @Nullable private JsonObject spawnStructureJson;
    @Nullable private DimensionTypeEntry dimensionTypeEntry;

    public DimensionConfigEntry(int id)
    {
        this.dimension = id;
    }

    public int getDimension()
    {
        return this.dimension;
    }

    public boolean getOverride()
    {
        return this.override;
    }

    public boolean getUnregister()
    {
        return this.unregister;
    }

    public boolean getShouldLoadOnStart()
    {
        return this.shouldLoadOnStart;
    }

    public boolean shouldUseNormalBiomes()
    {
        return this.normalBiomes;
    }

    public boolean getDisableTeleportingFrom()
    {
        return this.disableTeleportingFrom;
    }

    public boolean getDisableTeleportingTo()
    {
        return this.disableTeleportingTo;
    }

    public boolean isTemporaryDimension()
    {
        return this.isTemporaryDimension;
    }

    @Nullable
    public String getBiome()
    {
        return this.biome;
    }

    @Nullable
    public String getBiomeProvider()
    {
        return this.biomeProvider;
    }

    @Nullable
    public String getWorldTemplate()
    {
        return this.worldTemplate;
    }

    public void setOverride(boolean override)
    {
        this.override = override;
    }

    public void setUnregister(boolean unregister)
    {
        // Don't allow unregistering the overworld, or bad things will happen!
        this.unregister = unregister && this.dimension != 0;
    }

    public boolean hasDimensionTypeEntry()
    {
        return this.getDimensionTypeEntry() != null;
    }

    @Nullable
    public DimensionTypeEntry getDimensionTypeEntry()
    {
        return this.dimensionTypeEntry;
    }

    public void setDimensionTypeEntry(DimensionTypeEntry entry)
    {
        this.dimensionTypeEntry = entry;
    }

    @Nullable
    public JsonObject getWorldInfoJson()
    {
        return this.worldInfoJson;
    }

    @Nullable
    public JsonObject getOneTimeWorldInfoJson()
    {
        return this.oneTimeWorldInfoJson;
    }

    @Nullable
    public JsonObject getSpawnStructureJson()
    {
        return this.spawnStructureJson;
    }

    public void writeToByteBuf(ByteBuf buf)
    {
        buf.writeInt(this.dimension);
        buf.writeBoolean(this.unregister);
        buf.writeBoolean(this.override);

        if (this.dimensionTypeEntry != null)
        {
            buf.writeByte(1);
            this.dimensionTypeEntry.writeToByteBuf(buf);
        }
        else
        {
            buf.writeByte(0);
        }
    }

    public static DimensionConfigEntry fromByteBuf(ByteBuf buf)
    {
        DimensionConfigEntry entry = new DimensionConfigEntry(buf.readInt());
        entry.setUnregister(buf.readBoolean());
        entry.setOverride(buf.readBoolean());

        if (buf.readByte() != 0)
        {
            entry.dimensionTypeEntry = DimensionTypeEntry.fromByteBuf(buf);
        }

        return entry;
    }

    @Nonnull
    public static DimensionConfigEntry fromJson(int dimension, @Nonnull JsonObject obj)
    {
        DimensionConfigEntry entry = new DimensionConfigEntry(dimension);

        entry.override =   JEDJsonUtils.getBooleanOrDefault(obj, "override", false);
        entry.unregister = JEDJsonUtils.getBooleanOrDefault(obj, "unregister", false) && dimension != 0;
        entry.shouldLoadOnStart      = JEDJsonUtils.getBooleanOrDefault(obj, "load_on_start", false);
        entry.normalBiomes           = JEDJsonUtils.getBooleanOrDefault(obj, "normal_biomes", false);
        entry.disableTeleportingFrom = JEDJsonUtils.getBooleanOrDefault(obj, "disable_teleporting_from", false);
        entry.disableTeleportingTo =   JEDJsonUtils.getBooleanOrDefault(obj, "disable_teleporting_to", false);
        entry.isTemporaryDimension =   JEDJsonUtils.getBooleanOrDefault(obj, "temporary_dimension", false);
        entry.biome         = JEDJsonUtils.getStringOrDefault(obj, "biome", null, false);
        entry.biomeProvider = JEDJsonUtils.getStringOrDefault(obj, "biomeprovider", null, false);
        entry.worldTemplate = JEDJsonUtils.getStringOrDefault(obj, "world_template", null, false);

        if (obj.has("dimensiontype") && obj.get("dimensiontype").isJsonObject())
        {
            JsonObject objDimType = obj.get("dimensiontype").getAsJsonObject();
            entry.setDimensionTypeEntry(DimensionTypeEntry.fromJson(dimension, objDimType));
        }

        entry.worldInfoJson         = JEDJsonUtils.getNestedObject(obj, "worldinfo", false);
        entry.oneTimeWorldInfoJson  = JEDJsonUtils.getNestedObject(obj, "worldinfo_onetime", false);
        entry.spawnStructureJson    = JEDJsonUtils.getNestedObject(obj, "spawn_structure", false);

        if (obj.has("jed") && obj.get("jed").isJsonObject())
        {
            JsonObject objJed = obj.get("jed").getAsJsonObject();

            if (objJed.size() > 0)
            {
                entry.jedTag = objJed;
                JEDWorldProperties.createAndSetPropertiesForDimension(dimension, objJed);
            }
        }

        return entry;
    }

    public JsonObject toJson()
    {
        JsonObject jsonEntry = new JsonObject();
        jsonEntry.addProperty("dim", this.getDimension());

        if (this.override)
        {
            jsonEntry.addProperty("override", true);
        }

        if (this.unregister)
        {
            jsonEntry.addProperty("unregister", true);
        }

        if (this.shouldLoadOnStart)
        {
            jsonEntry.addProperty("load_on_start", true);
        }

        if (this.normalBiomes)
        {
            jsonEntry.addProperty("normal_biomes", true);
        }

        if (this.disableTeleportingFrom)
        {
            jsonEntry.addProperty("disable_teleporting_from", true);
        }

        if (this.disableTeleportingTo)
        {
            jsonEntry.addProperty("disable_teleporting_to", true);
        }

        if (this.isTemporaryDimension)
        {
            jsonEntry.addProperty("temporary_dimension", true);
        }

        if (this.biome != null)
        {
            jsonEntry.addProperty("biome", this.biome);
        }

        if (this.biomeProvider != null)
        {
            jsonEntry.addProperty("biomeprovider", this.biomeProvider);
        }

        if (this.worldTemplate != null)
        {
            jsonEntry.addProperty("world_template", this.worldTemplate);
        }

        if (this.dimensionTypeEntry != null)
        {
            jsonEntry.add("dimensiontype", this.dimensionTypeEntry.toJson());
        }

        if (this.jedTag != null)
        {
            jsonEntry.add("jed",                JEDJsonUtils.deepCopy(this.jedTag));
        }

        if (this.worldInfoJson != null)
        {
            jsonEntry.add("worldinfo",          JEDJsonUtils.deepCopy(this.worldInfoJson));
        }

        if (this.oneTimeWorldInfoJson != null)
        {
            jsonEntry.add("worldinfo_onetime",  JEDJsonUtils.deepCopy(this.oneTimeWorldInfoJson));
        }

        if (this.spawnStructureJson != null)
        {
            jsonEntry.add("spawn_structure",  JEDJsonUtils.deepCopy(this.spawnStructureJson));
        }

        return jsonEntry;
    }

    public String getDescription()
    {
        return String.format("{id=%d,override=%s,unregister=%s,load_on_start=%s,biome=%s,biomeprovider=%s,world_template=%s," +
                             "disable_teleporting_from=%s,disable_teleporting_to=%s,temporary_dimension=%s,DimensionTypeEntry:[%s]}",
                this.dimension, this.override, this.unregister, this.shouldLoadOnStart, this.biome, this.biomeProvider,
                this.worldTemplate, this.disableTeleportingFrom, this.disableTeleportingTo, this.isTemporaryDimension,
                this.dimensionTypeEntry != null ? this.dimensionTypeEntry.getDescription() : "N/A");
    }

    @Override
    public int compareTo(DimensionConfigEntry other)
    {
        if (this.getDimension() == other.getDimension())
        {
            return 0;
        }

        return this.getDimension() > other.getDimension() ? 1 : -1;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + dimension;
        return result;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) { return true; }
        if (other == null) { return false; }
        if (getClass() != other.getClass()) { return false; }

        return this.getDimension() == ((DimensionConfigEntry) other).getDimension();
    }
}
