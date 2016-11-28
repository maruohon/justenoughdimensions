package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import com.google.common.collect.ImmutableSet;
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
import net.minecraftforge.fml.common.network.ByteBufUtils;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import io.netty.buffer.ByteBuf;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDir;
    private final File dimensionFile;
    private final Map<Integer, DimensionEntry> dimensions = new HashMap<Integer, DimensionEntry>();

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

    public ImmutableSet<DimensionEntry> getRegisteredDimensions()
    {
        return ImmutableSet.<DimensionEntry>copyOf(this.dimensions.values());
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
                    DimensionEntry entry = this.parseDimensionType(dimension, object.get("dimensiontype").getAsJsonObject());

                    if (entry != null)
                    {
                        DimensionManager.registerDimension(dimension, entry.registerDimensionType());
                        this.dimensions.put(dimension, entry);
                    }
                }
                else
                {
                    JustEnoughDimensions.logger.info("Using default values for DimensionType of dimension {}", dimension);

                    DimensionEntry entry = new DimensionEntry(dimension, "DIM" + dimension, "dim_" + dimension, false, WorldProviderSurface.class);
                    DimensionManager.registerDimension(dimension, entry.registerDimensionType());
                    this.dimensions.put(dimension, entry);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DimensionEntry parseDimensionType(int dimension, JsonObject dimType)
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

        return new DimensionEntry(dimension, name, suffix, keepLoaded, providerClass);
    }

    public static class DimensionEntry
    {
        private final int id;
        private final String name;
        private final String suffix;
        private final boolean keepLoaded;
        private final Class<? extends WorldProvider> providerClass;

        public DimensionEntry(int id, String name, String suffix, boolean keepLoaded, Class<? extends WorldProvider> providerClass)
        {
            this.id = id;
            this.name = name;
            this.suffix = suffix;
            this.keepLoaded = keepLoaded;
            this.providerClass = providerClass;
        }

        public int getId()
        {
            return this.id;
        }

        public DimensionType registerDimensionType()
        {
            return DimensionType.register(this.name, this.suffix, this.id, this.providerClass, this.keepLoaded);
        }

        public void writeToByteBuf(ByteBuf buf)
        {
            buf.writeInt(this.id);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        }
    }
}
