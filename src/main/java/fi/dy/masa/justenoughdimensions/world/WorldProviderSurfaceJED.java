package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProviderSingle;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;

public class WorldProviderSurfaceJED extends WorldProviderJED
{
    @Override
    protected void init()
    {
        super.init();

        // If this dimension has been configured for a single biome,
        // then set it here so that it's early enough for Sponge to use it.
        String biomeName = DimensionConfig.instance().getBiomeFor(this.getDimension());
        Biome biome = biomeName != null ? Biome.REGISTRY.getObject(new ResourceLocation(biomeName)) : null;

        if (biome != null)
        {
            JustEnoughDimensions.logInfo("WorldProviderSurfaceJED.init(): Using BiomeProviderSingle with biome '{}' for dimension {}",
                    biomeName, this.getDimension());
            this.biomeProvider = new BiomeProviderSingle(biome);
        }
    }

    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z)
    {
        return false;
    }
}
