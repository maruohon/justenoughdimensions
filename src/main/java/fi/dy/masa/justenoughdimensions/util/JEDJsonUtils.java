package fi.dy.masa.justenoughdimensions.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class JEDJsonUtils
{
    @Nullable
    public static JsonObject getOrCreateNestedObject(JsonObject parent, String key)
    {
        return getNestedObject(parent, key, true);
    }

    @Nullable
    public static JsonObject getNestedObject(JsonObject parent, String key, boolean create)
    {
        if (parent.has(key) == false || parent.get(key).isJsonObject() == false)
        {
            if (create == false)
            {
                return null;
            }

            JsonObject obj = new JsonObject();
            parent.add(key, obj);
            return obj;
        }
        else
        {
            return parent.get(key).getAsJsonObject();
        }
    }

    /**
     * If <b>obj</b> is not null, makes a copy of the object <b>obj</b> and adds it as <b>name</b> into <b>parent</b>
     * @param parent
     * @param name
     * @param obj
     */
    public static void copyJsonObject(JsonObject parent, String name, @Nullable JsonObject obj)
    {
        if (obj != null)
        {
            try
            {
                // Serialize and deserialize as a way to make a copy
                JsonElement root = deserialize(serialize(obj));

                if (root != null && root.isJsonObject())
                {
                    parent.add(name, root.getAsJsonObject());
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

    /**
     * Converts the given JsonElement into its String representation
     * @param element
     * @return
     */
    public static String serialize(@Nonnull JsonElement element)
    {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(element);
    }

    @Nullable
    public static JsonElement deserialize(String input) throws JsonParseException, JsonSyntaxException
    {
        JsonParser parser = new JsonParser();
        return parser.parse(input);
    }
}
