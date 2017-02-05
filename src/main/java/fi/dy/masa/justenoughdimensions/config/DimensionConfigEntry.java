package fi.dy.masa.justenoughdimensions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import io.netty.buffer.ByteBuf;

public class DimensionConfigEntry implements Comparable<DimensionConfigEntry>
{
    private final int id;
    private boolean override;
    private boolean unregister;
    private String biome; // if != null, then use BiomeProviderSingle with this biome
    private JsonObject worldInfoJson;
    private JsonObject oneTimeWorldInfoJson;
    private DimensionTypeEntry dimensionTypeEntry;

    public DimensionConfigEntry(int id)
    {
        this.id = id;
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

    public String getBiome()
    {
        return this.biome;
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

    public boolean hasDimensionTypeEntry()
    {
        return this.getDimensionTypeEntry() != null;
    }

    public DimensionTypeEntry getDimensionTypeEntry()
    {
        return this.dimensionTypeEntry;
    }

    public void setDimensionTypeEntry(DimensionTypeEntry entry)
    {
        this.dimensionTypeEntry = entry;
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

    public void writeToByteBuf(ByteBuf buf)
    {
        buf.writeInt(this.id);
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

        if (this.dimensionTypeEntry != null)
        {
            jsonEntry.add("dimensiontype", this.dimensionTypeEntry.toJson());
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
        return String.format("{id: %d, override: %s, unregister: %s, biome: '%s', DimensionTypeEntry: [%s]}",
                this.id, this.override, this.unregister, this.biome,
                this.dimensionTypeEntry != null ? this.dimensionTypeEntry.getDescription() : "N/A");
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
}
