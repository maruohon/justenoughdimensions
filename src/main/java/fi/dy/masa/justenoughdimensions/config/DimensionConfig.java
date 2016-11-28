package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import java.io.FileReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDir;
    private final File dimensionFile;

    private DimensionConfig(File configDir)
    {
        instance = this;
        this.configDir = configDir;
        this.dimensionFile = new File(configDir, "dimensions.json");
    }

    public static DimensionConfig create(File configDir)
    {
        return new DimensionConfig(configDir);
    }

    public static DimensionConfig instance()
    {
        return instance;
    }

    public void readConfigAndRegisterDimensions()
    {
        File file = this.dimensionFile;

        if (file.exists() && file.isFile() && file.canRead())
        {
            JsonParser parser = new JsonParser();

            try
            {
                JsonObject root = parser.parse(new FileReader(file)).getAsJsonObject();
                this.parseConfigAndRegisterDimensions(root);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to parse the config file '{}'", file.getName());
                e.printStackTrace();
            }
        }
        else if (this.configDir.isDirectory() == false)
        {
            this.configDir.mkdirs();
        }
    }

    private void parseConfigAndRegisterDimensions(JsonObject root) throws IllegalStateException
    {
        JsonArray array;
        JsonObject object;

        array = root.get("dimensions").getAsJsonArray();

        for (JsonElement el : array)
        {
            object = el.getAsJsonObject();

            if (object.has("dim") && object.get("dim").isJsonPrimitive())
            {
                int dimension = object.get("dim").getAsInt();

                if (DimensionManager.isDimensionRegistered(dimension))
                {
                    if (Configs.replaceRegisteredDimensions)
                    {
                        JustEnoughDimensions.logger.warn("A dimension with id {} is already registered, unregistering the old one...", dimension);
                        DimensionManager.unregisterDimension(dimension);
                    }
                    else
                    {
                        JustEnoughDimensions.logger.warn("A dimension with id {} is already registered, skipping it...", dimension);
                        continue;
                    }
                }

                if (object.has("dimensiontype") && object.get("dimensiontype").isJsonObject())
                {
                    DimensionType dimensionType = this.parseAndRegisterDimensionType(dimension, object.get("dimensiontype").getAsJsonObject());

                    if (dimensionType != null)
                    {
                        DimensionManager.registerDimension(dimension, dimensionType);
                    }
                }
                else
                {
                    JustEnoughDimensions.logger.info("Using default values for DimensionType of dimension {}", dimension);
                    DimensionType dimensionType = DimensionType.register("DIM" + dimension, "dim_" + dimension, dimension, WorldProviderSurface.class, false);
                    DimensionManager.registerDimension(dimension, dimensionType);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DimensionType parseAndRegisterDimensionType(int dimension, JsonObject dimType)
    {
        String name = dimType.get("name").getAsString();
        String suffix = dimType.has("suffix") ? dimType.get("suffix").getAsString() : name.toLowerCase().replace(" ", "_");

        boolean keepLoaded = dimType.has("keeploaded") && dimType.get("keeploaded").getAsBoolean();
        String worldProvider = "";
        Class<? extends WorldProvider> providerClass = WorldProviderSurface.class;

        if (dimType.has("worldprovider") && dimType.get("worldprovider").isJsonPrimitive())
        {
            worldProvider = dimType.get("worldprovider").getAsString();

            if (worldProvider.equals("WorldProviderSurface"))
            {
                providerClass = WorldProviderSurface.class;
            }
            else if (worldProvider.equals("WorldProviderHell"))
            {
                providerClass = WorldProviderHell.class;
            }
            else if (worldProvider.equals("WorldProviderEnd"))
            {
                providerClass = WorldProviderEnd.class;
            }
            else
            {
                try
                {
                    providerClass = (Class<? extends WorldProvider>) Class.forName(worldProvider);
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.error("Failed to get a WorldProvider class for '{}'", worldProvider);
                    e.printStackTrace();
                    return null;
                }
            }
        }

        JustEnoughDimensions.logger.info("Creating a customized DimensionType for dimension {} with values:" +
                "{name: {}, suffix: {}, keeploaded: {}, worldprovider: {}}", dimension, name, suffix, keepLoaded, worldProvider);

        return DimensionType.register(name, suffix, dimension, providerClass, keepLoaded);
    }
}
