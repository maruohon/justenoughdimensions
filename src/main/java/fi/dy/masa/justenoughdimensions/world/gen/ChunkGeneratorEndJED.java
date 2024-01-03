package fi.dy.masa.justenoughdimensions.world.gen;

import java.util.Random;
import net.minecraft.block.BlockFalling;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkGeneratorEnd;

import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;

public class ChunkGeneratorEndJED extends ChunkGeneratorEnd
{
    protected final World world;
    protected final int dimension;
    protected final Random rand;

    public ChunkGeneratorEndJED(World world, boolean mapFeaturesEnabled, long seed, BlockPos spawn)
    {
        super(world, mapFeaturesEnabled, seed, spawn);

        this.world = world;
        this.dimension = world.provider.getDimension();
        this.rand = new Random(seed);
    }

    @Override
    public void populate(int x, int z)
    {
        long distSq = (long) x * (long) x + (long) z * (long) z;
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(this.dimension);

        if (distSq > 64L || props == null || props.getDisableEndSpikes() == false)
        {
            super.populate(x, z);
        }
        else
        {
            BlockFalling.fallInstantly = true;
            net.minecraftforge.event.ForgeEventFactory.onChunkPopulate(true, this, this.world, this.rand, x, z, false);
            net.minecraftforge.event.ForgeEventFactory.onChunkPopulate(false, this, this.world, this.rand, x, z, false);
            BlockFalling.fallInstantly = false;
        }
    }
}
