package fi.dy.masa.justenoughdimensions.world;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;

public class JEDWorldProperties
{
    private static final Map<Integer, JEDWorldProperties> PROPERTIES = new HashMap<>();

    private NBTTagCompound fullJEDTag;
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
    public static JEDWorldProperties getProperties(World world)
    {
        return getProperties(world.provider.getDimension());
    }

    @Nullable
    public static JEDWorldProperties getProperties(int dimension)
    {
        return PROPERTIES.get(dimension);
    }

    public static JEDWorldProperties createPropertiesFor(int dimension, NBTTagCompound nbt)
    {
        JEDWorldProperties props = new JEDWorldProperties(nbt);
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

    public JEDWorldProperties()
    {
    }

    private JEDWorldProperties(NBTTagCompound tag)
    {
        this.fullJEDTag = tag;

        if (tag.hasKey("ForceGameMode",     Constants.NBT.TAG_BYTE))   { this.forceGameMode     = tag.getBoolean("ForceGameMode"); }
        if (tag.hasKey("CustomDayCycle",    Constants.NBT.TAG_BYTE))   { this.useCustomDayCycle = tag.getBoolean("CustomDayCycle"); }
        if (tag.hasKey("DayLength",         Constants.NBT.TAG_INT))    { this.dayLength         = tag.getInteger("DayLength"); }
        if (tag.hasKey("NightLength",       Constants.NBT.TAG_INT))    { this.nightLength       = tag.getInteger("NightLength"); }
        if (tag.hasKey("CloudHeight",       Constants.NBT.TAG_INT))    { this.cloudHeight       = tag.getInteger("CloudHeight"); }
        if (tag.hasKey("SkyRenderer",       Constants.NBT.TAG_STRING)) { this.skyRenderer       = tag.getString("SkyRenderer"); }
        if (tag.hasKey("SkyRenderType",     Constants.NBT.TAG_BYTE))   { this.skyRenderType     = tag.getByte("SkyRenderType"); }
        if (tag.hasKey("SkyDisableFlags",   Constants.NBT.TAG_BYTE))   { this.skyDisableFlags   = tag.getByte("SkyDisableFlags"); }

        if (tag.hasKey("SkyColor",      Constants.NBT.TAG_STRING)) { this.skyColor   = JEDStringUtils.hexStringToColor(tag.getString("SkyColor")); }
        if (tag.hasKey("CloudColor",    Constants.NBT.TAG_STRING)) { this.cloudColor = JEDStringUtils.hexStringToColor(tag.getString("CloudColor")); }
        if (tag.hasKey("FogColor",      Constants.NBT.TAG_STRING)) { this.fogColor   = JEDStringUtils.hexStringToColor(tag.getString("FogColor")); }

        if (tag.hasKey("LightBrightness", Constants.NBT.TAG_LIST))
        {
            NBTTagList list = tag.getTagList("LightBrightness", Constants.NBT.TAG_FLOAT);

            if (list.tagCount() == 16)
            {
                this.customLightBrightnessTable = new float[16];

                for (int i = 0; i < 16; i++)
                {
                    this.customLightBrightnessTable[i] = list.getFloatAt(i);
                }
            }
        }

        if (tag.hasKey("CanRespawnHere", Constants.NBT.TAG_BYTE))   { this.canRespawnHere = tag.getBoolean("CanRespawnHere"); }
        if (tag.hasKey("RespawnDimension", Constants.NBT.TAG_INT))  { this.respawnDimension = tag.getInteger("RespawnDimension"); }

        if (this.dayLength   <= 0) { this.dayLength = 1; }
        if (this.nightLength <= 0) { this.nightLength = 1; }
    }

    public NBTTagCompound getJEDTagForClientSync()
    {
        NBTTagCompound tag = new NBTTagCompound();

        if (this.dayLength != 12000)    { tag.setInteger("DayLength", this.dayLength); }
        if (this.nightLength != 12000)  { tag.setInteger("NightLength", this.nightLength); }
        if (this.cloudHeight != 128)    { tag.setInteger("CloudHeight", this.cloudHeight); }
        if (this.skyRenderer != null)   { tag.setString("SkyRenderer", this.skyRenderer); }
        if (this.skyRenderType != 0)    { tag.setByte("SkyRenderType", (byte) this.skyRenderType); }
        if (this.skyDisableFlags != 0)  { tag.setByte("SkyDisableFlags", (byte) this.skyDisableFlags); }
        if (this.useCustomDayCycle)  { tag.setBoolean("CustomDayCycle", this.useCustomDayCycle); }
        if (this.skyColor != null)   { tag.setString("SkyColor",   JEDStringUtils.colorToHexString(this.skyColor)); }
        if (this.cloudColor != null) { tag.setString("CloudColor", JEDStringUtils.colorToHexString(this.cloudColor)); }
        if (this.fogColor != null)   { tag.setString("FogColor",   JEDStringUtils.colorToHexString(this.fogColor)); }
        if (this.customLightBrightnessTable != null) { tag.setTag("LightBrightness", writeFloats(this.customLightBrightnessTable)); }

        return tag;
    }

    public NBTTagCompound getFullJEDTag()
    {
        return this.fullJEDTag;
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

    public boolean getForceGamemode()
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
