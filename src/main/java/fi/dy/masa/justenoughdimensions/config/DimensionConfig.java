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
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.network.MessageSyncDimensions;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDirConfigs;
    private final File dimensionFileConfigs;
    private final Map<Integer, DimensionConfigEntry> dimensions = new HashMap<Integer, DimensionConfigEntry>();
    private final Map<Integer, NBTTagCompound> customWorldInfo = new HashMap<Integer, NBTTagCompound>(8);
    private final Map<Integer, NBTTagCompound> onetimeWorldInfo = new HashMap<Integer, NBTTagCompound>(8);
    private JsonObject dimBuilderData = new JsonObject();

    private DimensionConfig(File configDir)
    {
        instance = this;
        this.configDirConfigs = configDir;
        this.dimensionFileConfigs = new File(configDir, "dimensions.json");
    }

    public static DimensionConfig create(File configDir)
    {
        return new DimensionConfig(configDir);
    }

    public static DimensionConfig instance()
    {
        return instance;
    }

    public List<DimensionConfigEntry> getRegisteredDimensions()
    {
        return ImmutableList.<DimensionConfigEntry>copyOf(this.dimensions.values());
    }

    public boolean useCustomWorldInfoFor(int dimension)
    {
        return this.customWorldInfo.containsKey(dimension);
    }

    @Nullable
    public String getBiomeFor(int dimension)
    {
        DimensionConfigEntry entry = this.dimensions.get(dimension);
        return entry != null ? entry.getBiome() : null;
    }

    public void setWorldInfoValues(int dimension, NBTTagCompound tagIn, WorldInfoType type)
    {
        Map<Integer, NBTTagCompound> map = type == WorldInfoType.ONE_TIME ? this.onetimeWorldInfo : this.customWorldInfo;
        NBTTagCompound dimNBT = map.get(dimension);

        if (dimNBT != null)
        {
            tagIn.merge(dimNBT);
        }
    }

    private File getConfigFile(File worldDir)
    {
        File file = null;

        if (worldDir != null)
        {
            file = new File(new File(worldDir, Reference.MOD_ID), "dimensions.json");
        }

        if (file == null || file.exists() == false || file.isFile() == false || file.canRead() == false)
        {
            file = this.dimensionFileConfigs;
        }

        return file;
    }

    /**
     * Only call this method after the overworld has been loaded
     */
    public void readDimensionConfig()
    {
        this.readDimensionConfig(DimensionManager.getCurrentSaveRootDirectory());
    }

    public void readDimensionConfig(File worldDir)
    {
        File file = this.getConfigFile(worldDir);

        if (file.exists() && file.isFile() && file.canRead())
        {
            try
            {
                JsonParser parser = new JsonParser();
                JsonElement rootElement = parser.parse(new FileReader(file));

                if (rootElement != null)
                {
                    this.parseDimensionConfig(rootElement);
                }
                else
                {
                    JustEnoughDimensions.logger.warn("The dimensions.json config was empty");
                }
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to parse the config file '{}'", file.getName());
                e.printStackTrace();
            }
        }
        else if (this.configDirConfigs.isDirectory() == false)
        {
            this.configDirConfigs.mkdirs();
        }
    }

    public void registerDimensions()
    {
        for (DimensionConfigEntry entry : this.dimensions.values())
        {
            this.registerDimension(entry.getId(), entry);
        }

        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));
    }

    public void registerOverriddenDimensions()
    {
        if (Configs.enableReplacingRegisteredDimensions)
        {
            for (DimensionConfigEntry entry : this.dimensions.values())
            {
                if (DimensionManager.isDimensionRegistered(entry.getId()))
                {
                    if (Configs.enableUnregisteringDimensions && entry.getUnregister())
                    {
                        JustEnoughDimensions.logInfo("Unregistering dimension {}...", entry.getId());
                        DimensionManager.unregisterDimension(entry.getId());
                    }
                    else if (entry.getOverride())
                    {
                        this.registerDimension(entry.getId(), entry);
                    }
                }
            }
        }
    }

    private boolean registerDimension(int dimension, DimensionConfigEntry entry)
    {
        if (entry.getUnregister() || entry.hasDimensionTypeEntry() == false)
        {
            return false;
        }

        if (DimensionManager.isDimensionRegistered(dimension) == false)
        {
            JustEnoughDimensions.logInfo("Registering a dimension with ID {}...", dimension);
            DimensionManager.registerDimension(dimension, entry.getDimensionTypeEntry().registerDimensionType());
            return true;
        }
        else if (Configs.enableReplacingRegisteredDimensions && entry.getOverride())
        {
            if (DimensionManager.getWorld(dimension) == null)
            {
                JustEnoughDimensions.logInfo("Overriding dimension {}...", dimension);
                DimensionManager.unregisterDimension(dimension);
                DimensionManager.registerDimension(dimension, entry.getDimensionTypeEntry().registerDimensionType());
                return true;
            }
            else
            {
                JustEnoughDimensions.logger.warn("Dimension {} is already registered and currently loaded, can't override it...", dimension);
            }
        }
        else
        {
            JustEnoughDimensions.logger.warn("Dimension {} is already registered, skipping it...", dimension);
        }

        return false;
    }

    public String registerDimensionFromConfig(int dimension) throws CommandException
    {
        DimensionConfigEntry entry = this.dimensions.get(dimension);

        if (entry != null)
        {
            if (entry.getUnregister())
            {
                CommandJED.throwNumber("register.from.config.unregister.set", Integer.valueOf(dimension));
            }

            if (this.registerDimension(dimension, entry))
            {
                PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));
                return entry.getDescription();
            }
            else
            {
                CommandJED.throwNumber("register.from.config", Integer.valueOf(dimension));
            }
        }

        CommandJED.throwNumber("register.not.in.config", Integer.valueOf(dimension));
        return "";
    }

    public String registerNewDimension(int dimension) throws CommandException
    {
        DimensionConfigEntry entry = new DimensionConfigEntry(dimension);
        entry.setDimensionTypeEntry(this.createDefaultDimensionTypeEntry(dimension));
        return this.registerNewDimension(dimension, entry);
    }

    private String registerNewDimension(int dimension, DimensionConfigEntry entry) throws CommandException
    {
        boolean success = this.registerDimension(dimension, entry);

        if (success == false)
        {
            CommandJED.throwNumber("dimension.already.registered", Integer.valueOf(dimension));
        }

        this.dimensions.put(dimension, entry);
        this.saveConfig();
        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));

        return entry.getDescription();
    }

    public String registerNewDimension(int dimension, String name, String suffix, boolean keepLoaded,
            String providerClassName, boolean override) throws CommandException
    {
        Class<? extends WorldProvider> providerClass = DimensionTypeEntry.getProviderClass(providerClassName);

        if (providerClass == null)
        {
            CommandJED.throwCommand("invalid.worldprovider.name", providerClassName);
        }

        DimensionConfigEntry entry = new DimensionConfigEntry(dimension);
        entry.setDimensionTypeEntry(new DimensionTypeEntry(dimension, name, suffix, keepLoaded, providerClass));
        entry.setOverride(override);

        return this.registerNewDimension(dimension, entry);
    }

    public void unregisterCustomDimensions()
    {
        for (DimensionConfigEntry entry : this.dimensions.values())
        {
            if (entry.getUnregister() == false &&
                entry.getOverride() == false &&
                entry.hasDimensionTypeEntry() &&
                DimensionManager.isDimensionRegistered(entry.getId()))
            {
                JustEnoughDimensions.logInfo("Unregistering dimension {}...", entry.getId());
                DimensionManager.unregisterDimension(entry.getId());
            }
        }
    }

    public void removeDimensionAndSaveConfig(int dimension)
    {
        this.removeDimension(dimension);
        this.saveConfig();
    }

    private void removeDimension(int dimension)
    {
        this.dimensions.remove(dimension);
        this.customWorldInfo.remove(dimension);
        this.onetimeWorldInfo.remove(dimension);
    }

    private void saveConfig()
    {
        List<DimensionConfigEntry> dims = new ArrayList<DimensionConfigEntry>();
        dims.addAll(this.getRegisteredDimensions());
        Collections.sort(dims);

        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();

        for (DimensionConfigEntry dimEntry : dims)
        {
            array.add(dimEntry.toJson());
        }

        root.add("dimensions", array);

        try
        {
            FileWriter writer = new FileWriter(this.getConfigFile(DimensionManager.getCurrentSaveRootDirectory()));
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

    private void parseDimensionConfig(JsonElement rootElement) throws IllegalStateException
    {
        JustEnoughDimensions.logInfo("Reading the dimensions.json config...");

        if (rootElement == null || rootElement.isJsonObject() == false ||
            rootElement.getAsJsonObject().has("dimensions") == false ||
            rootElement.getAsJsonObject().get("dimensions").isJsonArray() == false)
        {
            JustEnoughDimensions.logger.warn("The dimensions.json config is missing some of the top level elements...");
            return;
        }

        JsonArray array = rootElement.getAsJsonObject().get("dimensions").getAsJsonArray();
        JsonObject object;
        int count = 0;
        this.customWorldInfo.clear();
        this.onetimeWorldInfo.clear();
        this.dimensions.clear();

        for (JsonElement el : array)
        {
            object = el.getAsJsonObject();

            if (object.has("dim") && object.get("dim").isJsonPrimitive())
            {
                this.parseDimensionConfigEntry(object.get("dim").getAsInt(), object);
                count++;
            }
        }

        JustEnoughDimensions.logInfo("Read {} dimension entries from the config", count);
    }

    private void parseDimensionConfigEntry(int dimension, JsonObject object)
    {
        DimensionConfigEntry configEntry = new DimensionConfigEntry(dimension);

        boolean override = object.has("override") && object.get("override").isJsonPrimitive() &&
                object.get("override").getAsBoolean();
        boolean unregister = object.has("unregister") && object.get("unregister").isJsonPrimitive() &&
                object.get("unregister").getAsBoolean();
        String biome = object.has("biome") && object.get("biome").isJsonPrimitive() ?
                object.get("biome").getAsString() : null;

        configEntry.setOverride(override);
        configEntry.setUnregister(unregister);
        configEntry.setBiome(biome);

        if (object.has("worldinfo") && object.get("worldinfo").isJsonObject())
        {
            JsonObject obj = object.get("worldinfo").getAsJsonObject();
            configEntry.setWorldInfoJson(obj);
            this.customWorldInfo.put(dimension, this.parseAndGetCustomWorldInfoValues(dimension, obj));
        }

        if (object.has("worldinfo_onetime") && object.get("worldinfo_onetime").isJsonObject())
        {
            JsonObject obj = object.get("worldinfo_onetime").getAsJsonObject();
            configEntry.setOneTimeWorldInfoJson(obj);
            this.onetimeWorldInfo.put(dimension, this.parseAndGetCustomWorldInfoValues(dimension, obj));
        }

        if (object.has("dimensiontype") && object.get("dimensiontype").isJsonObject())
        {
            JsonObject obj = object.get("dimensiontype").getAsJsonObject();
            configEntry.setDimensionTypeEntry(this.parseDimensionTypeEntry(dimension, obj));
        }

        this.dimensions.put(dimension, configEntry);
    }

    private DimensionTypeEntry createDefaultDimensionTypeEntry(int dimension)
    {
        return new DimensionTypeEntry(dimension, "DIM" + dimension, "dim_" + dimension, false, WorldProviderSurfaceJED.class);
    }

    public void dimbuilderClear()
    {
        this.dimBuilderData = new JsonObject();
    }

    public void dimbuilderDimtype(int id, String name, String suffix, String keepLoaded, String worldProvider)
    {
        JsonObject dimType = this.getOrCreateNestedObject(this.dimBuilderData, "dimensiontype");
        dimType.add("id", new JsonPrimitive(id));
        dimType.add("name", new JsonPrimitive(name));
        dimType.add("suffix", new JsonPrimitive(suffix));
        dimType.add("keeploaded", new JsonPrimitive(keepLoaded));
        dimType.add("worldprovider", new JsonPrimitive(worldProvider));
    }

    public void dimbuilderSet(String key, String value, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") || key.equals("unregister") || key.equals("biome"))
        {
            obj.add(key, new JsonPrimitive(value));
        }
        else if (key.equals("id") || key.equals("name") || key.equals("suffix") || key.equals("keeploaded") ||
                 key.equals("worldprovider") || key.equals("vanilladimensiontype"))
        {
            this.getOrCreateNestedObject(obj, "dimensiontype").add(key, new JsonPrimitive(value));
        }
        else
        {
            obj = this.getOrCreateNestedObject(obj, type.getKeyName());

            if (this.isJEDProperty(key))
            {
                obj = this.getOrCreateNestedObject(obj, "JED");
            }

            obj.add(key, new JsonPrimitive(value));
        }
    }

    public boolean dimbuilderRemove(String key, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") || key.equals("unregister") || key.equals("biome"))
        {
            return obj.remove(key) != null;
        }
        else if (key.equals("id") || key.equals("name") || key.equals("suffix") || key.equals("keeploaded") ||
                 key.equals("worldprovider") || key.equals("vanilladimensiontype"))
        {
            obj = this.getNestedObject(obj, "dimensiontype", false);
            return obj != null ? obj.remove(key) != null : false;
        }
        else
        {
            obj = this.getNestedObject(obj, type.getKeyName(), false);

            if (obj != null)
            {
                if (this.isJEDProperty(key))
                {
                    obj = this.getNestedObject(obj, "JED", false);
                }

                return obj != null ? obj.remove(key) != null : false;
            }

            return false;
        }
    }

    public void dimbuilderList(@Nullable String key, WorldInfoType type, ICommandSender sender) throws CommandException
    {
        if (key != null)
        {
            JsonPrimitive prim = this.getDimbuilderPrimitive(key, type);

            if (prim != null)
            {
                sender.sendMessage(new TextComponentString(key + " = " + prim.getAsString()));
            }
            else
            {
                CommandJED.throwCommand("dimbuilder.list", key);
            }
        }
        else
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JustEnoughDimensions.logger.info("==== Dim Builder list start ====");
            JustEnoughDimensions.logger.info("\n" + gson.toJson(this.dimBuilderData));
            JustEnoughDimensions.logger.info("==== Dim Builder list end ====");
            sender.sendMessage(new TextComponentTranslation("jed.commands.info.output.printed.to.console"));
        }
    }

    public boolean dimbuilderReadFrom(int dimension)
    {
        DimensionConfigEntry entry = this.dimensions.get(dimension);

        if (entry != null)
        {
            this.dimBuilderData = entry.toJson();
            this.dimBuilderData.remove("dim");
            return true;
        }

        return false;
    }

    public void dimbuilderSaveAs(int dimension)
    {
        this.removeDimension(dimension);
        this.parseDimensionConfigEntry(dimension, this.dimBuilderData);
        this.saveConfig();
    }

    public void dimbuilderCreateAs(int dimension) throws CommandException
    {
        this.dimbuilderSaveAs(dimension);
        this.registerDimensionFromConfig(dimension);
    }

    private JsonObject getOrCreateNestedObject(JsonObject parent, String key)
    {
        return this.getNestedObject(parent, key, true);
    }

    @Nullable
    private JsonObject getNestedObject(JsonObject parent, String key, boolean create)
    {
        if (parent.has(key) == false || parent.get(key).isJsonObject() == false)
        {
            if (create == false)
            {
                return null;
            }

            JsonObject obj = new JsonObject();
            parent.add(key, obj);
            return obj;
        }
        else
        {
            return parent.get(key).getAsJsonObject();
        }
    }

    @Nullable
    private JsonPrimitive getDimbuilderPrimitive(String key, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if ((key.equals("override") || key.equals("unregister") || key.equals("biome")) &&
                obj.has(key) && obj.get(key).isJsonPrimitive())
        {
            return obj.get(key).getAsJsonPrimitive();
        }

        if (key.equals("id") || key.equals("name") || key.equals("suffix") || key.equals("keeploaded") ||
            key.equals("worldprovider") || key.equals("vanilladimensiontype"))
        {
            if (obj.has("dimensiontype") && obj.get("dimensiontype").isJsonObject())
            {
                obj = obj.get("dimensiontype").getAsJsonObject();
                return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
            }
            else
            {
                return null;
            }
        }

        if (obj.has(type.getKeyName()) && obj.get(type.getKeyName()).isJsonObject())
        {
            obj = obj.get(type.getKeyName()).getAsJsonObject();

            if (this.isJEDProperty(key) && obj.has("JED") && obj.get("JED").isJsonObject())
            {
                obj = obj.get("JED").getAsJsonObject();
                // The requested key exists inside the JED object
                return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
            }
            // The requested key is a value directly inside the worldinfo object
            else if (obj.has(key) && obj.get(key).isJsonPrimitive())
            {
                return obj.get(key).getAsJsonPrimitive();
            }
        }

        return null;
    }

    private NBTTagCompound parseAndGetCustomWorldInfoValues(int dimension, JsonObject object) throws IllegalStateException
    {
        NBTTagCompound nbt = new NBTTagCompound();

        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            JsonElement element = entry.getValue();
            String key = entry.getKey();
            NBTBase tag = this.getTagForValue(key, element);

            if (tag != null)
            {
                nbt.setTag(key, tag);
            }
        }

        return nbt;
    }

    private boolean isJEDProperty(String key)
    {
        return  key.equals("ForceGameMode") ||
                key.equals("CustomDayCycle") ||
                key.equals("DayLength") ||
                key.equals("NightLength") ||
                key.equals("CloudHeight") ||
                key.equals("SkyColor") ||
                key.equals("CloudColor") ||
                key.equals("FogColor") ||
                key.equals("SkyRenderType") ||
                key.equals("SkyDisableFlags");
    }

    private NBTBase getTagForValue(String key, JsonElement element)
    {
        // These are the keys/values in a vanilla level.dat
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

        if (key.equals("RandomSeed"))
        {
            String seedStr = element.getAsString();
            try
            {
                long seed = Long.parseLong(seedStr);
                if (seed != 0L)
                {
                    return new NBTTagLong(seed);
                }
            }
            catch (NumberFormatException e)
            {
                return new NBTTagLong(seedStr.hashCode());
            }
        }

        // Custom JED properties
        if (key.equals("ForceGameMode"))    { return new NBTTagByte(    element.getAsBoolean() ? (byte) 1 : 0); }
        if (key.equals("CustomDayCycle"))   { return new NBTTagByte(    element.getAsBoolean() ? (byte) 1 : 0); }
        if (key.equals("DayLength"))        { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("NightLength"))      { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("CloudHeight"))      { return new NBTTagInt(     element.getAsInt()      ); }
        if (key.equals("SkyColor"))         { return new NBTTagString(  element.getAsString()   ); }
        if (key.equals("CloudColor"))       { return new NBTTagString(  element.getAsString()   ); }
        if (key.equals("FogColor"))         { return new NBTTagString(  element.getAsString()   ); }
        if (key.equals("SkyRenderType"))    { return new NBTTagByte(    element.getAsByte()     ); }
        if (key.equals("SkyDisableFlags"))  { return new NBTTagByte(    element.getAsByte()     ); }

        if (element.isJsonObject())
        {
            NBTTagCompound tag = new NBTTagCompound();
            JsonObject obj = element.getAsJsonObject();

            if (key.equals("GameRules"))
            {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet())
                {
                    tag.setString(entry.getKey(), entry.getValue().getAsString());
                }
            }
            else if (key.equals("JED"))
            {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet())
                {
                    // Calls this method recursively to get the JED-specific values listed above
                    tag.setTag(entry.getKey(), this.getTagForValue(entry.getKey(), entry.getValue()));
                }
            }

            return tag;
        }

        JustEnoughDimensions.logger.warn("Unrecognized option in worldinfo.values: '{} = {}'", key, element.getAsString());
        return null;
    }

    private DimensionTypeEntry parseDimensionTypeEntry(int dimension, JsonObject dimType)
    {
        int dimTypeId = dimType.has("id") && dimType.get("id").isJsonPrimitive() ? dimType.get("id").getAsInt() : dimension;

        if (dimType.has("vanilladimensiontype") && dimType.get("vanilladimensiontype").isJsonPrimitive())
        {
            String typeName = dimType.get("vanilladimensiontype").getAsString();
            JustEnoughDimensions.logInfo("Using a vanilla DimensionType (or some other existing one) '{}' for dimension {}", typeName, dimension);
            return new DimensionTypeEntry(dimTypeId, typeName);
        }

        String name = (dimType.has("name") && dimType.get("name").isJsonPrimitive()) ?
                dimType.get("name").getAsString() : "DIM" + dimension;
        if (StringUtils.isBlank(name)) { name = "DIM" + dimension; }

        String suffix = (dimType.has("suffix") && dimType.get("suffix").isJsonPrimitive()) ?
                dimType.get("suffix").getAsString() : name.toLowerCase().replace(" ", "_");

        boolean keepLoaded = dimType.has("keeploaded") && dimType.get("keeploaded").isJsonPrimitive() && dimType.get("keeploaded").getAsBoolean();

        Class<? extends WorldProvider> providerClass = WorldProviderSurfaceJED.class;
        String providerName = "";

        if (dimType.has("worldprovider") && dimType.get("worldprovider").isJsonPrimitive())
        {
            providerName = dimType.get("worldprovider").getAsString();

            // Don't allow using the vanilla surface or hell providers for the vanilla end dimension,
            // because that will lead to a crash in the teleport code (null returned from getSpawnCoordinate() for
            // other vanilla providers than the End).
            if (dimension == 1)
            {
                if (providerName.equals("WorldProviderSurface") || providerName.equals("net.minecraft.world.WorldProviderSurface"))
                {
                    providerName = WorldProviderSurfaceJED.class.getSimpleName();
                    JustEnoughDimensions.logger.warn("Changing the provider for DIM1 to {} to prevent a vanilla crash", providerName);
                }
                else if (providerName.equals("WorldProviderHell") || providerName.equals("net.minecraft.world.WorldProviderHell"))
                {
                    providerName = WorldProviderHellJED.class.getSimpleName();
                    JustEnoughDimensions.logger.warn("Changing the provider for DIM1 to {} to prevent a vanilla crash", providerName);
                }
            }

            providerClass = DimensionTypeEntry.getProviderClass(providerName);
        }

        if (providerClass == null)
        {
            providerClass = WorldProviderSurfaceJED.class;

            JustEnoughDimensions.logger.warn("Failed to get a WorldProvider for name '{}', using {} as a fall-back",
                    providerName, DimensionTypeEntry.getNameForWorldProvider(providerClass));
        }

        JustEnoughDimensions.logInfo("Creating a customized DimensionType for dimension {}", dimension);

        return new DimensionTypeEntry(dimension, name, suffix, keepLoaded, providerClass);
    }

    public enum WorldInfoType
    {
        REGULAR ("worldinfo"),
        ONE_TIME ("worldinfo_onetime");

        private final String keyName;

        private WorldInfoType(String keyName)
        {
            this.keyName = keyName;
        }

        public String getKeyName()
        {
            return this.keyName;
        }
    }
}
