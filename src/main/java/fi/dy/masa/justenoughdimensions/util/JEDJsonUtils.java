package fi.dy.masa.justenoughdimensions.util;

import java.io.File;
import java.io.FileReader;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.util.math.Vec3d;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class JEDJsonUtils
{
    public static final Gson GSON = new GsonBuilder().create();
    public static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    public static boolean hasBoolean(JsonObject obj, String name)
    {
        JsonElement el = obj.get(name);

        if (el != null && el.isJsonPrimitive())
        {
            try
            {
                el.getAsBoolean();
                return true;
            }
            catch (Exception e) {}
        }

        return false;
    }

    public static boolean hasInteger(JsonObject obj, String name)
    {
        JsonElement el = obj.get(name);

        if (el != null && el.isJsonPrimitive())
        {
            try
            {
                el.getAsInt();
                return true;
            }
            catch (Exception e) {}
        }

        return false;
    }

    public static boolean hasDouble(JsonObject obj, String name)
    {
        JsonElement el = obj.get(name);

        if (el != null && el.isJsonPrimitive())
        {
            try
            {
                el.getAsDouble();
                return true;
            }
            catch (Exception e) {}
        }

        return false;
    }

    public static boolean hasString(JsonObject obj, String name)
    {
        JsonElement el = obj.get(name);

        if (el != null && el.isJsonPrimitive())
        {
            try
            {
                el.getAsString();
                return true;
            }
            catch (Exception e) {}
        }

        return false;
    }

    public static boolean hasObject(JsonObject obj, String name)
    {
        JsonElement el = obj.get(name);

        if (el != null && el.isJsonObject())
        {
            return true;
        }

        return false;
    }

    public static String getStringOrDefault(JsonObject obj, String name, String defaultValue, boolean allowEmpty)
    {
        if (obj.has(name) && obj.get(name).isJsonPrimitive())
        {
            try
            {
                String str = obj.get(name).getAsString();
                return allowEmpty == false && StringUtils.isBlank(str) ? defaultValue : str;
            }
            catch (Exception e) {}
        }

        return defaultValue;
    }

    public static byte getByteOrDefault(JsonObject obj, String name, byte defaultValue)
    {
        if (obj.has(name) && obj.get(name).isJsonPrimitive())
        {
            try
            {
                return obj.get(name).getAsByte();
            }
            catch (Exception e) {}
        }

        return defaultValue;
    }

    public static int getIntegerOrDefault(JsonObject obj, String name, int defaultValue)
    {
        if (obj.has(name) && obj.get(name).isJsonPrimitive())
        {
            try
            {
                return obj.get(name).getAsInt();
            }
            catch (Exception e) {}
        }

        return defaultValue;
    }

    public static float getFloatOrDefault(JsonObject obj, String name, float defaultValue)
    {
        if (obj.has(name) && obj.get(name).isJsonPrimitive())
        {
            try
            {
                return obj.get(name).getAsFloat();
            }
            catch (Exception e) {}
        }

        return defaultValue;
    }

    public static double getDoubleOrDefault(JsonObject obj, String name, double defaultValue)
    {
        if (obj.has(name) && obj.get(name).isJsonPrimitive())
        {
            try
            {
                return obj.get(name).getAsDouble();
            }
            catch (Exception e) {}
        }

        return defaultValue;
    }

    public static boolean getBooleanOrDefault(JsonObject obj, String name, boolean defaultValue)
    {
        if (obj.has(name) && obj.get(name).isJsonPrimitive())
        {
            try
            {
                return obj.get(name).getAsBoolean();
            }
            catch (Exception e) {}
        }

        return defaultValue;
    }

    public static boolean getBoolean(JsonObject obj, String name)
    {
        return getBooleanOrDefault(obj, name, false);
    }

    public static byte getByte(JsonObject obj, String name)
    {
        return getByteOrDefault(obj, name, (byte) 0);
    }

    public static int getInteger(JsonObject obj, String name)
    {
        return getIntegerOrDefault(obj, name, 0);
    }

    public static float getFloat(JsonObject obj, String name)
    {
        return getFloatOrDefault(obj, name, 0F);
    }

    public static double getDouble(JsonObject obj, String name)
    {
        return getDoubleOrDefault(obj, name, 0D);
    }

    public static String getString(JsonObject obj, String name)
    {
        return getStringOrDefault(obj, name, null, true);
    }

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

    public static Vec3d getVec3dOrDefault(JsonObject obj, String arrayName, Vec3d defaultValue)
    {
        if (obj.has(arrayName) && obj.get(arrayName).isJsonArray())
        {
            JsonArray arr = obj.get(arrayName).getAsJsonArray();

            try
            {
                Vec3d vec = new Vec3d(arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble());
                return vec;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to parse a Vec3d value '{}' from JSON", arrayName, e);
            }
        }

        return defaultValue;
    }

    // https://stackoverflow.com/questions/29786197/gson-jsonobject-copy-value-affected-others-jsonobject-instance
    @Nullable
    public static JsonObject deepCopy(@Nullable JsonObject jsonObject)
    {
        if (jsonObject != null)
        {
            JsonObject result = new JsonObject();

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet())
            {
                result.add(entry.getKey(), deepCopy(entry.getValue()));
            }

            return result;
        }

        return null;
    }

    @Nonnull
    public static JsonArray deepCopy(@Nonnull JsonArray jsonArray)
    {
        JsonArray result = new JsonArray();

        for (JsonElement e : jsonArray)
        {
            result.add(deepCopy(e));
        }

        return result;
    }

    @Nonnull
    public static JsonElement deepCopy(@Nonnull JsonElement jsonElement)
    {
        if (jsonElement.isJsonPrimitive() || jsonElement.isJsonNull())
        {
            return jsonElement; // these are immutable anyway
        }
        else if (jsonElement.isJsonObject())
        {
            return deepCopy(jsonElement.getAsJsonObject());
        }
        else if (jsonElement.isJsonArray())
        {
            return deepCopy(jsonElement.getAsJsonArray());
        }
        else
        {
            throw new UnsupportedOperationException("Unsupported element: " + jsonElement);
        }
    }

    /**
     * Converts the given JsonElement into its String representation
     * @param element
     * @return
     */
    public static String serialize(@Nonnull JsonElement element)
    {
        return GSON.toJson(element);
    }

    @Nullable
    public static JsonElement deserialize(String input) throws JsonParseException, JsonSyntaxException
    {
        JsonParser parser = new JsonParser();
        return parser.parse(input);
    }

    @Nullable
    public static JsonElement parseJsonFile(File file)
    {
        if (file != null && file.exists() && file.isFile() && file.canRead())
        {
            String fileName = file.getAbsolutePath();

            try
            {
                JsonParser parser = new JsonParser();
                return parser.parse(new FileReader(file));
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to parse the JSON file '{}'", fileName, e);
            }
        }

        return null;
    }
}
