package fi.dy.masa.justenoughdimensions.world;

import javax.annotation.Nullable;
import net.minecraft.client.audio.MusicTicker.MusicType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.ChunkGeneratorHell;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.justenoughdimensions.util.ClientUtils;

public class WorldProviderHellJED extends WorldProviderJED
{
    @Override
    public void init()
    {
        this.doesWaterVaporize = true;
        this.nether = true;

        // Initialize the default values (used if the properties haven't been set via the config)
        this.canRespawnHere = false;
        this.isSurfaceWorld = false;
        this.hasXZFog = true;
        this.movementFactor = 8.0D;

        this.setBiomeProviderIfConfigured();

        if (this.biomeProvider == null)
        {
            this.biomeProvider = new BiomeProviderSingle(Biomes.HELL);
        }
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks)
    {
        if (this.properties.getUseCustomDayCycle())
        {
            return super.calculateCelestialAngle(worldTime, partialTicks);
        }
        else if (this.properties.getUseCustomCelestialAngleRange())
        {
            return getCustomCelestialAngleValue(this.world, this.properties, this.getDayCycleLength(), worldTime, partialTicks);
        }

        return 0.5F;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @Nullable
    public MusicType getMusicType()
    {
        MusicType music = ClientUtils.getMusicTypeFromProperties(this.properties);
        return music != null ? music : MusicType.NETHER;
    }

    @Override
    public Vec3d getFogColor(float celestialAngle, float partialTicks)
    {
        if (this.properties.getFogColor() == null)
        {
            return new Vec3d(0.2, 0.03, 0.03);
        }

        return super.getFogColor(1f, partialTicks);
    }

    @Override
    protected void generateLightBrightnessTable()
    {
        for (int i = 0; i <= 15; ++i)
        {
            float f1 = 1.0F - (float)i / 15.0F;
            this.lightBrightnessTable[i] = (1.0F - f1) / (f1 * 3.0F + 1.0F) * 0.9F + 0.1F;
        }
    }

    @Override
    public IChunkGenerator createChunkGenerator()
    {
        return new ChunkGeneratorHell(this.world, this.world.getWorldInfo().isMapFeaturesEnabled(), this.world.getSeed());
    }
}
