package fi.dy.masa.justenoughdimensions.world;

import javax.annotation.Nonnull;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;

public class WorldInfoJED extends WorldInfo
{
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
    private float[] customLightBrightnessTable = null;

    public WorldInfoJED(NBTTagCompound nbt)
    {
        super(nbt);

        if (nbt.hasKey("JED", Constants.NBT.TAG_COMPOUND))
        {
            NBTTagCompound tag = nbt.getCompoundTag("JED");
            if (tag.hasKey("ForceGameMode", Constants.NBT.TAG_BYTE))   { this.forceGameMode = tag.getBoolean("ForceGameMode"); }
            if (tag.hasKey("CustomDayCycle", Constants.NBT.TAG_BYTE))  { this.useCustomDayCycle = tag.getBoolean("CustomDayCycle"); }
            if (tag.hasKey("DayLength",     Constants.NBT.TAG_INT))    { this.dayLength   = tag.getInteger("DayLength"); }
            if (tag.hasKey("NightLength",   Constants.NBT.TAG_INT))    { this.nightLength = tag.getInteger("NightLength"); }
            if (tag.hasKey("CloudHeight",   Constants.NBT.TAG_INT))    { this.cloudHeight = tag.getInteger("CloudHeight"); }
            if (tag.hasKey("SkyRenderType", Constants.NBT.TAG_BYTE))   { this.skyRenderType = tag.getByte("SkyRenderType"); }
            if (tag.hasKey("SkyDisableFlags", Constants.NBT.TAG_BYTE)) { this.skyDisableFlags = tag.getByte("SkyDisableFlags"); }

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
        }

        if (this.dayLength   <= 0) { this.dayLength = 1; }
        if (this.nightLength <= 0) { this.nightLength = 1; }
    }

    // Commented out the JED property saving to NBT, because doing that would mean
    // that the properties can't be removed via the dimbuilder command or by
    // removing them from the dimensions.json config file.

    /*@Override
    public NBTTagCompound cloneNBTCompound(NBTTagCompound nbt)
    {
        nbt = super.cloneNBTCompound(nbt);
        nbt.setTag("JED", this.getJEDTag());

        return nbt;
    }*/

    public NBTTagCompound getJEDTag()
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("DayLength", this.dayLength);
        tag.setInteger("NightLength", this.nightLength);
        tag.setInteger("CloudHeight", this.cloudHeight);
        tag.setByte("SkyRenderType", (byte) this.skyRenderType);
        tag.setByte("SkyDisableFlags", (byte) this.skyDisableFlags);

        if (this.forceGameMode)      { tag.setBoolean("ForceGameMode", this.forceGameMode); }
        if (this.useCustomDayCycle)  { tag.setBoolean("CustomDayCycle", this.useCustomDayCycle); }
        if (this.skyColor != null)   { tag.setString("SkyColor",   JEDStringUtils.colorToHexString(this.skyColor)); }
        if (this.cloudColor != null) { tag.setString("CloudColor", JEDStringUtils.colorToHexString(this.cloudColor)); }
        if (this.fogColor != null)   { tag.setString("FogColor",   JEDStringUtils.colorToHexString(this.fogColor)); }
        if (this.customLightBrightnessTable != null) { tag.setTag("LightBrightness", writeFloats(this.customLightBrightnessTable)); }

        return tag;
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

    @Override
    public void setDifficulty(EnumDifficulty newDifficulty)
    {
        // NO-OP to prevent the MinecraftServer from reseting this
    }

    @Override
    public void setGameType(GameType type)
    {
        // NO-OP to prevent the MinecraftServer from reseting this
    }

    public void setDifficultyJED(EnumDifficulty newDifficulty)
    {
        super.setDifficulty(newDifficulty);
    }

    public void setGameTypeJED(GameType type)
    {
        super.setGameType(type);
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
}
