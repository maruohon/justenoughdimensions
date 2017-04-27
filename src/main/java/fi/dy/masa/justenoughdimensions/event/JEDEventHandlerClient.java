package fi.dy.masa.justenoughdimensions.event;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.ColorType;

@Mod.EventBusSubscriber(Side.CLIENT)
public class JEDEventHandlerClient
{
    private static final Map<ResourceLocation, Integer> FOLIAGE_COLORS = new HashMap<ResourceLocation, Integer>();
    private static final Map<ResourceLocation, Integer> GRASS_COLORS = new HashMap<ResourceLocation, Integer>();
    private static final Map<ResourceLocation, Integer> WATER_COLORS = new HashMap<ResourceLocation, Integer>();
    private static boolean hasFoliageColors;
    private static boolean hasGrassColors;
    private static boolean hasWaterColors;

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

    public static void setColors(ColorType type, @Nullable Map<ResourceLocation, Integer> colorsIn)
    {
        Map<ResourceLocation, Integer> colors = getColorMap(type);
        colors.clear();

        if (colorsIn != null)
        {
            colors.putAll(colorsIn);
        }

        setFlag(type, colors.isEmpty() == false);
    }

    private static int getColor(ColorType type, Biome biome, int defaultColor)
    {
        if (getFlag(type))
        {
            Integer color = getColorMap(type).get(biome.getRegistryName());
            return color != null ? color.intValue() : defaultColor;
        }

        return defaultColor;
    }

    private static Map<ResourceLocation, Integer> getColorMap(ColorType type)
    {
        switch (type)
        {
            case FOLIAGE:   return FOLIAGE_COLORS;
            case GRASS:     return GRASS_COLORS;
            case WATER:     return WATER_COLORS;
            default:        return FOLIAGE_COLORS;
        }
    }

    private static boolean getFlag(ColorType type)
    {
        switch (type)
        {
            case FOLIAGE:   return hasFoliageColors;
            case GRASS:     return hasGrassColors;
            case WATER:     return hasWaterColors;
            default:        return false;
        }
    }

    private static void setFlag(ColorType type, boolean value)
    {
        switch (type)
        {
            case FOLIAGE:   hasFoliageColors = value; break;
            case GRASS:     hasGrassColors = value; break;
            case WATER:     hasWaterColors = value; break;
            default:
        }
    }
}
