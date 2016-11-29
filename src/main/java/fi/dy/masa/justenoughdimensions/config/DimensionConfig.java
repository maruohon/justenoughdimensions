package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import com.google.common.collect.ImmutableList;
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
    private final List<DimensionEntry> dimensions = new ArrayList<DimensionEntry>();

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

    public List<DimensionEntry> getRegisteredDimensions()
    {
        return ImmutableList.<DimensionEntry>copyOf(this.dimensions);
    }

    public void readDimensionConfig()
    {
        File file = this.dimensionFile;

        if (file.exists() && file.isFile() && file.canRead())
        {
            try
            {
                JsonParser parser = new JsonParser();
                JsonObject root = parser.parse(new FileReader(file)).getAsJsonObject();
                this.parseDimensionConfig(root);
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

    public void registerDimensions()
    {
        for (DimensionEntry entry : this.dimensions)
        {
            int dimension = entry.getId();

            if (DimensionManager.isDimensionRegistered(dimension) == false)
            {
                JustEnoughDimensions.logger.warn("Registering a dimension with ID {}...", dimension);
                DimensionManager.registerDimension(dimension, entry.registerDimensionType());
            }
            else
            {
                JustEnoughDimensions.logger.warn("A dimension with id {} is already registered, skipping it...", dimension);
            }
        }
    }

    private void parseDimensionConfig(JsonObject root) throws IllegalStateException
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
                DimensionEntry entry = null;

                if (object.has("dimensiontype") && object.get("dimensiontype").isJsonObject())
                {
                    entry = this.parseDimensionType(dimension, object.get("dimensiontype").getAsJsonObject());
                }
                else
                {
                    JustEnoughDimensions.logger.info("Using default values for the DimensionType of dimension {}", dimension);
                    entry = new DimensionEntry(dimension, "DIM" + dimension, "dim_" + dimension, false, WorldProviderSurface.class);
                }

                if (entry != null)
                {
                    if (this.dimensions.contains(entry))
                    {
                        this.dimensions.remove(entry);
                    }

                    this.dimensions.add(entry);
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
        String worldProviderName = "";
        Class<? extends WorldProvider> providerClass = WorldProviderSurface.class;

        if (dimType.has("worldprovider") && dimType.get("worldprovider").isJsonPrimitive())
        {
            worldProviderName = dimType.get("worldprovider").getAsString();

            if (worldProviderName.equals("WorldProviderSurface"))
            {
                providerClass = WorldProviderSurface.class;
            }
            else if (worldProviderName.equals("WorldProviderHell"))
            {
                providerClass = WorldProviderHell.class;
            }
            else if (worldProviderName.equals("WorldProviderEnd"))
            {
                providerClass = WorldProviderEnd.class;
            }
            else
            {
                try
                {
                    providerClass = (Class<? extends WorldProvider>) Class.forName(worldProviderName);
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.error("Failed to get a WorldProvider class for '{}'", worldProviderName);
                    e.printStackTrace();
                    return null;
                }
            }
        }

        JustEnoughDimensions.logger.info("Creating a customized DimensionType for dimension {}", dimension);

        return new DimensionEntry(dimension, name, suffix, keepLoaded, providerClass);
    }

    public static class DimensionEntry implements Comparable<DimensionEntry>
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
            JustEnoughDimensions.logger.info("Registering a DimensionType with values:" +
                    "{id: {}, name: \"{}\", suffix: \"{}\", keeploaded: {}, WorldProvider: {}}",
                    this.id, this.name, this.suffix, this.keepLoaded, this.providerClass.getName());

            return DimensionType.register(this.name, this.suffix, this.id, this.providerClass, this.keepLoaded);
        }

        public void writeToByteBuf(ByteBuf buf)
        {
            buf.writeInt(this.id);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        }

        @Override
        public int compareTo(DimensionEntry other)
        {
            if (this.getId() == other.getId())
            {
                return 0;
            }

            return this.getId() > other.getId() ? 1 : -1;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + id;
            return result;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) { return true; }
            if (other == null) { return false; }
            if (getClass() != other.getClass()) { return false; }

            return this.getId() == ((DimensionEntry) other).getId();
        }
    }
}
