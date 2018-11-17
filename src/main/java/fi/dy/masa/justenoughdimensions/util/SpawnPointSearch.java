package fi.dy.masa.justenoughdimensions.util;

import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class SpawnPointSearch
{
    private final Type type;
    private final Integer minY;
    private final Integer maxY;

    private SpawnPointSearch(Type type, @Nullable Integer minY, @Nullable Integer maxY)
    {
        this.type = type;
        this.minY = minY;
        this.maxY = maxY;
    }

    public Type getType()
    {
        return this.type;
    }

    @Nullable
    public Integer getMinY()
    {
        return this.minY;
    }

    @Nullable
    public Integer getMaxY()
    {
        return this.maxY;
    }

    public static SpawnPointSearch fromJson(JsonObject obj)
    {
        Type type = Type.OVERWORLD;
        Integer minY = JEDJsonUtils.hasInteger(obj, "min_y") ? obj.get("min_y").getAsInt() : null;
        Integer maxY = JEDJsonUtils.hasInteger(obj, "max_y") ? obj.get("max_y").getAsInt() : null;

        if (JEDJsonUtils.hasString(obj, "type"))
        {
            type = Type.fromString(obj.get("type").getAsString());
        }
        else
        {
            JustEnoughDimensions.logger.warn("SpawnPointSearch: No 'type' specified, using 'overworld'");
        }

        return new SpawnPointSearch(type, minY, maxY);
    }

    @Override
    public String toString()
    {
        String strMin = this.minY != null ? this.minY.toString() : "<none>";
        String strMax = this.maxY != null ? this.maxY.toString() : "<none>";
        return String.format("{type: %s, min_y: %s, max_y: %s}", this.type.name().toLowerCase(), strMin, strMax);
    }

    public enum Type
    {
        OVERWORLD,
        CAVERN,
        NONE;

        public static Type fromString(String name)
        {
            for (Type type : values())
            {
                if (type.name().equalsIgnoreCase(name))
                {
                    return type;
                }
            }

            JustEnoughDimensions.logger.warn("SpawnPointSearch: Unknown type '{}' specified, falling back to 'overworld'", name);

            return OVERWORLD;
        }
    }
}
