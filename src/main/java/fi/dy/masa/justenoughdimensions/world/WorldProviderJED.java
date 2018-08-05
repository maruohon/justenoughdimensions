package fi.dy.masa.justenoughdimensions.world;

import javax.annotation.Nullable;
import net.minecraft.client.audio.MusicTicker.MusicType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.util.ClientUtils;
import fi.dy.masa.justenoughdimensions.util.world.VoidTeleport;
import fi.dy.masa.justenoughdimensions.util.world.VoidTeleport.VoidTeleportData;
import fi.dy.masa.justenoughdimensions.util.world.WorldInfoUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldUtils;

public class WorldProviderJED extends WorldProviderSurface implements IWorldProviderJED
{
    protected JEDWorldProperties properties;
    private boolean worldInfoSet;
    protected boolean canRespawnHere;
    protected boolean isSurfaceWorld;
    protected boolean hasXZFog;
    protected double movementFactor;
    protected VoidTeleportData voidTeleport = null;
    protected VoidTeleportData skyTeleport = null;
    protected int teleportCounter;

    @Override
    public boolean getWorldInfoHasBeenSet()
    {
        return this.worldInfoSet;
    }

    @Override
    protected void init()
    {
        super.init();

        // Initialize the default values (used if the properties haven't been set via the config)
        this.canRespawnHere = true;
        this.isSurfaceWorld = true;
        this.hasXZFog = false;
        this.movementFactor = 1.0D;

        this.setBiomeProviderIfConfigured();
    }

    protected void setBiomeProviderIfConfigured()
    {
        // If this dimension has been configured for a single biome,
        // then set it here so that it's early enough for Sponge to use it.
        String biomeName = DimensionConfig.instance().getBiomeFor(this.getDimension());
        Biome biome = biomeName != null ? Biome.REGISTRY.getObject(new ResourceLocation(biomeName)) : null;

        if (biome != null)
        {
            JustEnoughDimensions.logInfo("WorldProviderJED.setBiomeProviderIfConfigured(): Using BiomeProviderSingle with biome '{}' for dimension {}",
                    biomeName, this.getDimension());
            this.biomeProvider = new BiomeProviderSingle(biome);
        }
    }

    @Override
    public void setDimension(int dimension)
    {
        super.setDimension(dimension);

        this.properties = JEDWorldProperties.getOrCreateProperties(dimension);

        // This method gets called the first time from DimensionManager.createProviderFor(),
        // at which time the world hasn't been set yet. The second call comes from the WorldServer
        // constructor, and there the world has just been set.
        if (this.world != null && this.getWorldInfoHasBeenSet() == false)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfo(this.world);
            this.hasSkyLight = this.properties.getHasSkyLight() != null ? this.properties.getHasSkyLight().booleanValue() : this.hasSkyLight;
            //WorldUtils.overrideWorldProviderSettings(this.world, this);
            this.worldInfoSet = true;

            this.skyTeleport =  VoidTeleportData.fromJson(this.properties.getNestedObject("sky_teleport"), this.getDimension());
            this.voidTeleport = VoidTeleportData.fromJson(this.properties.getNestedObject("void_teleport"), this.getDimension());

            // This is to fix the allowHostiles and allowPeacefulMobs options not
            // taking effect in single player after first loading a world,
            // before some other dimension gets loaded and the setDifficultyForAllWorlds() getting called... >_>
            // See at the end of IntegratedServer#loadAllWorlds(), the null check prevents setDifficultyForAllWorlds()
            // from getting called >_>
            if (this.world.isRemote == false)
            {
                boolean allowHostiles = true;
                boolean allowPeaceful = true;

                if (this.world.getWorldInfo().isHardcoreModeEnabled() == false)
                {
                    if (this.world.getMinecraftServer().isSinglePlayer())
                    {
                        allowHostiles = this.world.getDifficulty() != EnumDifficulty.PEACEFUL;
                    }
                    else
                    {
                        allowHostiles = this.world.getMinecraftServer().allowSpawnMonsters();
                        allowPeaceful = this.world.getMinecraftServer().getCanSpawnAnimals();
                    }
                }

                this.world.setAllowedSpawnTypes(allowHostiles, allowPeaceful);
            }
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
        return this.properties.canRespawnHere() != null ? this.properties.canRespawnHere() : this.canRespawnHere;
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
    public void setAllowedSpawnTypes(final boolean allowHostileIn, final boolean allowPeacefulIn)
    {
        // This fixes the custom dimensions being unable to spawn hostile mobs if the overworld is set to Peaceful
        // See Minecraft#runTick(), the call to this.world.setAllowedSpawnTypes(),
        // and also MinecraftServer#setDifficultyForAllWorlds()
        boolean allowHostile = this.world.getWorldInfo().getDifficulty() != EnumDifficulty.PEACEFUL;
        boolean allowPeaceful = allowPeacefulIn;

        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(this.getDimension());

        if (props != null)
        {
            Boolean hostiles = props.canSpawnHostiles();
            Boolean peaceful = props.canSpawnPeacefulMobs();

            if (hostiles != null)
            {
                allowHostile = hostiles.booleanValue();
            }

            if (peaceful != null)
            {
                allowPeaceful = peaceful.booleanValue();
            }
        }

        super.setAllowedSpawnTypes(allowHostile, allowPeaceful);
    }

    @Override
    public void setJEDProperties(JEDWorldProperties properties)
    {
        this.properties = properties;
        ClientUtils.setRenderersFrom(this, this.properties.getFullJEDProperties());
    }

    @Override
    public void onWorldUpdateEntities()
    {
        super.onWorldUpdateEntities();

        if (++this.teleportCounter >= this.properties.getVoidTeleportInterval())
        {
            VoidTeleport.tryVoidTeleportEntities(this.world, this.voidTeleport, this.skyTeleport);
            this.teleportCounter = 0;
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
    public void setWorldTime(long time)
    {
        time = getNewWorldTime(time, this.getWorldTime(), this.properties);
        super.setWorldTime(time);
    }

    public static long getNewWorldTime(long nextTime, long currentTime, JEDWorldProperties properties)
    {
        // The time is incremented normally (ie. not using '/time set' etc.)
        if (properties.getUseCustomDayTimeRange() && (currentTime + 1) == nextTime)
        {
            int min = properties.getDayTimeMin();
            int max = properties.getDayTimeMax();
            int dayCycleLength = max - min + 1;

            if ((nextTime % dayCycleLength) == 0)
            {
                nextTime += properties.getDayCycleIncrement() - dayCycleLength;
            }
        }

        return nextTime;
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
        if (this.properties.getUseCustomDayCycle())
        {
            return calculateCelestialAngle(this.world, this.properties, this.getDayCycleLength(), worldTime, partialTicks);
        }
        else if (this.properties.getUseCustomCelestialAngleRange())
        {
            return getCustomCelestialAngleValue(this.world, this.properties, this.getDayCycleLength(), worldTime, partialTicks);
        }

        return super.calculateCelestialAngle(worldTime, partialTicks);
    }

    public static float calculateCelestialAngle(World world, JEDWorldProperties properties, int dayCycleLength, long worldTime, float partialTicks)
    {
        long dayTicks = worldTime % dayCycleLength;
        int duskOrDawnLength = (int) (0.075f * dayCycleLength);
        int dayLength = properties.getDayLength();
        int nightLength = properties.getNightLength();
        float angle;

        // This check fixes the sun/moon spazzing out in-place noticeably
        // with short day cycles if the daylight cycle has been disabled.
        if (world.getGameRules().getBoolean("doDaylightCycle") == false)
        {
            partialTicks = 0f;
        }

        // Day, including dusk (The day part starts duskOrDawnLength before 0, so
        // subtract the duskOrDawnLength length from the day length to get the upper limit
        // of the day part of the cycle.)
        if (dayTicks > dayCycleLength - duskOrDawnLength || dayTicks < dayLength - duskOrDawnLength)
        {
            // Dawn (1.5 / 20)th of the full day cycle just before the day rolls over to 0 ticks
            if (dayTicks > dayLength) // this check could also be the "i > cycleLength - duskOrDawnLength"
            {
                dayTicks -= dayCycleLength - duskOrDawnLength;
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

    public static float getCustomCelestialAngleValue(World world, JEDWorldProperties properties, int dayCycleLength, long worldTime, float partialTicks)
    {
        long dayTicks = worldTime % dayCycleLength;

        // This check fixes the sun/moon spazzing out in-place noticeably
        // with short day cycles if the daylight cycle has been disabled.
        if (world.getGameRules().getBoolean("doDaylightCycle") == false)
        {
            partialTicks = 0f;
        }

        float min = properties.getCelestialAngleMin();
        float max = properties.getCelestialAngleMax();

        return min + ((max - min) * (((float) dayTicks + partialTicks) / (float) dayCycleLength));
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z)
    {
        Boolean ignore = this.properties.ignoreSpawnSuitability();

        if (ignore != null && ignore.booleanValue())
        {
            return true;
        }

        return super.canCoordinateBeSpawn(x, z);
    }

    @Override
    public boolean canDoLightning(net.minecraft.world.chunk.Chunk chunk)
    {
        return this.properties.canDoLightning() != null ? this.properties.canDoLightning().booleanValue() : true;
    }

    @Override
    public boolean canDoRainSnowIce(net.minecraft.world.chunk.Chunk chunk)
    {
        return this.properties.canDoRainSnowIce() != null ? this.properties.canDoRainSnowIce().booleanValue() : true;
    }

    @Override
    public boolean canBlockFreeze(BlockPos pos, boolean noWaterAdj)
    {
        if (this.properties.canDoRainSnowIce() != null)
        {
            return this.properties.canDoRainSnowIce().booleanValue() && WorldUtils.canBlockFreeze(this.world, pos, noWaterAdj);
        }

        return super.canBlockFreeze(pos, noWaterAdj);
    }

    @Override
    public boolean canSnowAt(BlockPos pos, boolean checkLight)
    {
        if (this.properties.canDoRainSnowIce() != null)
        {
            return this.properties.canDoRainSnowIce().booleanValue() && WorldUtils.canSnowAt(this.world, pos);
        }

        return super.canSnowAt(pos, checkLight);
    }

    @Override
    public boolean doesXZShowFog(int x, int z)
    {
        return this.properties.getHasXZFog() != null ? this.properties.getHasXZFog().booleanValue() : this.hasXZFog;
    }

    @Override
    public boolean isSurfaceWorld()
    {
        return this.properties.isSurfaceWorld() != null ? this.properties.isSurfaceWorld().booleanValue() : this.isSurfaceWorld;
    }

    @Override
    public int getAverageGroundLevel()
    {
        return this.properties.getAverageGroundLevel() != null ? this.properties.getAverageGroundLevel().intValue() : super.getAverageGroundLevel();
    }

    @Override
    public double getHorizon()
    {
        return this.properties.getHorizon() != null ? this.properties.getHorizon().doubleValue() : super.getHorizon();
    }

    @Override
    public double getMovementFactor()
    {
        return this.properties.getMovementFactor() != null ? this.properties.getMovementFactor().doubleValue() : this.movementFactor;
    }

    @Override
    public float getSunBrightness(float partialTicks)
    {
        return this.properties.getSunBrightness() != null ? this.properties.getSunBrightness().floatValue() : super.getSunBrightness(partialTicks);
    }

    @Override
    public float getSunBrightnessFactor(float partialTicks)
    {
        return this.properties.getSunBrightnessFactor() != null ? this.properties.getSunBrightnessFactor().floatValue() : super.getSunBrightnessFactor(partialTicks);
    }

    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z)
    {
        return this.isSurfaceWorld() == false;
    }

    @Override
    public boolean shouldClientCheckLighting()
    {
        if (this.properties.shouldClientCheckLight() != null)
        {
            return this.properties.shouldClientCheckLight().booleanValue();
        }

        return (this instanceof WorldProviderSurfaceJED) == false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @Nullable
    public MusicType getMusicType()
    {
        return ClientUtils.getMusicTypeFromProperties(this.properties);
    }

    @Override
    public Vec3d getSkyColor(Entity entity, float partialTicks)
    {
        Vec3d skyColor = this.properties.getSkyColor();

        if (skyColor == null)
        {
            return super.getSkyColor(entity, partialTicks);
        }

        int x = MathHelper.floor(entity.posX);
        int y = MathHelper.floor(entity.posY);
        int z = MathHelper.floor(entity.posZ);
        BlockPos blockpos = new BlockPos(x, y, z);

        int blendColour = net.minecraftforge.client.ForgeHooksClient.getSkyBlendColour(this.world, blockpos);
        float r = (float)((blendColour >> 16 & 255) / 255.0F * skyColor.x);
        float g = (float)((blendColour >>  8 & 255) / 255.0F * skyColor.y);
        float b = (float)((blendColour       & 255) / 255.0F * skyColor.z);
        float f1 = MathHelper.cos(this.world.getCelestialAngle(partialTicks) * ((float) Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);

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

    @Override
    public float getCloudHeight()
    {
        return (float) this.properties.getCloudHeight();
    }

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
