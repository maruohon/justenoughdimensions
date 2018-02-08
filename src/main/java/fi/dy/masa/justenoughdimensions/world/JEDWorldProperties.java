package fi.dy.masa.justenoughdimensions.world;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;

public class JEDWorldProperties
{
    private static final Map<Integer, JEDWorldProperties> PROPERTIES = new HashMap<>();

    private JsonObject fullJEDTag;
    private JsonObject colorData;
    private boolean forceGameMode;
    private boolean useCustomDayCycle;
    private int dayLength = 12000;
    private int nightLength = 12000;
    private int cloudHeight = 128;
    private int skyRenderType;
    private int skyDisableFlags;
    private Vec3d skyColor = null;
    private Vec3d cloudColor = null;
    private Vec3d fogColor = null;
    private String skyRenderer = null;
    private float[] customLightBrightnessTable = null;
    private Boolean canRespawnHere = null;
    private Integer respawnDimension = null;

    @Nullable
    public static JEDWorldProperties getPropertiesIfExists(World world)
    {
        return getPropertiesIfExists(world.provider.getDimension());
    }

    @Nullable
    public static JEDWorldProperties getPropertiesIfExists(int dimension)
    {
        return PROPERTIES.get(dimension);
    }

    /**
     * Gets the properties for the requested dimension.
     * If there no properties for that dimension, then a new default instance is returned.
     * NOTE: If the default instance is created and returned, it is NOT added to the map.
     * @param dimension
     * @return the properties for the requested dimension, or a new default instance (which is NOT added to the map!)
     */
    public static JEDWorldProperties getOrCreateProperties(int dimension)
    {
        return getOrCreateProperties(dimension, null);
    }

    /**
     * Gets the properties for the requested dimension.
     * If there no properties for that dimension, then a new instance is created and returned,
     * based on the JsonObject passed in.
     * NOTE: If a new instance is created and returned, it is NOT added to the map.
     * @param dimension
     * @param obj the properties to set for the created instance
     * @return the properties for the requested dimension, or a new default instance (which is NOT added to the map!)
     */
    public static JEDWorldProperties getOrCreateProperties(int dimension, @Nullable JsonObject obj)
    {
        JEDWorldProperties props = PROPERTIES.get(dimension);

        if (props == null)
        {
            props = obj != null ? new JEDWorldProperties(obj) : new JEDWorldProperties();
        }

        return props;
    }

    public static JEDWorldProperties createAndSetPropertiesForDimension(int dimension, @Nonnull JsonObject obj)
    {
        JEDWorldProperties props = new JEDWorldProperties(obj);
        PROPERTIES.put(dimension, props);
        return props;
    }

    public static void removePropertiesFrom(int dimension)
    {
        PROPERTIES.remove(dimension);
    }

    public static void clearWorldProperties()
    {
        PROPERTIES.clear();
    }

    private JEDWorldProperties()
    {
    }

    private JEDWorldProperties(JsonObject obj)
    {
        this.fullJEDTag = JEDJsonUtils.deepCopy(obj);
        this.colorData = JEDJsonUtils.getNestedObject(obj, "Colors", false);

        if (JEDJsonUtils.hasBoolean(obj, "ForceGameMode"))      { this.forceGameMode        = JEDJsonUtils.getBoolean(obj, "ForceGameMode"); }
        if (JEDJsonUtils.hasBoolean(obj, "CustomDayCycle"))     { this.useCustomDayCycle    = JEDJsonUtils.getBoolean(obj, "CustomDayCycle"); }
        if (JEDJsonUtils.hasBoolean(obj, "CanRespawnHere"))     { this.canRespawnHere       = JEDJsonUtils.getBoolean(obj, "CanRespawnHere"); }
        
        if (JEDJsonUtils.hasInteger(obj, "DayLength"))          { this.dayLength            = JEDJsonUtils.getInteger(obj, "DayLength"); }
        if (JEDJsonUtils.hasInteger(obj, "NightLength"))        { this.nightLength          = JEDJsonUtils.getInteger(obj, "NightLength"); }
        if (JEDJsonUtils.hasInteger(obj, "CloudHeight"))        { this.cloudHeight          = JEDJsonUtils.getInteger(obj, "CloudHeight"); }
        if (JEDJsonUtils.hasInteger(obj, "SkyRenderType"))      { this.skyRenderType        = JEDJsonUtils.getInteger(obj, "SkyRenderType"); }
        if (JEDJsonUtils.hasInteger(obj, "SkyDisableFlags"))    { this.skyDisableFlags      = JEDJsonUtils.getInteger(obj, "SkyDisableFlags"); }
        if (JEDJsonUtils.hasInteger(obj, "RespawnDimension"))   { this.respawnDimension     = JEDJsonUtils.getInteger(obj, "RespawnDimension"); }

        if (JEDJsonUtils.hasString(obj, "SkyRenderer"))         { this.skyRenderer          = JEDJsonUtils.getString(obj, "SkyRenderer"); }

        if (JEDJsonUtils.hasString(obj, "SkyColor"))   { this.skyColor   = JEDStringUtils.hexStringToColor(JEDJsonUtils.getString(obj, "SkyColor")); }
        if (JEDJsonUtils.hasString(obj, "FogColor"))   { this.fogColor   = JEDStringUtils.hexStringToColor(JEDJsonUtils.getString(obj, "FogColor")); }
        if (JEDJsonUtils.hasString(obj, "CloudColor")) { this.cloudColor = JEDStringUtils.hexStringToColor(JEDJsonUtils.getString(obj, "CloudColor")); }

        JsonElement el = obj.get("LightBrightness");

        if (el != null && el.isJsonArray())
        {
            JsonArray arr = el.getAsJsonArray();

            if (arr.size() == 16)
            {
                try
                {
                    float[] light = new float[16];

                    for (int i = 0; i < 16; i++)
                    {
                        this.customLightBrightnessTable[i] = arr.get(i).getAsFloat();
                    }

                    this.customLightBrightnessTable = light;
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.warn("Failed to read a light brightness table from JSON", e);
                }
            }
        }

        if (this.dayLength   <= 0) { this.dayLength = 1; }
        if (this.nightLength <= 0) { this.nightLength = 1; }
    }

    public JsonObject getJEDPropsForClientSync()
    {
        JsonObject obj = new JsonObject();

        if (this.dayLength != 12000)    { obj.add("DayLength",          new JsonPrimitive(this.dayLength)); }
        if (this.nightLength != 12000)  { obj.add("NightLength",        new JsonPrimitive(this.nightLength)); }
        if (this.cloudHeight != 128)    { obj.add("CloudHeight",        new JsonPrimitive(this.cloudHeight)); }
        if (this.skyRenderer != null)   { obj.add("SkyRenderer",        new JsonPrimitive(this.skyRenderer)); }
        if (this.skyRenderType != 0)    { obj.add("SkyRenderType",      new JsonPrimitive(this.skyRenderType)); }
        if (this.skyDisableFlags != 0)  { obj.add("SkyDisableFlags",    new JsonPrimitive(this.skyDisableFlags)); }
        if (this.useCustomDayCycle)     { obj.add("CustomDayCycle",     new JsonPrimitive(this.useCustomDayCycle)); }
        if (this.skyColor != null)      { obj.add("SkyColor",           new JsonPrimitive(JEDStringUtils.colorToHexString(this.skyColor))); }
        if (this.cloudColor != null)    { obj.add("CloudColor",         new JsonPrimitive(JEDStringUtils.colorToHexString(this.cloudColor))); }
        if (this.fogColor != null)      { obj.add("FogColor",           new JsonPrimitive(JEDStringUtils.colorToHexString(this.fogColor))); }

        if (this.colorData != null)
        {
            obj.add("Colors", JEDJsonUtils.deepCopy(this.colorData));
        }

        if (this.customLightBrightnessTable != null)
        {
            JsonArray arr = new JsonArray();

            for (int i = 0; i < this.customLightBrightnessTable.length; i++)
            {
                arr.add(this.customLightBrightnessTable[i]);
            }

            obj.add("LightBrightness", arr);
        }

        return obj;
    }

    public JsonObject getFullJEDPropertiesObject()
    {
        return JEDJsonUtils.deepCopy(this.fullJEDTag);
    }

    @Nonnull
    private static NBTTagList writeFloats(float... values)
    {
        NBTTagList tagList = new NBTTagList();

        for (float f : values)
        {
            tagList.appendTag(new NBTTagFloat(f));
        }

        return tagList;
    }

    public boolean getForceGameMode()
    {
        return this.forceGameMode;
    }

    public boolean getUseCustomDayCycle()
    {
        return this.useCustomDayCycle;
    }

    public int getDayLength()
    {
        return this.dayLength;
    }

    public int getNightLength()
    {
        return this.nightLength;
    }

    public float[] getCustomLightBrightnessTable()
    {
        return this.customLightBrightnessTable;
    }

    public int getSkyRenderType()
    {
        return this.skyRenderType;
    }

    public int getSkyDisableFlags()
    {
        return this.skyDisableFlags;
    }

    public int getCloudHeight()
    {
        return this.cloudHeight;
    }

    @Nullable
    public Vec3d getSkyColor()
    {
        return this.skyColor;
    }

    @Nullable
    public Vec3d getFogColor()
    {
        return this.fogColor;
    }

    @Nullable
    public Vec3d getCloudColor()
    {
        return this.cloudColor;
    }

    @Nullable
    public Boolean canRespawnHere()
    {
        return this.canRespawnHere;
    }

    @Nullable
    public Integer getRespawnDimension()
    {
        return this.respawnDimension;
    }
}
