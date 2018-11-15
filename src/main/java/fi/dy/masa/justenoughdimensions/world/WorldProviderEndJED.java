package fi.dy.masa.justenoughdimensions.world;

import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.client.audio.MusicTicker.MusicType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.util.ClientUtils;
import fi.dy.masa.justenoughdimensions.util.world.DragonFightManagerDummy;
import fi.dy.masa.justenoughdimensions.util.world.VoidTeleport;
import fi.dy.masa.justenoughdimensions.util.world.VoidTeleport.VoidTeleportData;
import fi.dy.masa.justenoughdimensions.util.world.WorldInfoUtils;
import fi.dy.masa.justenoughdimensions.util.world.WorldUtils;

public class WorldProviderEndJED extends WorldProviderEnd implements IWorldProviderJED
{
    private static final Field field_dragonFightManager = ReflectionHelper.findField(WorldProviderEnd.class, "field_186064_g", "dragonFightManager");

    protected JEDWorldProperties properties;
    private boolean worldInfoSet;
    protected VoidTeleportData voidTeleport = null;
    protected VoidTeleportData skyTeleport = null;
    protected int teleportCounter;

    @Override
    public boolean getWorldInfoHasBeenSet()
    {
        return this.worldInfoSet;
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
            this.worldInfoSet = true;

            this.skyTeleport =  VoidTeleportData.fromJson(this.properties.getNestedObject("sky_teleport"), this.getDimension());
            this.voidTeleport = VoidTeleportData.fromJson(this.properties.getNestedObject("void_teleport"), this.getDimension());

            if (this.world.isRemote == false && this.properties.getDisableDragon())
            {
                NBTTagCompound tag = this.world.getWorldInfo().getDimensionData(dimension);

                try
                {
                    JustEnoughDimensions.logInfo("Trying to override the DragonFightManager in dimension {}...", dimension);
                    field_dragonFightManager.set(this, new DragonFightManagerDummy((WorldServer) this.world, tag.getCompoundTag("DragonFight")));
                    JustEnoughDimensions.logInfo("Overrode the DragonFightManager with '{}'", DragonFightManagerDummy.class.getName());
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.warn("Failed to override the DragonFightManager in dimension {}", dimension);
                }
            }
        }
    }

    @Override
    public IChunkGenerator createChunkGenerator()
    {
        if (this.properties.getDisableEndSpikes())
        {
            JustEnoughDimensions.logInfo("Using '{}' in dimension {}", ChunkGeneratorEndJED.class.getName(), this.getDimension());
            return new ChunkGeneratorEndJED(this.world, this.world.getWorldInfo().isMapFeaturesEnabled(), this.world.getSeed(), this.getSpawnCoordinate());
        }
        else
        {
            return super.createChunkGenerator();
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

        return type != null ? type : super.getDimensionType();
    }

    @Override
    public void setJEDProperties(JEDWorldProperties properties)
    {
        this.properties = properties;
        ClientUtils.setRenderersFrom(this, this.properties.getFullJEDProperties());
    }

    @Override
    public WorldSleepResult canSleepAt(EntityPlayer player, BlockPos pos)
    {
        return this.properties.canSleepHere() != null ? this.properties.canSleepHere() : super.canSleepAt(player, pos);
    }

    @Override
    public boolean canRespawnHere()
    {
        return this.properties.canRespawnHere() != null ? this.properties.canRespawnHere() : false;
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
    public int getMoonPhase(long worldTime)
    {
        long cycleLength = this.getDayCycleLength();
        return (int)(worldTime / cycleLength % 8L + 8L) % 8;
    }

    @Override
    public void setWorldTime(long time)
    {
        time = WorldProviderJED.getNewWorldTime(time, this.getWorldTime(), this.properties);
        super.setWorldTime(time);
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks)
    {
        if (this.properties.getUseCustomDayCycle())
        {
            return WorldProviderJED.calculateCelestialAngle(this.world, this.properties, this.getDayCycleLength(), worldTime, partialTicks);
        }
        else if (this.properties.getUseCustomCelestialAngleRange())
        {
            return WorldProviderJED.getCustomCelestialAngleValue(this.world, this.properties, this.getDayCycleLength(), worldTime, partialTicks);
        }

        return super.calculateCelestialAngle(worldTime, partialTicks);
    }

    @Override
    public boolean canDropChunk(int x, int z)
    {
        return this.world.isSpawnChunk(x, z) == false || this.getDimensionType().shouldLoadSpawn() == false;
    }

    @Override
    public void setAllowedSpawnTypes(boolean allowHostileIn, boolean allowPeacefulIn)
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
        return this.properties.canDoLightning() != null ? this.properties.canDoLightning().booleanValue() : false;
    }

    @Override
    public boolean canDoRainSnowIce(net.minecraft.world.chunk.Chunk chunk)
    {
        return this.properties.canDoRainSnowIce() != null ? this.properties.canDoRainSnowIce().booleanValue() : false;
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
        return this.properties.getHasXZFog() != null ? this.properties.getHasXZFog().booleanValue() : super.doesXZShowFog(x, z);
    }

    @Override
    public boolean isSurfaceWorld()
    {
        return this.properties.isSurfaceWorld() != null ? this.properties.isSurfaceWorld().booleanValue() : false;
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
        return this.properties.getMovementFactor() != null ? this.properties.getMovementFactor().doubleValue() : 1.0D;
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
    public boolean shouldClientCheckLighting()
    {
        if (this.properties.shouldClientCheckLight() != null)
        {
            return this.properties.shouldClientCheckLight().booleanValue();
        }

        return true;
    }

    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z)
    {
        return this.isSurfaceWorld() == false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @Nullable
    public MusicType getMusicType()
    {
        MusicType music = ClientUtils.getMusicTypeFromProperties(this.properties);
        return music != null ? music : null;
    }

    @Override
    public boolean isSkyColored()
    {
        return this.properties.getSkyColor() != null;
    }

    @Override
    public float getCloudHeight()
    {
        return (float) this.properties.getCloudHeight();
    }

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

        return new Vec3d(r, g, b);
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

    @Override
    public Vec3d getCloudColor(float partialTicks)
    {
        Vec3d cloudColor = this.properties.getCloudColor();
        return cloudColor != null ? cloudColor : super.getCloudColor(partialTicks);
    }
}
