package fi.dy.masa.justenoughdimensions.world.gen;

import java.util.Map;
import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.MapGenCaves;
import net.minecraft.world.gen.feature.WorldGenDungeons;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class ChunkGeneratorFlatJED extends ChunkGeneratorFlat
{
    protected final World world;
    protected final Random random;
    protected final MapGenCaves caveGenerator = new MapGenCaves();
    protected IBlockState[] cachedBlockIDs;
    protected Map<String, MapGenStructure> structureGenerators;
    protected WorldGenLakes waterLakeGenerator;
    protected WorldGenLakes lavaLakeGenerator;
    protected boolean hasDecoration;
    protected boolean hasDungeons;
    protected boolean generateCaves;
    protected boolean doModPopulation;

    @SuppressWarnings("unchecked")
    public ChunkGeneratorFlatJED(World worldIn, long seed, boolean generateStructures, String flatGeneratorSettings)
    {
        super(worldIn, seed, generateStructures, flatGeneratorSettings);

        this.world = worldIn;
        this.random = new Random(seed);

        // Unfortunately everything is private in the vanilla class...
        try
        {
            FlatGeneratorInfo flatWorldGenInfo = (FlatGeneratorInfo) ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82699_e").get(this);
            Map<String, Map<String, String>> map = flatWorldGenInfo.getWorldFeatures();

            this.cachedBlockIDs = (IBlockState[]) ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82700_c").get(this);
            this.structureGenerators = (Map<String, MapGenStructure>) ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82696_f").get(this);
            this.waterLakeGenerator = (WorldGenLakes) ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82703_i").get(this);
            this.lavaLakeGenerator = (WorldGenLakes) ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82701_j").get(this);

            this.hasDecoration = ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82697_g").getBoolean(this);
            this.hasDungeons = ObfuscationReflectionHelper.findField(ChunkGeneratorFlat.class, "field_82702_h").getBoolean(this);

            this.generateCaves = map.containsKey("caves");
            this.doModPopulation = ! map.containsKey("no_mod_population");
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.error("Failed to reflect fields from {}", ChunkGeneratorFlat.class.getName(), e);
        }
    }

    @Override
    public Chunk generateChunk(int x, int z)
    {
        ChunkPrimer chunkPrimer = new ChunkPrimer();

        for (int y = 0; y < this.cachedBlockIDs.length; ++y)
        {
            IBlockState state = this.cachedBlockIDs[y];

            if (state != null)
            {
                for (int tmpX = 0; tmpX < 16; ++tmpX)
                {
                    for (int tmpZ = 0; tmpZ < 16; ++tmpZ)
                    {
                        chunkPrimer.setBlockState(tmpX, y, tmpZ, state);
                    }
                }
            }
        }

        if (this.generateCaves)
        {
            this.caveGenerator.generate(this.world, x, z, chunkPrimer);
        }

        for (MapGenBase structureGen : this.structureGenerators.values())
        {
            structureGen.generate(this.world, x, z, chunkPrimer);
        }

        Chunk chunk = new Chunk(this.world, chunkPrimer, x, z);
        Biome[] biomeArray = this.world.getBiomeProvider().getBiomes((Biome[]) null, x * 16, z * 16, 16, 16);
        byte[] biomeByteArray = chunk.getBiomeArray();

        for (int i = 0; i < biomeByteArray.length; ++i)
        {
            biomeByteArray[i] = (byte) Biome.getIdForBiome(biomeArray[i]);
        }

        chunk.generateSkylightMap();

        return chunk;
    }

    @Override
    public void populate(int chunkX, int chunkZ)
    {
        net.minecraft.block.BlockFalling.fallInstantly = true;

        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;
        BlockPos startCorner = new BlockPos(startX, 0, startZ);
        Biome biome = this.world.getBiome(new BlockPos(startX + 16, 0, startZ + 16));
        boolean villageGenerated = false;

        this.random.setSeed(this.world.getSeed());
        long k = this.random.nextLong() / 2L * 2L + 1L;
        long l = this.random.nextLong() / 2L * 2L + 1L;
        this.random.setSeed((long) chunkX * k + (long) chunkZ * l ^ this.world.getSeed());

        if (this.doModPopulation)
        {
            ForgeEventFactory.onChunkPopulate(true, this, this.world, this.random, chunkX, chunkZ, villageGenerated);
        }

        for (MapGenStructure structure : this.structureGenerators.values())
        {
            boolean success = structure.generateStructure(this.world, this.random, chunkPos);

            if (structure instanceof MapGenVillage)
            {
                villageGenerated |= success;
            }
        }

        if (this.waterLakeGenerator != null && ! villageGenerated && this.random.nextInt(4) == 0)
        {
            this.waterLakeGenerator.generate(this.world, this.random, startCorner.add(this.random.nextInt(16) + 8, this.random.nextInt(256), this.random.nextInt(16) + 8));
        }

        if (this.lavaLakeGenerator != null && ! villageGenerated && this.random.nextInt(8) == 0)
        {
            BlockPos pos = startCorner.add(this.random.nextInt(16) + 8, this.random.nextInt(this.random.nextInt(248) + 8), this.random.nextInt(16) + 8);

            if (pos.getY() < this.world.getSeaLevel() || this.random.nextInt(10) == 0)
            {
                this.lavaLakeGenerator.generate(this.world, this.random, pos);
            }
        }

        if (this.hasDungeons)
        {
            for (int i = 0; i < 8; ++i)
            {
                (new WorldGenDungeons()).generate(this.world, this.random, startCorner.add(this.random.nextInt(16) + 8, this.random.nextInt(256), this.random.nextInt(16) + 8));
            }
        }

        if (this.hasDecoration)
        {
            biome.decorate(this.world, this.random, startCorner);
        }

        if (this.doModPopulation)
        {
            ForgeEventFactory.onChunkPopulate(false, this, this.world, this.random, chunkX, chunkZ, villageGenerated);
        }

        net.minecraft.block.BlockFalling.fallInstantly = false;
    }
}
