package fi.dy.masa.justenoughdimensions.event;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(Side.CLIENT)
public class JEDEventHandlerClient
{
    private static final Map<Integer, Map<ResourceLocation, Integer>> FOLIAGE_COLORS = new HashMap<Integer, Map<ResourceLocation, Integer>>();
    private static final Map<Integer, Map<ResourceLocation, Integer>> GRASS_COLORS = new HashMap<Integer, Map<ResourceLocation, Integer>>();
    private static final Map<Integer, Map<ResourceLocation, Integer>> WATER_COLORS = new HashMap<Integer, Map<ResourceLocation, Integer>>();

    @SubscribeEvent
    public static void onGetFoliageColor(BiomeEvent.GetFoliageColor event)
    {
        event.setNewColor(getColor(ColorType.FOLIAGE, event.getBiome(), event.getOriginalColor()));
    }

    @SubscribeEvent
    public static void onGetGrassColor(BiomeEvent.GetGrassColor event)
    {
        event.setNewColor(getColor(ColorType.GRASS, event.getBiome(), event.getOriginalColor()));
    }

    @SubscribeEvent
    public static void onGetWaterColor(BiomeEvent.GetWaterColor event)
    {
        event.setNewColor(getColor(ColorType.WATER, event.getBiome(), event.getOriginalColor()));
    }

    public static void setColors(int dimension, ColorType type, Map<ResourceLocation, Integer> colorsIn)
    {
        Map<Integer, Map<ResourceLocation, Integer>> map = getColorMap(type);
        Map<ResourceLocation, Integer> colors = map.get(dimension);

        if (colors == null)
        {
            colors = new HashMap<ResourceLocation, Integer>();
            map.put(dimension, colors);
        }

        colors.clear();
        colors.putAll(colorsIn);
    }

    private static int getColor(ColorType type, Biome biome, int defaultColor)
    {
        int dimension = Minecraft.getMinecraft().world.provider.getDimension();
        Map<Integer, Map<ResourceLocation, Integer>> map = getColorMap(type);
        Map<ResourceLocation, Integer> colors = map.get(dimension);

        if (colors == null || colors.isEmpty())
        {
            return defaultColor;
        }

        Integer color = colors.get(biome.getRegistryName());
        return color != null ? color.intValue() : defaultColor;
    }

    private static Map<Integer, Map<ResourceLocation, Integer>> getColorMap(ColorType type)
    {
        switch (type)
        {
            case FOLIAGE: return FOLIAGE_COLORS;
            case GRASS:   return GRASS_COLORS;
            case WATER:   return WATER_COLORS;
            default:      return FOLIAGE_COLORS;
        }
    }

    public enum ColorType
    {
        FOLIAGE,
        GRASS,
        WATER;
    }
}
