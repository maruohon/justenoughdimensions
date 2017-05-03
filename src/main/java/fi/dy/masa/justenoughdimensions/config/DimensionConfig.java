package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.network.MessageSyncDimensions;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDirConfigs;
    private final File dimensionConfigsFile;
    private final Set<Integer> registeredDimensions = new HashSet<Integer>();
    private final Map<Integer, DimensionConfigEntry> dimensions = new HashMap<Integer, DimensionConfigEntry>();
    private final Map<Integer, NBTTagCompound> customWorldInfo = new HashMap<Integer, NBTTagCompound>(8);
    private final Map<Integer, NBTTagCompound> onetimeWorldInfo = new HashMap<Integer, NBTTagCompound>(8);
    private final Map<String, Integer> worldInfoKeys = new HashMap<String, Integer>();
    private final Map<String, Integer> worldInfoKeysJED = new HashMap<String, Integer>();
    private final Map<String, Integer> worldInfoKeysListTypes = new HashMap<String, Integer>();
    private JsonObject dimBuilderData = new JsonObject();

    private DimensionConfig(File configDir)
    {
        instance = this;
        this.configDirConfigs = configDir;
        this.dimensionConfigsFile = new File(configDir, "dimensions.json");
        this.initWorldInfoKeys();
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

    @Nullable
    public DimensionConfigEntry getDimensionConfigFor(int dimension)
    {
        return this.dimensions.get(dimension);
    }

    private void initWorldInfoKeys()
    {
        // These are the keys/values in a vanilla level.dat
        this.worldInfoKeys.put("RandomSeed",           Constants.NBT.TAG_LONG);
        this.worldInfoKeys.put("generatorName",        Constants.NBT.TAG_STRING);
        this.worldInfoKeys.put("generatorVersion",     Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("generatorOptions",     Constants.NBT.TAG_STRING);
        this.worldInfoKeys.put("GameType",             Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("MapFeatures",          Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("SpawnX",               Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("SpawnY",               Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("SpawnZ",               Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("Time",                 Constants.NBT.TAG_LONG);
        this.worldInfoKeys.put("DayTime",              Constants.NBT.TAG_LONG);
        this.worldInfoKeys.put("LastPlayed",           Constants.NBT.TAG_LONG);
        this.worldInfoKeys.put("SizeOnDisk",           Constants.NBT.TAG_LONG);
        this.worldInfoKeys.put("LevelName",            Constants.NBT.TAG_STRING);
        this.worldInfoKeys.put("version",              Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("clearWeatherTime",     Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("rainTime",             Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("raining",              Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("thunderTime",          Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("thundering",           Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("hardcore",             Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("initialized",          Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("allowCommands",        Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("Difficulty",           Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("DifficultyLocked",     Constants.NBT.TAG_BYTE);
        this.worldInfoKeys.put("BorderCenterX",        Constants.NBT.TAG_DOUBLE);
        this.worldInfoKeys.put("BorderCenterZ",        Constants.NBT.TAG_DOUBLE);
        this.worldInfoKeys.put("BorderSize",           Constants.NBT.TAG_DOUBLE);
        this.worldInfoKeys.put("BorderSizeLerpTime",   Constants.NBT.TAG_LONG);
        this.worldInfoKeys.put("BorderSizeLerpTarget", Constants.NBT.TAG_DOUBLE);
        this.worldInfoKeys.put("BorderSafeZone",       Constants.NBT.TAG_DOUBLE);
        this.worldInfoKeys.put("BorderDamagePerBlock", Constants.NBT.TAG_DOUBLE);
        this.worldInfoKeys.put("BorderWarningBlocks",  Constants.NBT.TAG_INT);
        this.worldInfoKeys.put("BorderWarningTime",    Constants.NBT.TAG_INT);

        // Custom JED properties
        this.worldInfoKeysJED.put("ForceGameMode",    Constants.NBT.TAG_BYTE);
        this.worldInfoKeysJED.put("CustomDayCycle",   Constants.NBT.TAG_BYTE);
        this.worldInfoKeysJED.put("DayLength",        Constants.NBT.TAG_INT);
        this.worldInfoKeysJED.put("NightLength",      Constants.NBT.TAG_INT);
        this.worldInfoKeysJED.put("CloudHeight",      Constants.NBT.TAG_INT);
        this.worldInfoKeysJED.put("SkyColor",         Constants.NBT.TAG_STRING);
        this.worldInfoKeysJED.put("CloudColor",       Constants.NBT.TAG_STRING);
        this.worldInfoKeysJED.put("FogColor",         Constants.NBT.TAG_STRING);
        this.worldInfoKeysJED.put("SkyRenderType",    Constants.NBT.TAG_BYTE);
        this.worldInfoKeysJED.put("SkyDisableFlags",  Constants.NBT.TAG_BYTE);
        this.worldInfoKeysJED.put("LightBrightness",  Constants.NBT.TAG_LIST);
        this.worldInfoKeysJED.put("CanRespawnHere",   Constants.NBT.TAG_BYTE);
        this.worldInfoKeysJED.put("RespawnDimension", Constants.NBT.TAG_INT);

        this.worldInfoKeysListTypes.put("LightBrightness", Constants.NBT.TAG_FLOAT);
    }

    public boolean useCustomWorldInfoFor(int dimension)
    {
        return this.customWorldInfo.containsKey(dimension) || this.onetimeWorldInfo.containsKey(dimension);
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
            file = this.dimensionConfigsFile;
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

    public void registerNonOverrideDimensions()
    {
        for (DimensionConfigEntry entry : this.dimensions.values())
        {
            if (Configs.enableReplacingRegisteredDimensions == false || entry.getOverride() == false)
            {
                this.registerDimension(entry.getId(), entry);
            }
        }

        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));
    }

    public void registerOverriddenDimensions()
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
                else if (Configs.enableReplacingRegisteredDimensions && entry.getOverride())
                {
                    this.registerDimension(entry.getId(), entry);
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
            this.registeredDimensions.add(dimension);
            return true;
        }
        else if (Configs.enableReplacingRegisteredDimensions && entry.getOverride())
        {
            if (DimensionManager.getWorld(dimension) == null)
            {
                JustEnoughDimensions.logInfo("Overriding dimension {}...", dimension);
                DimensionManager.unregisterDimension(dimension);
                DimensionManager.registerDimension(dimension, entry.getDimensionTypeEntry().registerDimensionType());
                this.registeredDimensions.add(dimension);
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
        Set<Integer> toRemove = new HashSet<Integer>();

        for (int dimension : this.registeredDimensions)
        {
            if (dimension != 0 && DimensionManager.isDimensionRegistered(dimension) && DimensionManager.getWorld(dimension) == null)
            {
                JustEnoughDimensions.logInfo("Unregistering dimension {}...", dimension);
                DimensionManager.unregisterDimension(dimension);
                toRemove.add(dimension);
            }
        }

        this.registeredDimensions.removeAll(toRemove);
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
        boolean disableTeleporting = object.has("disableteleporting") && object.get("disableteleporting").isJsonPrimitive() &&
                object.get("disableteleporting").getAsBoolean();
        String biome = object.has("biome") && object.get("biome").isJsonPrimitive() ?
                object.get("biome").getAsString() : null;

        configEntry.setOverride(override);
        configEntry.setUnregister(unregister);
        configEntry.setDisableTeleporting(disableTeleporting);
        configEntry.setBiome(biome);

        if (object.has("colors") && object.get("colors").isJsonObject())
        {
            configEntry.setColorJson(object.getAsJsonObject("colors"));
        }

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
        DimensionTypeEntry dte = new DimensionTypeEntry(dimension, "DIM" + dimension, "dim_" + dimension, false, WorldProviderSurfaceJED.class);
        JustEnoughDimensions.logInfo("Created a default DimensionTypeEntry for dimension {}: {}", dimension, dte.getDescription());
        return dte;
    }

    public void dimbuilderClear()
    {
        this.dimBuilderData = new JsonObject();
    }

    public void dimbuilderDimtype(int id, String name, String suffix, String keepLoaded, String worldProvider)
    {
        JsonObject dimType = JEDJsonUtils.getOrCreateNestedObject(this.dimBuilderData, "dimensiontype");
        dimType.add("id", new JsonPrimitive(id));
        dimType.add("name", new JsonPrimitive(name));
        dimType.add("suffix", new JsonPrimitive(suffix));
        dimType.add("keeploaded", new JsonPrimitive(keepLoaded));
        dimType.add("worldprovider", new JsonPrimitive(worldProvider));
    }

    public void dimbuilderSet(String key, String value, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") || key.equals("unregister") || key.equals("disableteleporting") || key.equals("biome"))
        {
            obj.add(key, new JsonPrimitive(value));
        }
        else if (key.equals("id") || key.equals("name") || key.equals("suffix") || key.equals("keeploaded") ||
                 key.equals("worldprovider") || key.equals("vanilladimensiontype"))
        {
            JEDJsonUtils.getOrCreateNestedObject(obj, "dimensiontype").add(key, new JsonPrimitive(value));
        }
        else if (key.equals("worldinfo") || key.equals("worldinfo_onetime"))
        {
            JEDJsonUtils.getOrCreateNestedObject(obj, type.getKeyName());
        }
        else
        {
            obj = JEDJsonUtils.getOrCreateNestedObject(obj, type.getKeyName());

            if (this.isJEDProperty(key))
            {
                obj = JEDJsonUtils.getOrCreateNestedObject(obj, "JED");
            }
            // Not a JED property and not a (direct) vanilla level.dat key, so let's assume it's a GameRule then
            else if (this.worldInfoKeys.get(key) == null)
            {
                obj = JEDJsonUtils.getOrCreateNestedObject(obj, "GameRules");
            }

            obj.add(key, new JsonPrimitive(value));
        }
    }

    public boolean dimbuilderRemove(String key, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") || key.equals("unregister") || key.equals("disableteleporting") || key.equals("biome") || key.equals("colors"))
        {
            return obj.remove(key) != null;
        }
        else if (key.equals("id") || key.equals("name") || key.equals("suffix") || key.equals("keeploaded") ||
                 key.equals("worldprovider") || key.equals("vanilladimensiontype"))
        {
            obj = JEDJsonUtils.getNestedObject(obj, "dimensiontype", false);
            return obj != null ? obj.remove(key) != null : false;
        }
        else if (key.equals("worldinfo") || key.equals("worldinfo_onetime"))
        {
            obj.remove(type.getKeyName());
            return true;
        }
        else
        {
            obj = JEDJsonUtils.getNestedObject(obj, type.getKeyName(), false);

            if (obj != null)
            {
                // vanilla level.dat properties
                if (obj.has(key))
                {
                    return obj.remove(key) != null;
                }
                // JED properties or GameRules
                else
                {
                    String tagName = this.isJEDProperty(key) ? "JED" : "GameRules";
                    obj = JEDJsonUtils.getNestedObject(obj, tagName, false);
                    return obj != null ? obj.remove(key) != null : false;
                }
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

    @Nullable
    private JsonPrimitive getDimbuilderPrimitive(String key, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if ((key.equals("override") || key.equals("unregister") || key.equals("disableteleporting") || key.equals("biome")) &&
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

            // The requested key is a value directly inside the worldinfo object
            if (obj.has(key) && obj.get(key).isJsonPrimitive())
            {
                return obj.get(key).getAsJsonPrimitive();
            }
            else
            {
                String tagName = this.isJEDProperty(key) ? "JED" : "GameRules";

                if (obj.has(tagName) && obj.get(tagName).isJsonObject())
                {
                    obj = obj.get(tagName).getAsJsonObject();
                    // The requested key exists inside the object
                    return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
                }
            }
        }

        return null;
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
        return this.worldInfoKeysJED.get(key) != null;
    }

    @Nullable
    private NBTBase getTagForValue(String key, JsonElement element)
    {
        if (key.equals("RandomSeed"))
        {
            String seedStr = element.getAsString();
            try
            {
                long seed = Long.parseLong(seedStr);
                return new NBTTagLong(seed);
            }
            catch (NumberFormatException e)
            {
                return new NBTTagLong(seedStr.hashCode());
            }
        }
        else if (element.isJsonObject())
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

        Integer type = this.worldInfoKeys.get(key);

        // Keys from the vanilla level.dat
        if (type != null)
        {
            return this.getTagForType(key, type, element);
        }

        // Custom JED properties
        type = this.worldInfoKeysJED.get(key);

        if (type != null)
        {
            return this.getTagForType(key, type, element);
        }

        JustEnoughDimensions.logger.warn("Unrecognized option in worldinfo.values: '{} = {}'", key, element.getAsString());
        return null;
    }

    @Nullable
    private NBTBase getTagForType(String key, int type, JsonElement element)
    {
        switch (type)
        {
            case Constants.NBT.TAG_BYTE:
                try
                {
                    String str = element.getAsString();
                    if (str != null && (str.equals("true") || str.equals("false")))
                    {
                        return new NBTTagByte(element.getAsBoolean() ? (byte) 1 : 0);
                    }
                }
                catch (Exception e) {}
                return new NBTTagByte(element.getAsByte());

            case Constants.NBT.TAG_SHORT:
                return new NBTTagShort(element.getAsShort());

            case Constants.NBT.TAG_INT:
                return new NBTTagInt(element.getAsInt());

            case Constants.NBT.TAG_LONG:
                return new NBTTagLong(element.getAsLong());

            case Constants.NBT.TAG_FLOAT:
                return new NBTTagFloat(element.getAsFloat());

            case Constants.NBT.TAG_DOUBLE:
                return new NBTTagDouble(element.getAsDouble());

            case Constants.NBT.TAG_STRING:
                return new NBTTagString(element.getAsString());

            case Constants.NBT.TAG_LIST:
                if (element.isJsonArray() && this.worldInfoKeysListTypes.containsKey(key))
                {
                    JsonArray arr = element.getAsJsonArray();
                    NBTTagList list = new NBTTagList();
                    int listType = this.worldInfoKeysListTypes.get(key);

                    for (JsonElement el : arr)
                    {
                        list.appendTag(this.getTagForType("", listType, el));
                    }

                    return list;
                }
        }

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
                dimType.get("suffix").getAsString() : "_dim" + dimension;

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

    public enum ColorType
    {
        FOLIAGE ("FoliageColors"),
        GRASS   ("GrassColors"),
        WATER   ("WaterColors");

        private final String keyName;

        private ColorType(String keyName)
        {
            this.keyName = keyName;
        }

        public String getKeyName()
        {
            return this.keyName;
        }
    }
}
