package fi.dy.masa.justenoughdimensions.event;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.ColorType;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;

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

    @Nullable
    public static Map<ResourceLocation, Integer> getColorMap(@Nullable JsonObject obj, ColorType type)
    {
        String key = type.getKeyName();

        if (obj != null && obj.has(key) && obj.get(key).isJsonArray())
        {
            Map<ResourceLocation, Integer> colors = new HashMap<ResourceLocation, Integer>();
            JsonArray arr = obj.getAsJsonArray(key);

            for (JsonElement el : arr)
            {
                if (el.isJsonObject())
                {
                    JsonObject o = el.getAsJsonObject();

                    if (o.has("color"))
                    {
                        String strColor = o.get("color").getAsString();

                        if (o.has("biome"))
                        {
                            colors.put(new ResourceLocation(o.get("biome").getAsString()), JEDStringUtils.hexStringToInt(strColor));
                        }
                        else if (o.has("biome_regex"))
                        {
                            addColorForBiomeRegex(o.get("biome_regex").getAsString(), JEDStringUtils.hexStringToInt(strColor), colors);
                        }
                    }
                }
            }

            return colors;
        }

        return null;
    }

    private static void addColorForBiomeRegex(String regex, int color, Map<ResourceLocation, Integer> colors)
    {
        try
        {
            Pattern pattern = Pattern.compile(regex);

            // ForgeRegistries.BIOMES.getKeys() will fail in a built mod in 1.10.2, due to Forge bug #3427
            for (ResourceLocation rl : Biome.REGISTRY.getKeys())
            {
                if (pattern.matcher(rl.toString()).matches())
                {
                    colors.put(rl, color);
                }
            }
        }
        catch (PatternSyntaxException e)
        {
            JustEnoughDimensions.logger.warn("DimensionConfig.addColorForBiomeRegex(): Invalid regular expression", e);
        }
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
