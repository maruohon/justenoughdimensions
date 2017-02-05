package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants;

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

    public WorldInfoJED(NBTTagCompound nbt)
    {
        super(nbt);

        if (nbt.hasKey("JED", Constants.NBT.TAG_COMPOUND))
        {
            NBTTagCompound tag = nbt.getCompoundTag("JED");
            if (tag.hasKey("ForceGameMode", Constants.NBT.TAG_BYTE))   { this.forceGameMode = tag.getBoolean("ForceGameMode"); }
            if (tag.hasKey("CustomDayCycle", Constants.NBT.TAG_BYTE))   { this.useCustomDayCycle = tag.getBoolean("CustomDayCycle"); }
            if (tag.hasKey("DayLength",     Constants.NBT.TAG_INT))    { this.dayLength   = tag.getInteger("DayLength"); }
            if (tag.hasKey("NightLength",   Constants.NBT.TAG_INT))    { this.nightLength = tag.getInteger("NightLength"); }
            if (tag.hasKey("CloudHeight",   Constants.NBT.TAG_INT))    { this.cloudHeight = tag.getInteger("CloudHeight"); }
            if (tag.hasKey("SkyRenderType", Constants.NBT.TAG_BYTE))   { this.skyRenderType = tag.getByte("SkyRenderType"); }
            if (tag.hasKey("SkyDisableFlags", Constants.NBT.TAG_BYTE)) { this.skyDisableFlags = tag.getByte("SkyDisableFlags"); }

            if (tag.hasKey("SkyColor",      Constants.NBT.TAG_STRING)) { this.skyColor   = hexStringToColor(tag.getString("SkyColor")); }
            if (tag.hasKey("CloudColor",    Constants.NBT.TAG_STRING)) { this.cloudColor = hexStringToColor(tag.getString("CloudColor")); }
            if (tag.hasKey("FogColor",      Constants.NBT.TAG_STRING)) { this.fogColor   = hexStringToColor(tag.getString("FogColor")); }
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
        if (this.skyColor != null)   { tag.setString("SkyColor",   colorToHexString(this.skyColor)); }
        if (this.cloudColor != null) { tag.setString("CloudColor", colorToHexString(this.cloudColor)); }
        if (this.fogColor != null)   { tag.setString("FogColor",   colorToHexString(this.fogColor)); }

        return tag;
    }

    public static String colorToHexString(Vec3d color)
    {
        int c = 0;

        c |= (int) ((MathHelper.clamp(color.zCoord, 0, 1) * 0xFF));
        c |= (int) ((MathHelper.clamp(color.yCoord, 0, 1) * 0xFF)) << 8;
        c |= (int) ((MathHelper.clamp(color.xCoord, 0, 1) * 0xFF)) << 16;

        return String.format("%06X", c);
    }

    public static Vec3d intToColor(int i)
    {
        return new Vec3d((double) ((i >> 16) & 0xFF) / (double) 0xFF,
                         (double) ((i >>  8) & 0xFF) / (double) 0xFF,
                         (double) (        i & 0xFF) / (double) 0xFF);
    }

    public static Vec3d hexStringToColor(String colorStr)
    {
        int i = 0;

        try
        {
            // Long so that the MSB being one (ie. would-be-negative-number) doesn't mess up things
            i = (int) Long.parseLong(colorStr, 16);
        }
        catch (NumberFormatException e) {}

        return intToColor(i);
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
