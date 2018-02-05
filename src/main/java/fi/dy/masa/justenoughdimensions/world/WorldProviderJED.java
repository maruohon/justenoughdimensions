package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
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
import fi.dy.masa.justenoughdimensions.util.world.WorldInfoUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldUtils;

public class WorldProviderJED extends WorldProvider implements IWorldProviderJED
{
    protected JEDWorldProperties properties;
    private boolean worldInfoSet;

    @Override
    public boolean getWorldInfoHasBeenSet()
    {
        return this.worldInfoSet;
    }

    @Override
    public void setDimension(int dimension)
    {
        super.setDimension(dimension);

        JEDWorldProperties props = JEDWorldProperties.getProperties(dimension);

        if (props == null)
        {
            props = new JEDWorldProperties();
        }

        this.properties = props;

        // This method gets called the first time from DimensionManager.createProviderFor(),
        // at which time the world hasn't been set yet. The second call comes from the WorldServer
        // constructor, and there the world has just been set.
        if (this.world != null && this.getWorldInfoHasBeenSet() == false)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfo(this.world);
            //WorldUtils.overrideWorldProviderSettings(this.world, this);
            this.worldInfoSet = true;
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
    public boolean canRespawnHere()
    {
        return this.properties.canRespawnHere() != null ? this.properties.canRespawnHere() : true;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player)
    {
        if (this.properties.getRespawnDimension() != null)
        {
            return this.properties.getRespawnDimension();
        }
        else
        {
            return this.canRespawnHere() ? this.getDimension() : 0;
        }
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
            this.properties = JEDWorldProperties.createPropertiesFor(this.getDimension(), tag);
        }

        if (tag != null && tag.hasKey("SkyRenderer", Constants.NBT.TAG_STRING))
        {
            WorldUtils.createSkyRendererFromName(this, tag.getString("SkyRenderer"));
        }
        else if (this.properties.getSkyRenderType() != 0)
        {
            this.setSkyRenderer(new SkyRenderer(this.properties.getSkyRenderType(), this.properties.getSkyDisableFlags()));
        }
        else
        {
            this.setSkyRenderer(null);
        }
    }

    @Override
    public float[] getLightBrightnessTable()
    {
        if (this.properties.getCustomLightBrightnessTable() != null)
        {
            return this.properties.getCustomLightBrightnessTable();
        }

        return super.getLightBrightnessTable();
    }

    public int getDayCycleLength()
    {
        return this.properties.getDayLength() + this.properties.getNightLength();
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
        if (this.properties.getUseCustomDayCycle() == false)
        {
            return super.calculateCelestialAngle(worldTime, partialTicks);
        }

        int cycleLength = this.getDayCycleLength();
        int dayTicks = (int) (worldTime % cycleLength);
        int duskOrDawnLength = (int) (0.075f * cycleLength);
        int dayLength = this.properties.getDayLength();
        int nightLength = this.properties.getNightLength();
        float angle;

        // This fixes the sun/moon spazzing out in-place noticeably with short day cycles
        if (this.world.getGameRules().getBoolean("doDaylightCycle") == false)
        {
            partialTicks = 0f;
        }

        // Day, including dusk (The day part starts duskOrDawnLength before 0, so
        // subtract the duskOrDawnLength length from the day length to get the upper limit
        // of the day part of the cycle.)
        if (dayTicks > cycleLength - duskOrDawnLength || dayTicks < dayLength - duskOrDawnLength)
        {
            // Dawn (1.5 / 20)th of the full day cycle just before the day rolls over to 0 ticks
            if (dayTicks > dayLength) // this check could also be the "i > cycleLength - duskOrDawnLength"
            {
                dayTicks -= cycleLength - duskOrDawnLength;
            }
            // Day, starts from 0 ticks, so we need to add the dawn part
            else
            {
                dayTicks += duskOrDawnLength;
            }

            angle = (((float) dayTicks + partialTicks) / (float) dayLength * 0.65f) + 0.675f;
        }
        // Night
        else
        {
            dayTicks -= (dayLength - duskOrDawnLength);
            angle = (((float) dayTicks + partialTicks) / (float) nightLength * 0.35f) + 0.325f;
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
        Vec3d skyColor = this.properties.getSkyColor();

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
        float r = (float)((blendColour >> 16 & 255) / 255.0F * skyColor.x);
        float g = (float)((blendColour >>  8 & 255) / 255.0F * skyColor.y);
        float b = (float)((blendColour       & 255) / 255.0F * skyColor.z);
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
        Vec3d cloudColor = this.properties.getCloudColor();

        if (cloudColor == null)
        {
            return super.getCloudColor(partialTicks);
        }

        float f1 = MathHelper.cos(this.world.getCelestialAngle(partialTicks) * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        float r = (float) cloudColor.x;
        float g = (float) cloudColor.y;
        float b = (float) cloudColor.z;

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
        return (float) this.properties.getCloudHeight();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getFogColor(float celestialAngle, float partialTicks)
    {
        Vec3d fogColor = this.properties.getFogColor();

        if (fogColor == null)
        {
            return super.getFogColor(celestialAngle, partialTicks);
        }

        float f = MathHelper.cos(celestialAngle * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        float r = (float) fogColor.x;
        float g = (float) fogColor.y;
        float b = (float) fogColor.z;
        r = r * (f * 0.94F + 0.06F);
        g = g * (f * 0.94F + 0.06F);
        b = b * (f * 0.91F + 0.09F);
        return new Vec3d(r, g, b);
    }
}
