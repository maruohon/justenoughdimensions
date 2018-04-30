package fi.dy.masa.justenoughdimensions.util;

import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import net.minecraft.client.audio.MusicTicker.MusicType;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.client.render.SkyRenderer;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;

public class ClientUtils
{
    public static boolean setRenderersFrom(WorldProvider provider, @Nullable JsonObject obj)
    {
        if (obj == null)
        {
            /*
            setRenderer(provider, null, RendererType.SKY);
            setRenderer(provider, null, RendererType.CLOUD);
            setRenderer(provider, null, RendererType.WEATHER);
            */
            return false;
        }

        boolean success = false;

        if (JEDJsonUtils.hasString(obj, "SkyRenderer"))
        {
            success |= createAndSetRendererFromName(provider, JEDJsonUtils.getString(obj, "SkyRenderer"), RendererType.SKY);
        }
        else
        {
            int skyRenderType   = JEDJsonUtils.hasInteger(obj, "SkyRenderType")   ? JEDJsonUtils.getInteger(obj, "SkyRenderType")   : 0;
            int skyDisableFlags = JEDJsonUtils.hasInteger(obj, "SkyDisableFlags") ? JEDJsonUtils.getInteger(obj, "SkyDisableFlags") : 0;

            if (skyRenderType != 0)
            {
                provider.setSkyRenderer(new SkyRenderer(skyRenderType, skyDisableFlags));
                success = true;
            }
            /*
            else
            {
                provider.setSkyRenderer(null);
            }
            */
        }

        if (JEDJsonUtils.hasString(obj, "CloudRenderer"))
        {
            success |= createAndSetRendererFromName(provider, JEDJsonUtils.getString(obj, "CloudRenderer"), RendererType.CLOUD);
        }

        if (JEDJsonUtils.hasString(obj, "WeatherRenderer"))
        {
            success |= createAndSetRendererFromName(provider, JEDJsonUtils.getString(obj, "WeatherRenderer"), RendererType.WEATHER);
        }

        return success;
    }

    @Nullable
    public static boolean createAndSetRendererFromName(WorldProvider provider, String name, RendererType type)
    {
        try
        {
            @SuppressWarnings("unchecked")
            Class<? extends IRenderHandler> clazz = (Class<? extends IRenderHandler>) Class.forName(name);

            if (clazz != null)
            {
                IRenderHandler renderer = clazz.newInstance();

                if (renderer != null)
                {
                    JustEnoughDimensions.logInfo("WorldUtils.setRenderersOnNonJEDWorld(): Setting a custom {} renderer '{}' for dimension {}",
                            type.getName(), renderer.getClass().getName(), provider.getDimension());

                    setRenderer(provider, renderer, type);

                    return true;
                }
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to create a {} renderer from class name '{}'", type.getName(), name, e);
        }

        //setRenderer(provider, null, type);

        return false;
    }

    private static void setRenderer(WorldProvider provider, IRenderHandler renderer, RendererType type)
    {
        switch (type)
        {
            case SKY:
                provider.setSkyRenderer(renderer);
                break;
            case CLOUD:
                provider.setCloudRenderer(renderer);
                break;
            case WEATHER:
                provider.setWeatherRenderer(renderer);
                break;
        }
    }

    @Nullable
    public static MusicType getMusicTypeFromProperties(JEDWorldProperties props)
    {
        String name = props.getMusicType();
        return name != null ? ClientUtils.getMusicTypeFromName(name) : null;
    }

    @Nullable
    public static MusicType getMusicTypeFromName(String name)
    {
        for (MusicType type : MusicType.values())
        {
            if (type.name().equalsIgnoreCase(name))
            {
                return type;
            }
        }

        return null;
    }

    public enum RendererType
    {
        SKY,
        CLOUD,
        WEATHER;

        public String getName()
        {
            return this.name().toLowerCase();
        }
    }
}
