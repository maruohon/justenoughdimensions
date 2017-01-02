package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderHell;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class WorldProviderHellJED extends WorldProviderJED
{
    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z)
    {
        return true;
    }

    @Override
    public void createBiomeProvider()
    {
        this.biomeProvider = new BiomeProviderSingle(Biomes.HELL);
        this.doesWaterVaporize = true;
        this.hasNoSky = true;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks)
    {
        return 0.5F;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getFogColor(float celestialAngle, float partialTicks)
    {
        if (this.fogColor == null)
        {
            return new Vec3d(0.2, 0.03, 0.03);
        }

        return super.getFogColor(celestialAngle, partialTicks);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean doesXZShowFog(int x, int z)
    {
        return true;
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
        return new ChunkProviderHell(this.world, this.world.getWorldInfo().isMapFeaturesEnabled(), this.world.getSeed());
    }

    @Override
    public boolean isSurfaceWorld()
    {
        return false;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z)
    {
        return false;
    }

    @Override
    public boolean canRespawnHere()
    {
        return false;
    }
}
