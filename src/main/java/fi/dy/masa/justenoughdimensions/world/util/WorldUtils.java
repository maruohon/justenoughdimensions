package fi.dy.masa.justenoughdimensions.world.util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.feature.WorldGeneratorBonusChest;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.network.MessageSyncWorldProviderProperties;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

public class WorldUtils
{
    private static Field field_WorldProvider_biomeProvider = null;

    static
    {
        try
        {
            field_WorldProvider_biomeProvider = ReflectionHelper.findField(WorldProvider.class, "field_76578_c", "biomeProvider");
        }
        catch (UnableToFindFieldException e)
        {
            JustEnoughDimensions.logger.error("JEDEventHandler: Reflection failed!!", e);
        }
    }

    public static void syncWorldProviderProperties(EntityPlayer player)
    {
        World world = player.getEntityWorld();

        if (world.getWorldInfo() instanceof WorldInfoJED && player instanceof EntityPlayerMP)
        {
            PacketHandler.INSTANCE.sendTo(new MessageSyncWorldProviderProperties((WorldInfoJED) world.getWorldInfo()), (EntityPlayerMP) player);
        }
    }

    public static void overrideBiomeProvider(World world)
    {
        int dimension = world.provider.getDimension();
        String biomeName = DimensionConfig.instance().getBiomeFor(dimension);
        Biome biome = biomeName != null ? Biome.REGISTRY.getObject(new ResourceLocation(biomeName)) : null;

        if (biome != null)
        {
            JustEnoughDimensions.logInfo("Overriding the BiomeProvider for dimension {} with BiomeProviderSingle" +
                " using the biome '{}' ('{}')", dimension, biomeName, biome.getBiomeName());

            BiomeProvider provider = new BiomeProviderSingle(biome);
            try
            {
                field_WorldProvider_biomeProvider.set(world.provider, provider);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to override the BiomeProvider of dimension {}", dimension);
            }
        }
    }

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
    }

    private static void createBonusChest(World world)
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
