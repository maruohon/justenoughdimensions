package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.command.CommandException;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.network.MessageSyncDimensions;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.world.WorldProviderJED;
import io.netty.buffer.ByteBuf;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDir;
    private final File dimensionFile;
    private final List<DimensionEntry> dimensions = new ArrayList<DimensionEntry>();
    private final Map<Integer, NBTTagCompound> customWorldInfoDimensions = new HashMap<Integer, NBTTagCompound>(8);

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

    public boolean useCustomWorldInfoFor(int dimension)
    {
        return this.customWorldInfoDimensions.containsKey(dimension);
    }

    public NBTTagCompound getWorldInfoValues(int dimension, NBTTagCompound nbt)
    {
        NBTTagCompound nbtDim = this.customWorldInfoDimensions.get(dimension);

        if (nbtDim != null)
        {
            for (String key : nbtDim.getKeySet())
            {
                NBTBase tag = nbtDim.getTag(key);

                if (tag != null)
                {
                    nbt.setTag(key, tag.copy());
                }
            }
        }

        return nbt;
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
                //JustEnoughDimensions.logger.info("Registering a dimension with ID {}...", dimension);
                DimensionManager.registerDimension(dimension, entry.registerDimensionType());
            }
            else
            {
                JustEnoughDimensions.logger.warn("A dimension with id {} is already registered, skipping it...", dimension);
            }
        }
    }

    public void registerDimension(int dimension) throws CommandException
    {
        DimensionEntry entry = this.createDefaultDimensionEntry(dimension);
        this.registerDimension(dimension, entry);
    }

    public void registerDimension(int dimension, String name, String suffix, boolean keepLoaded, String providerClassName) throws CommandException
    {
        Class<? extends WorldProvider> providerClass = this.getProviderClass(providerClassName);

        if (providerClass == null)
        {
            throw new NumberInvalidException("jed.commands.error.invalid.worldprovider.name", providerClassName);
        }

        this.registerDimension(dimension, new DimensionEntry(dimension, name, suffix, keepLoaded, providerClass));
    }

    private void registerDimension(int dimension, DimensionEntry entry) throws CommandException
    {
        if (DimensionManager.isDimensionRegistered(dimension))
        {
            throw new NumberInvalidException("jed.commands.error.dimension.already.registered", Integer.valueOf(dimension));
        }

        DimensionManager.registerDimension(dimension, entry.registerDimensionType());
        this.dimensions.add(entry);
        this.saveConfig();
        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));
    }

    public void removeDimensionAndSaveConfig(int dimension)
    {
        for (int i = 0; i < this.dimensions.size(); i++)
        {
            DimensionEntry entry = this.dimensions.get(i);

            if (entry.getId() == dimension)
            {
                this.dimensions.remove(i);
                break;
            }
        }

        this.saveConfig();
    }

    private void saveConfig()
    {
        Collections.sort(this.dimensions);

        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();

        for (DimensionEntry dimEntry : this.dimensions)
        {
            array.add(dimEntry.toJson());
        }

        root.add("dimensions", array);

        try
        {
            FileWriter writer = new FileWriter(this.dimensionFile);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writer.write(gson.toJson(root));
            writer.close();
        }
        catch (IOException e)
        {
            JustEnoughDimensions.logger.warn("Failed to write dimensions.json");
            e.printStackTrace();
        }
    }

    private void parseDimensionConfig(JsonObject root) throws IllegalStateException
    {
        JsonArray array;
        JsonObject object;

        JustEnoughDimensions.logger.info("Reading the dimensions.json config...");
        this.customWorldInfoDimensions.clear();

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
                    //JustEnoughDimensions.logger.info("Using default values for the DimensionType of dimension {}", dimension);
                    entry = this.createDefaultDimensionEntry(dimension);
                }

                if (entry != null)
                {
                    if (object.has("worldinfo") && object.get("worldinfo").isJsonObject())
                    {
                        JsonObject obj = object.get("worldinfo").getAsJsonObject();
                        entry.setWorldInfoJson(obj);
                        this.parseAndSetCustomWorldInfoValues(dimension, obj);
                    }

                    if (this.dimensions.contains(entry))
                    {
                        this.dimensions.remove(entry);
                    }

                    this.dimensions.add(entry);
                }
            }
        }
    }

    private void parseAndSetCustomWorldInfoValues(int dimension, JsonObject object) throws IllegalStateException
    {
        NBTTagCompound nbt = new NBTTagCompound();

        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            JsonElement element = entry.getValue();

            if (element.isJsonPrimitive())
            {
                String key = entry.getKey();
                NBTBase tag = this.getTagForValue(key, element);

                if (tag != null)
                {
                    nbt.setTag(key, tag);
                }
            }
        }

        this.customWorldInfoDimensions.put(dimension, nbt);
    }

    private NBTBase getTagForValue(String key, JsonElement element)
    {
        if (key.equals("RandomSeed"))           { return new NBTTagLong(    element.getAsLong()     ); }
        if (key.equals("generatorName"))        { return new NBTTagString(  element.getAsString()   ); }
        if (key.equals("generatorVersion"))     { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("generatorOptions"))     { return new NBTTagString(  element.getAsString()   ); }
        if (key.equals("GameType"))             { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("MapFeatures"))          { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("SpawnX"))               { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("SpawnY"))               { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("SpawnZ"))               { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("Time"))                 { return new NBTTagLong(    element.getAsLong()     ); }
        if (key.equals("DayTime"))              { return new NBTTagLong(    element.getAsLong()     ); }
        if (key.equals("LastPlayed"))           { return new NBTTagLong(    element.getAsLong()     ); }
        if (key.equals("SizeOnDisk"))           { return new NBTTagLong(    element.getAsLong()     ); }
        if (key.equals("LevelName"))            { return new NBTTagString(  element.getAsString()   ); }
        if (key.equals("version"))              { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("clearWeatherTime"))     { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("rainTime"))             { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("raining"))              { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("thunderTime"))          { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("thundering"))           { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("hardcore"))             { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("initialized"))          { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("allowCommands"))        { return new NBTTagByte(    element.getAsByte()     ); }

        if (key.equals("Difficulty"))           { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("DifficultyLocked"))     { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("BorderCenterX"))        { return new NBTTagDouble(  element.getAsDouble()   ); }
        if (key.equals("BorderCenterZ"))        { return new NBTTagDouble(  element.getAsDouble()   ); }
        if (key.equals("BorderSize"))           { return new NBTTagDouble(  element.getAsDouble()   ); }
        if (key.equals("BorderSizeLerpTime"))   { return new NBTTagLong(    element.getAsLong()     ); }
        if (key.equals("BorderSizeLerpTarget")) { return new NBTTagDouble(  element.getAsDouble()   ); }
        if (key.equals("BorderSafeZone"))       { return new NBTTagDouble(  element.getAsDouble()   ); }
        if (key.equals("BorderDamagePerBlock")) { return new NBTTagDouble(  element.getAsDouble()   ); }
        if (key.equals("BorderWarningBlocks"))  { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("BorderWarningTime"))    { return new NBTTagInt(     element.getAsInt()      ); }

        if (key.equals("GameRules") && element.isJsonObject())
        {
            NBTTagCompound tag = new NBTTagCompound();
            JsonObject obj = element.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : obj.entrySet())
            {
                tag.setString(entry.getKey(), entry.getValue().getAsString());
            }

            return tag;
        }

        JustEnoughDimensions.logger.warn("Unrecognized option in worldinfo.values: '{} = {}'", key, element.getAsString());
        return null;
    }

    private DimensionEntry createDefaultDimensionEntry(int dimension)
    {
        return new DimensionEntry(dimension, "DIM" + dimension, "dim_" + dimension, false, WorldProviderJED.class);
    }

    private DimensionEntry parseDimensionType(int dimension, JsonObject dimType)
    {
        String name = (dimType.has("name") && dimType.get("name").isJsonPrimitive()) ?
                dimType.get("name").getAsString() : "DIM" + dimension;
        if (StringUtils.isBlank(name)) { name = "DIM" + dimension; }

        String suffix = (dimType.has("suffix") && dimType.get("name").isJsonPrimitive()) ?
                dimType.get("suffix").getAsString() : name.toLowerCase().replace(" ", "_");

        boolean keepLoaded = dimType.has("keeploaded") && dimType.get("name").isJsonPrimitive() && dimType.get("keeploaded").getAsBoolean();

        Class<? extends WorldProvider> providerClass = WorldProviderJED.class;

        if (dimType.has("worldprovider") && dimType.get("worldprovider").isJsonPrimitive())
        {
            providerClass = this.getProviderClass(dimType.get("worldprovider").getAsString());
        }

        if (providerClass == null)
        {
            providerClass = WorldProviderJED.class;

            JustEnoughDimensions.logger.warn("Failed to get a WorldProvider for name '{}', using {} as a fall-back",
                    dimType.get("worldprovider").getAsString(), getNameForWorldProvider(providerClass));
        }

        //JustEnoughDimensions.logger.info("Creating a customized DimensionType for dimension {}", dimension);

        return new DimensionEntry(dimension, name, suffix, keepLoaded, providerClass);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends WorldProvider> getProviderClass(String providerClassName)
    {
        Class<? extends WorldProvider> providerClass;

        if (providerClassName.equals("WorldProviderJED"))
        {
            providerClass = WorldProviderJED.class;
        }
        else if (providerClassName.equals("WorldProviderSurface"))
        {
            providerClass = WorldProviderSurface.class;
        }
        else if (providerClassName.equals("WorldProviderHell"))
        {
            providerClass = WorldProviderHell.class;
        }
        else if (providerClassName.equals("WorldProviderEnd"))
        {
            providerClass = WorldProviderEnd.class;
        }
        else
        {
            try
            {
                providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to get a WorldProvider class for '{}'", providerClassName);
                e.printStackTrace();
                return null;
            }
        }

        return providerClass;
    }

    private static String getNameForWorldProvider(@Nonnull Class<? extends WorldProvider> clazz)
    {
        String provName = clazz.getName();

        // These ones are supported by their simple class names in this code
        if (provName.startsWith("net.minecraft.world.") ||
            provName.equals(WorldProviderJED.class.getName()))
        {
            return clazz.getSimpleName();
        }

        return provName;
    }

    public static class DimensionEntry implements Comparable<DimensionEntry>
    {
        private final int id;
        private final String name;
        private final String suffix;
        private final boolean keepLoaded;
        private final Class<? extends WorldProvider> providerClass;
        private JsonObject worldInfojson;

        public DimensionEntry(int id, String name, String suffix, boolean keepLoaded, @Nonnull Class<? extends WorldProvider> providerClass)
        {
            this.id = id;
            this.name = name;
            this.suffix = suffix;
            this.keepLoaded = keepLoaded;
            this.providerClass = providerClass;
        }

        public DimensionEntry setWorldInfoJson(JsonObject obj)
        {
            this.worldInfojson = obj;
            return this;
        }

        public int getId()
        {
            return this.id;
        }

        public DimensionType registerDimensionType()
        {
            JustEnoughDimensions.logger.info("Registering a DimensionType with values:" +
                    "{id: {}, name: \"{}\", suffix: \"{}\", keepLoaded: {}, WorldProvider: {}}",
                    this.id, this.name, this.suffix, this.keepLoaded, getNameForWorldProvider(this.providerClass));

            return DimensionType.register(this.name, this.suffix, this.id, this.providerClass, this.keepLoaded);
        }

        public void writeToByteBuf(ByteBuf buf)
        {
            buf.writeInt(this.id);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
        }

        public JsonObject toJson()
        {
            JsonObject jsonEntry = new JsonObject();
            jsonEntry.addProperty("dim", this.getId());

            JsonObject worldType = new JsonObject();
            worldType.addProperty("name", this.name);
            worldType.addProperty("suffix", this.suffix);
            worldType.addProperty("keeploaded", this.keepLoaded);
            worldType.addProperty("worldprovider", getNameForWorldProvider(this.providerClass));
            jsonEntry.add("dimensiontype", worldType);

            if (this.worldInfojson != null)
            {
                jsonEntry.add("worldinfo", this.worldInfojson);
            }

            return jsonEntry;
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
