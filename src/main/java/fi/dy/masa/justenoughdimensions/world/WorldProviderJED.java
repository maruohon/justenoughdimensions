package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.justenoughdimensions.client.render.SkyRenderer;
import fi.dy.masa.justenoughdimensions.world.util.WorldInfoUtils;

public class WorldProviderJED extends WorldProvider implements IWorldProviderJED
{
    protected int dayLength = 12000;
    protected int nightLength = 12000;
    protected int cloudHeight = 128;
    private int skyRenderType = 0;
    private int skyDisableFlags = 0;
    private boolean useCustomDayCycle;
    protected Vec3d skyColor = null;
    protected Vec3d cloudColor = null;
    protected Vec3d fogColor = null;

    @Override
    public void setDimension(int dim)
    {
        super.setDimension(dim);

        // This method gets called the first time from DimensionManager.createProviderFor(),
        // at which time the world hasn't been set yet. The second call comes from the WorldServer
        // constructor, and there the world has just been set.
        if (this.world != null)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfoOnly(this.world);
            //WorldUtils.overrideWorldProviderSettings(this.world, this);
        }
    }

    @Override
    public DimensionType getDimensionType()
    {
        DimensionType type = null;

        try
        {
            type = DimensionManager.getProviderType(this.getDimension());
        }
        catch (IllegalArgumentException e)
        {
        }

        return type != null ? type : DimensionType.OVERWORLD;
    }

    @Override
    public BlockPos getSpawnCoordinate()
    {
        // Override this method because by default it returns null, so if overriding the End
        // with this class, this prevents a crash in the vanilla TP code.
        return this.world.getSpawnPoint();
    }

    @Override
    public boolean canDropChunk(int x, int z)
    {
        return this.world.isSpawnChunk(x, z) == false || this.getDimensionType().shouldLoadSpawn() == false;
    }

    @Override
    public void setAllowedSpawnTypes(boolean allowHostile, boolean allowPeaceful)
    {
        // This fixes the custom dimensions being unable to spawn hostile mobs if the overworld is set to Peaceful
        // See Minecraft#runTick(), the call to this.world.setAllowedSpawnTypes(),
        // and also MinecraftServer#setDifficultyForAllWorlds()
        super.setAllowedSpawnTypes(this.world.getWorldInfo().getDifficulty() != EnumDifficulty.PEACEFUL, allowPeaceful);
    }

    @Override
    public void setJEDPropertiesFromNBT(NBTTagCompound tag)
    {
        if (tag != null)
        {
            this.useCustomDayCycle = tag.getBoolean("CustomDayCycle");
            if (tag.hasKey("DayLength",     Constants.NBT.TAG_INT))    { this.dayLength   = tag.getInteger("DayLength"); }
            if (tag.hasKey("NightLength",   Constants.NBT.TAG_INT))    { this.nightLength = tag.getInteger("NightLength"); }
            if (tag.hasKey("CloudHeight",   Constants.NBT.TAG_INT))    { this.cloudHeight = tag.getInteger("CloudHeight"); }
            if (tag.hasKey("SkyRenderType", Constants.NBT.TAG_BYTE))   { this.skyRenderType = tag.getByte("SkyRenderType"); }
            if (tag.hasKey("SkyDisableFlags", Constants.NBT.TAG_BYTE)) { this.skyDisableFlags = tag.getByte("SkyDisableFlags"); }

            if (tag.hasKey("SkyColor",      Constants.NBT.TAG_STRING)) { this.skyColor   = WorldInfoJED.hexStringToColor(tag.getString("SkyColor")); }
            if (tag.hasKey("CloudColor",    Constants.NBT.TAG_STRING)) { this.cloudColor = WorldInfoJED.hexStringToColor(tag.getString("CloudColor")); }
            if (tag.hasKey("FogColor",      Constants.NBT.TAG_STRING)) { this.fogColor   = WorldInfoJED.hexStringToColor(tag.getString("FogColor")); }
        }

        if (this.dayLength   <= 0) { this.dayLength = 1; }
        if (this.nightLength <= 0) { this.nightLength = 1; }

        if (this.skyRenderType != 0)
        {
            this.setSkyRenderer(new SkyRenderer(this.skyRenderType, this.skyDisableFlags));
        }
        else
        {
            this.setSkyRenderer(null);
        }
    }

    @Override
    public void setJEDPropertiesFromWorldInfo(WorldInfoJED worldInfo)
    {
        this.useCustomDayCycle = worldInfo.getUseCustomDayCycle();
        this.dayLength = worldInfo.getDayLength();
        this.nightLength = worldInfo.getNightLength();

        if (this.dayLength   <= 0) { this.dayLength = 1; }
        if (this.nightLength <= 0) { this.nightLength = 1; }
    }

    public int getDayCycleLength()
    {
        return this.dayLength + this.nightLength;
    }

    @Override
    public int getMoonPhase(long worldTime)
    {
        long cycleLength = this.getDayCycleLength();
        return (int)(worldTime / cycleLength % 8L + 8L) % 8;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks)
    {
        if (this.useCustomDayCycle == false)
        {
            return super.calculateCelestialAngle(worldTime, partialTicks);
        }

        int cycleLength = this.getDayCycleLength();
        int dayTicks = (int) (worldTime % cycleLength);
        int duskOrDawnLength = (int) (0.075f * cycleLength);
        float angle;

        // This fixes the sun/moon spazzing out in-place noticeably with short day cycles
        if (this.world.getGameRules().getBoolean("doDaylightCycle") == false)
        {
            partialTicks = 0f;
        }

        // Day, including dusk (The day part starts duskOrDawnLength before 0, so
        // subtract the duskOrDawnLength length from the day length to get the upper limit
        // of the day part of the cycle.)
        if (dayTicks > cycleLength - duskOrDawnLength || dayTicks < this.dayLength - duskOrDawnLength)
        {
            // Dawn (1.5 / 20)th of the full day cycle just before the day rolls over to 0 ticks
            if (dayTicks > this.dayLength) // this check could also be the "i > cycleLength - duskOrDawnLength"
            {
                dayTicks -= cycleLength - duskOrDawnLength;
            }
            // Day, starts from 0 ticks, so we need to add the dawn part
            else
            {
                dayTicks += duskOrDawnLength;
            }

            angle = (((float) dayTicks + partialTicks) / (float) this.dayLength * 0.65f) + 0.675f;
        }
        // Night
        else
        {
            dayTicks -= (this.dayLength - duskOrDawnLength);
            angle = (((float) dayTicks + partialTicks) / (float) this.nightLength * 0.35f) + 0.325f;
        }

        if (angle > 1.0F)
        {
            --angle;
        }

        float f1 = 1.0F - (float) ((Math.cos(angle * Math.PI) + 1.0D) / 2.0D);
        angle = angle + (f1 - angle) / 3.0F;

        return angle;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getSkyColor(Entity entity, float partialTicks)
    {
        Vec3d skyColor = this.skyColor;
        if (skyColor == null)
        {
            return super.getSkyColor(entity, partialTicks);
        }

        float f1 = MathHelper.cos(this.world.getCelestialAngle(partialTicks) * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        int x = MathHelper.floor(entity.posX);
        int y = MathHelper.floor(entity.posY);
        int z = MathHelper.floor(entity.posZ);
        BlockPos blockpos = new BlockPos(x, y, z);
        int blendColour = net.minecraftforge.client.ForgeHooksClient.getSkyBlendColour(this.world, blockpos);
        float r = (float)((blendColour >> 16 & 255) / 255.0F * skyColor.xCoord);
        float g = (float)((blendColour >>  8 & 255) / 255.0F * skyColor.yCoord);
        float b = (float)((blendColour       & 255) / 255.0F * skyColor.zCoord);
        r = r * f1;
        g = g * f1;
        b = b * f1;

        float rain = this.world.getRainStrength(partialTicks);
        if (rain > 0.0F)
        {
            float f7 = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.6F;
            float f8 = 1.0F - rain * 0.75F;
            r = r * f8 + f7 * (1.0F - f8);
            g = g * f8 + f7 * (1.0F - f8);
            b = b * f8 + f7 * (1.0F - f8);
        }

        float thunder = this.world.getThunderStrength(partialTicks);
        if (thunder > 0.0F)
        {
            float f11 = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.2F;
            float f9 = 1.0F - thunder * 0.75F;
            r = r * f9 + f11 * (1.0F - f9);
            g = g * f9 + f11 * (1.0F - f9);
            b = b * f9 + f11 * (1.0F - f9);
        }

        if (this.world.getLastLightningBolt() > 0)
        {
            float f12 = (float)this.world.getLastLightningBolt() - partialTicks;

            if (f12 > 1.0F)
            {
                f12 = 1.0F;
            }

            f12 = f12 * 0.45F;
            r = r * (1.0F - f12) + 0.8F * f12;
            g = g * (1.0F - f12) + 0.8F * f12;
            b = b * (1.0F - f12) + 1.0F * f12;
        }

        return new Vec3d(r, g, b);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getCloudColor(float partialTicks)
    {
        Vec3d cloudColor = this.cloudColor;
        if (cloudColor == null)
        {
            return super.getCloudColor(partialTicks);
        }

        float f1 = MathHelper.cos(this.world.getCelestialAngle(partialTicks) * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        float r = (float) cloudColor.xCoord;
        float g = (float) cloudColor.yCoord;
        float b = (float) cloudColor.zCoord;

        float rain = this.world.getRainStrength(partialTicks);
        if (rain > 0.0F)
        {
            float f6 = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.6F;
            float f7 = 1.0F - rain * 0.95F;
            r = r * f7 + f6 * (1.0F - f7);
            g = g * f7 + f6 * (1.0F - f7);
            b = b * f7 + f6 * (1.0F - f7);
        }

        r = r * (f1 * 0.9F + 0.1F);
        g = g * (f1 * 0.9F + 0.1F);
        b = b * (f1 * 0.85F + 0.15F);

        float thunder = this.world.getThunderStrength(partialTicks);
        if (thunder > 0.0F)
        {
            float f10 = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.2F;
            float f8 = 1.0F - thunder * 0.95F;
            r = r * f8 + f10 * (1.0F - f8);
            g = g * f8 + f10 * (1.0F - f8);
            b = b * f8 + f10 * (1.0F - f8);
        }

        return new Vec3d(r, g, b);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getCloudHeight()
    {
        return (float) this.cloudHeight;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getFogColor(float celestialAngle, float partialTicks)
    {
        Vec3d fogColor = this.fogColor;
        if (fogColor == null)
        {
            return super.getFogColor(celestialAngle, partialTicks);
        }

        float f = MathHelper.cos(celestialAngle * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        float r = (float) fogColor.xCoord;
        float g = (float) fogColor.yCoord;
        float b = (float) fogColor.zCoord;
        r = r * (f * 0.94F + 0.06F);
        g = g * (f * 0.94F + 0.06F);
        b = b * (f * 0.91F + 0.09F);
        return new Vec3d(r, g, b);
    }
}
