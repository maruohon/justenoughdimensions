package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.network.MessageSyncDimensions;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.util.ConfigFileUtils;
import fi.dy.masa.justenoughdimensions.util.ConfigFileUtils.ConfigComparator;
import fi.dy.masa.justenoughdimensions.util.ConfigFileUtils.FileAction;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDirJED;
    private final File dimensionConfigFileGlobal;
    private final Set<Integer> registeredDimensions = new HashSet<Integer>();
    private final Map<Integer, DimensionConfigEntry> dimensions = new HashMap<Integer, DimensionConfigEntry>();
    private final Map<Integer, NBTTagCompound> customWorldInfo = new HashMap<Integer, NBTTagCompound>();
    private final Map<Integer, NBTTagCompound> onetimeWorldInfo = new HashMap<Integer, NBTTagCompound>();
    private final Map<String, Integer> worldInfoKeys = new HashMap<String, Integer>();
    private final Map<String, Integer> jedKeys = new HashMap<String, Integer>();
    private final Map<String, Integer> jedKeysListTypes = new HashMap<String, Integer>();
    private JsonObject dimBuilderData = new JsonObject();
    private File currentDimensionConfigFile;
    private String currentConfigId = "__default";
    private int currentConfigVersion;

    private DimensionConfig(File configDirCommon)
    {
        instance = this;

        this.configDirJED = new File(configDirCommon, Reference.MOD_ID);
        this.dimensionConfigFileGlobal = new File(this.configDirJED, "dimensions.json");

        this.initWorldInfoKeys();
    }

    public static DimensionConfig init(File configDirCommon)
    {
        return new DimensionConfig(configDirCommon);
    }

    public static DimensionConfig instance()
    {
        return instance;
    }

    public Collection<DimensionConfigEntry> getRegisteredDimensions()
    {
        List<DimensionConfigEntry> list = new ArrayList<>();

        for (int dim : this.registeredDimensions)
        {
            list.add(this.dimensions.get(dim));
        }

        return list;
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
        this.jedKeys.put("CanRespawnHere",      Constants.NBT.TAG_BYTE);
        this.jedKeys.put("CloudColor",          Constants.NBT.TAG_STRING);
        this.jedKeys.put("CloudHeight",         Constants.NBT.TAG_INT);
        this.jedKeys.put("Colors",              Constants.NBT.TAG_LIST);
        this.jedKeys.put("CustomDayCycle",      Constants.NBT.TAG_BYTE);
        this.jedKeys.put("DayLength",           Constants.NBT.TAG_INT);
        this.jedKeys.put("FogColor",            Constants.NBT.TAG_STRING);
        this.jedKeys.put("ForceGameMode",       Constants.NBT.TAG_BYTE);
        this.jedKeys.put("LightBrightness",     Constants.NBT.TAG_LIST);
        this.jedKeys.put("NightLength",         Constants.NBT.TAG_INT);
        this.jedKeys.put("RespawnDimension",    Constants.NBT.TAG_INT);
        this.jedKeys.put("SkyColor",            Constants.NBT.TAG_STRING);
        this.jedKeys.put("SkyDisableFlags",     Constants.NBT.TAG_BYTE);
        this.jedKeys.put("SkyRenderer",         Constants.NBT.TAG_STRING);
        this.jedKeys.put("SkyRenderType",       Constants.NBT.TAG_BYTE);

        this.jedKeysListTypes.put("Colors",             Constants.NBT.TAG_COMPOUND);
        this.jedKeysListTypes.put("LightBrightness",    Constants.NBT.TAG_FLOAT);
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

    /**
     * Only call this method after the overworld has been loaded
     */
    public void readDimensionConfig()
    {
        this.readDimensionConfig(DimensionManager.getCurrentSaveRootDirectory());
    }

    public void readDimensionConfig(File worldDir)
    {
        if (worldDir != null)
        {
            File configDir = new File(new File(worldDir, "data"), Reference.MOD_ID);
            File configFile = new File(configDir, "dimensions.json");

            if (Configs.copyDimensionConfigToWorld)
            {
                ConfigFileUtils.createDirIfNotExists(configDir);
                ConfigFileUtils.tryCopyOrMoveConfigIfMissingOrOlder(configFile, this.dimensionConfigFileGlobal,
                        FileAction.COPY, new ConfigComparatorDimensionConfig());
            }

            if (configFile.exists() && configFile.isFile() && configFile.canRead())
            {
                this.currentDimensionConfigFile = configFile;
                this.readDimensionConfigFromFile(this.currentDimensionConfigFile);
                return;
            }
        }

        this.currentDimensionConfigFile = this.dimensionConfigFileGlobal;
        this.readDimensionConfigFromFile(this.currentDimensionConfigFile);
    }

    private void readDimensionConfigFromFile(File configFile)
    {
        this.customWorldInfo.clear();
        this.onetimeWorldInfo.clear();
        this.dimensions.clear();
        this.currentConfigId = "__default";
        this.currentConfigVersion = 0;
        JEDWorldProperties.clearWorldProperties();

        if (configFile != null)
        {
            String fileName = configFile.getAbsolutePath();
            JsonElement rootElement = JEDJsonUtils.parseJsonFile(configFile);

            if (rootElement != null)
            {
                JustEnoughDimensions.logInfo("Reading the dimension config from file '{}'", fileName);
                this.parseDimensionConfig(rootElement);
            }
            else
            {
                JustEnoughDimensions.logger.warn("The dimension config in file '{}' was empty or invalid", fileName);
            }
        }
        else
        {
            JustEnoughDimensions.logInfo("No 'dimensions.json' file found; neither global nor per-world");
        }

        this.restoreMissingVanillaDimensions();
    }

    private void restoreMissingVanillaDimensions()
    {
        for (int dim = -1; dim < 2; dim++)
        {
            // Not currently in the JED configs, and not registered,
            // assume they have been unregistered by JED in a per-world config for another world (in single player)
            if (this.dimensions.containsKey(dim) == false &&
                DimensionManager.isDimensionRegistered(dim) == false)
            {
                JustEnoughDimensions.logInfo("Dimension {} was not registered, and not found in the current JED config. " +
                                             "Registering the normal vanilla dimension for it.", dim);
                DimensionManager.registerDimension(dim, this.getVanillaDimensionType(dim));
            }
        }
    }

    private DimensionType getVanillaDimensionType(int dim)
    {
        switch (dim)
        {
            case  1: return DimensionType.THE_END;
            case -1: return DimensionType.NETHER;
            default: return DimensionType.OVERWORLD;
        }
    }

    public void registerDimensions()
    {
        for (DimensionConfigEntry entry : this.dimensions.values())
        {
            this.registerDimension(entry.getDimension(), entry);
        }

        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));
    }

    public void registerNonOverrideDimensions()
    {
        for (DimensionConfigEntry entry : this.dimensions.values())
        {
            if (DimensionManager.isDimensionRegistered(entry.getDimension()) == false)
            {
                this.registerDimension(entry.getDimension(), entry);
            }
        }

        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));
    }

    public void doDimensionOverridesAndUnregistering()
    {
        for (DimensionConfigEntry entry : this.dimensions.values())
        {
            if (DimensionManager.isDimensionRegistered(entry.getDimension()))
            {
                if (Configs.enableUnregisteringDimensions && entry.getUnregister())
                {
                    JustEnoughDimensions.logInfo("Unregistering dimension {}...", entry.getDimension());
                    DimensionManager.unregisterDimension(entry.getDimension());
                }
                else if (Configs.enableReplacingRegisteredDimensions && entry.getOverride())
                {
                    this.registerDimension(entry.getDimension(), entry);
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
            JustEnoughDimensions.logInfo("Registering a dimension with ID {}", dimension);
            DimensionManager.registerDimension(dimension, entry.getDimensionTypeEntry().getOrRegisterDimensionType(dimension));
            this.registeredDimensions.add(dimension);
            return true;
        }
        else if (Configs.enableReplacingRegisteredDimensions && entry.getOverride())
        {
            if (DimensionManager.getWorld(dimension) == null)
            {
                JustEnoughDimensions.logInfo("Overriding dimension {}", dimension);
                DimensionManager.unregisterDimension(dimension);
                DimensionManager.registerDimension(dimension, entry.getDimensionTypeEntry().getOrRegisterDimensionType(dimension));
                this.registeredDimensions.add(dimension);
                return true;
            }
            else
            {
                JustEnoughDimensions.logger.warn("Dimension {} is already registered and currently loaded, can't override it", dimension);
            }
        }
        else
        {
            JustEnoughDimensions.logger.warn("Dimension {} is already registered, skipping it", dimension);
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
        Iterator<Integer> iter = this.registeredDimensions.iterator();

        while (iter.hasNext())
        {
            int dimension = iter.next();

            if (dimension != 0 && DimensionManager.isDimensionRegistered(dimension) && DimensionManager.getWorld(dimension) == null)
            {
                JustEnoughDimensions.logInfo("Unregistering dimension {}", dimension);
                DimensionManager.unregisterDimension(dimension);
                iter.remove();
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
        JEDWorldProperties.removePropertiesFrom(dimension);
    }

    private void saveConfig()
    {
        List<DimensionConfigEntry> dims = new ArrayList<>(this.dimensions.values());
        Collections.sort(dims);

        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();

        for (DimensionConfigEntry dimEntry : dims)
        {
            array.add(dimEntry.toJson());
        }

        JsonObject objVersion = new JsonObject();
        objVersion.add("id", new JsonPrimitive(this.currentConfigId));
        objVersion.add("version", new JsonPrimitive(this.currentConfigVersion));

        root.add("config_version", objVersion);
        root.add("dimensions", array);

        try
        {
            FileWriter writer = new FileWriter(this.currentDimensionConfigFile);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writer.write(gson.toJson(root));
            writer.close();
        }
        catch (IOException e)
        {
            JustEnoughDimensions.logger.warn("Failed to write dimension config to file '{}'",
                    this.currentDimensionConfigFile.getAbsolutePath(), e);
        }
    }

    @Nullable
    private static Pair<String, Integer> getConfigVersion(JsonElement rootElement)
    {
        if (rootElement != null && rootElement.isJsonObject())
        {
            JsonObject root = rootElement.getAsJsonObject();
            JsonObject objVersion = JEDJsonUtils.getNestedObject(root, "config_version", false);

            if (objVersion != null)
            {
                String id = JEDJsonUtils.getStringOrDefault(objVersion, "id", "__default", true);
                int ver = JEDJsonUtils.getIntegerOrDefault(objVersion, "version", 0);

                return Pair.of(id, ver);
            }
        }

        return null;
    }

    private void parseDimensionConfig(JsonElement rootElement) throws IllegalStateException
    {
        if (rootElement == null || rootElement.isJsonObject() == false)
        {
            JustEnoughDimensions.logger.warn("The dimension config is missing the root object!");
            return;
        }

        JsonObject root = rootElement.getAsJsonObject();
        Pair<String, Integer> version = getConfigVersion(rootElement);

        if (version != null)
        {
            this.currentConfigId = version.getLeft();
            this.currentConfigVersion = version.getRight();
        }

        if (root.has("dimensions") && root.get("dimensions").isJsonArray())
        {
            JsonArray array = rootElement.getAsJsonObject().get("dimensions").getAsJsonArray();
            JsonObject object;
            int count = 0;

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
    }

    private void parseDimensionConfigEntry(int dimension, final JsonObject object)
    {
        DimensionConfigEntry configEntry = DimensionConfigEntry.fromJson(dimension, object);

        if (configEntry.getWorldInfoJson() != null)
        {
            this.customWorldInfo.put(dimension, this.parseAndGetCustomWorldInfoValues(dimension, configEntry.getWorldInfoJson()));
        }

        if (configEntry.getOneTimeWorldInfoJson() != null)
        {
            this.onetimeWorldInfo.put(dimension, this.parseAndGetCustomWorldInfoValues(dimension, configEntry.getOneTimeWorldInfoJson()));
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

        if (key.equals("override") || key.equals("unregister") || key.equals("biome") ||
            key.equals("disable_teleporting_from") || key.equals("disable_teleporting_to"))
        {
            obj.add(key, new JsonPrimitive(value));
        }
        else if (key.equals("id") || key.equals("name") || key.equals("suffix") || key.equals("keeploaded") ||
                 key.equals("worldprovider") || key.equals("existing_dimensiontype"))
        {
            JEDJsonUtils.getOrCreateNestedObject(obj, "dimensiontype").add(key, new JsonPrimitive(value));
        }
        else if (key.equals("worldinfo") || key.equals("worldinfo_onetime"))
        {
            JEDJsonUtils.getOrCreateNestedObject(obj, type.getKeyName());
        }
        else
        {
            if (this.isJEDProperty(key))
            {
                obj = JEDJsonUtils.getOrCreateNestedObject(obj, "jed");
            }
            else
            {
                obj = JEDJsonUtils.getOrCreateNestedObject(obj, type.getKeyName());

                // Not a JED property and not a (direct) vanilla level.dat key, so let's assume it's a GameRule then
                if (this.worldInfoKeys.get(key) == null)
                {
                    obj = JEDJsonUtils.getOrCreateNestedObject(obj, "GameRules");
                }
            }

            obj.add(key, new JsonPrimitive(value));
        }
    }

    public boolean dimbuilderRemove(String key, WorldInfoType type)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") ||
            key.equals("unregister") ||
            key.equals("biome") ||
            key.equals("disable_teleporting_from") ||
            key.equals("disable_teleporting_to") ||
            key.equals("jed") ||
            key.equals("worldinfo") ||
            key.equals("worldinfo_onetime"))
        {
            return obj.remove(key) != null;
        }
        else if (key.equals("id") ||
                 key.equals("name") ||
                 key.equals("suffix") ||
                 key.equals("keeploaded") ||
                 key.equals("worldprovider") ||
                 key.equals("existing_dimensiontype"))
        {
            obj = JEDJsonUtils.getNestedObject(obj, "dimensiontype", false);
            return obj != null ? obj.remove(key) != null : false;
        }
        else
        {
            if (this.isJEDProperty(key))
            {
                obj = JEDJsonUtils.getNestedObject(obj, "jed", false);
                boolean success = obj != null ? obj.remove(key) != null : false;

                if (obj != null && obj.size() == 0)
                {
                    this.dimBuilderData.remove("jed");
                }

                return success;
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
                    // GameRules
                    else
                    {
                        obj = JEDJsonUtils.getNestedObject(obj, "GameRules", false);
                        return obj != null && obj.remove(key) != null;
                    }
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

        if ((key.equals("override") ||
             key.equals("unregister") ||
             key.equals("biome") ||
             key.equals("disable_teleporting_from") ||
             key.equals("disable_teleporting_to")) &&
                obj.has(key) && obj.get(key).isJsonPrimitive())
        {
            return obj.get(key).getAsJsonPrimitive();
        }

        if (key.equals("id") ||
            key.equals("name") ||
            key.equals("suffix") ||
            key.equals("keeploaded") ||
            key.equals("worldprovider") ||
            key.equals("existing_dimensiontype"))
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

        if (this.isJEDProperty(key))
        {
            obj = JEDJsonUtils.getNestedObject(obj, "jed", false);
            return obj != null && obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
        }
        else if (obj.has(type.getKeyName()) && obj.get(type.getKeyName()).isJsonObject())
        {
            obj = obj.get(type.getKeyName()).getAsJsonObject();

            // The requested key is a value directly inside the worldinfo object
            if (obj.has(key) && obj.get(key).isJsonPrimitive())
            {
                return obj.get(key).getAsJsonPrimitive();
            }
            else if (obj.has("GameRules") && obj.get("GameRules").isJsonObject())
            {
                obj = obj.get("GameRules").getAsJsonObject();
                return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
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
            NBTBase tag = this.getTagForWorldInfoValue(key, element);

            if (tag != null)
            {
                nbt.setTag(key, tag);
            }
        }

        return nbt;
    }

    private boolean isJEDProperty(String key)
    {
        return this.jedKeys.get(key) != null;
    }

    @Nullable
    private NBTBase getTagForWorldInfoValue(String key, JsonElement element)
    {
        if (key.equals("RandomSeed") && element.isJsonPrimitive())
        {
            try
            {
                long seed = Long.parseLong(element.getAsString());
                return new NBTTagLong(seed);
            }
            catch (NumberFormatException e)
            {
                String seedStr = element.getAsString();
                return new NBTTagLong(seedStr.isEmpty() ? JustEnoughDimensions.RAND.nextLong() : seedStr.hashCode());
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
                    JsonElement el = entry.getValue();

                    if (el.isJsonPrimitive())
                    {
                        tag.setString(entry.getKey(), el.getAsString());
                    }
                    else
                    {
                        JustEnoughDimensions.logger.warn("Invalid GameRule value: '{} = {}'", entry.getKey(), el);
                    }
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

        JustEnoughDimensions.logger.warn("Unrecognized option in worldinfo.values: '{} = {}'", key, element);
        return null;
    }

    @Nullable
    private NBTBase getTagForType(String key, int type, JsonElement element)
    {
        try
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
                    if (element.isJsonArray() && this.jedKeysListTypes.containsKey(key))
                    {
                        JsonArray arr = element.getAsJsonArray();
                        NBTTagList list = new NBTTagList();
                        int listType = this.jedKeysListTypes.get(key);

                        for (JsonElement el : arr)
                        {
                            NBTBase tag = this.getTagForType("", listType, el);

                            if (tag != null)
                            {
                                list.appendTag(tag);
                            }
                        }

                        return list;
                    }
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Invalid/unexpected value: '{}' for key '{}'", key, element);
        }

        return null;
    }

    private static class ConfigComparatorDimensionConfig extends ConfigComparator
    {
        @Override
        public boolean shouldReplace(File fileToReplace, File replacementFile)
        {
            if (fileToReplace.exists() == false)
            {
                return true;
            }

            if ((fileToReplace.exists() && fileToReplace.isFile() && fileToReplace.canRead() &&
                replacementFile.exists() && replacementFile.isFile() && replacementFile.canRead()))
            {
                JsonElement rootElementOld = JEDJsonUtils.parseJsonFile(fileToReplace);
                JsonElement rootElementNew = JEDJsonUtils.parseJsonFile(replacementFile);
                Pair<String, Integer> versionOld = rootElementOld != null ? getConfigVersion(rootElementOld) : null;
                Pair<String, Integer> versionNew = rootElementNew != null ? getConfigVersion(rootElementNew) : null;

                if (versionOld != null && versionNew != null)
                {
                    return versionNew.getLeft().equals(versionOld.getLeft()) &&
                           versionNew.getRight() > versionOld.getRight();
                }
            }

            return false;
        }
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
