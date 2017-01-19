package fi.dy.masa.justenoughdimensions.world;

import java.util.List;
import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.feature.WorldGeneratorBonusChest;
import net.minecraft.world.storage.WorldInfo;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class WorldUtils
{
    public static void findAndSetWorldSpawn(World world, boolean fireEvent)
    {
        WorldInfo info = world.getWorldInfo();
        WorldSettings worldSettings = new WorldSettings(info);
        WorldProvider provider = world.provider;

        if (provider.canRespawnHere() == false)
        {
            info.setSpawn(BlockPos.ORIGIN.up(provider.getAverageGroundLevel()));
        }
        else if (info.getTerrainType() == WorldType.DEBUG_WORLD)
        {
            info.setSpawn(BlockPos.ORIGIN.up());
        }
        else
        {
            if (fireEvent && net.minecraftforge.event.ForgeEventFactory.onCreateWorldSpawn(world, worldSettings))
            {
                return;
            }

            BiomeProvider biomeProvider = provider.getBiomeProvider();
            List<Biome> list = biomeProvider.getBiomesToSpawnIn();
            Random random = new Random(world.getSeed());
            int x = 8;
            int z = 8;

            // This will not generate chunks, but only check the biome ID from the genBiomes.getInts() output
            BlockPos pos = biomeProvider.findBiomePosition(0, 0, 512, list, random);

            if (pos != null)
            {
                x = pos.getX();
                z = pos.getZ();
            }
            else
            {
                JustEnoughDimensions.logger.warn("Unable to find spawn biome");
            }

            int iterations = 0;

            // Note: The canCoordinateBeSpawn() call will actually generate chunks!
            while (provider.canCoordinateBeSpawn(x, z) == false)
            {
                x += random.nextInt(64) - random.nextInt(64);
                z += random.nextInt(64) - random.nextInt(64);
                iterations++;

                if (iterations >= 100)
                {
                    break;
                }
            }

            pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).up();
            info.setSpawn(pos);

            if (worldSettings.isBonusChestEnabled())
            {
                createBonusChest(world);
            }
        }

        // If the search fails, the spawn point may be left at sea level under ground,
        // so let's raise it to the surface (see WorldServer#createSpawnPosition() for the faulty logic)
        BlockPos pos = world.getSpawnPoint();
        pos = pos.up();
        for ( ; world.isAirBlock(pos) == false; pos = pos.up())
        {
        }

        world.getWorldInfo().setSpawn(pos);
    }

    public static void createBonusChest(World world)
    {
        WorldInfo info = world.getWorldInfo();
        WorldGeneratorBonusChest gen = new WorldGeneratorBonusChest();

        for (int i = 0; i < 10; ++i)
        {
            int x = info.getSpawnX() + world.rand.nextInt(6) - world.rand.nextInt(6);
            int z = info.getSpawnZ() + world.rand.nextInt(6) - world.rand.nextInt(6);
            BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).up();

            if (gen.generate(world, world.rand, pos))
            {
                break;
            }
        }
    }
}
