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
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.network.MessageSyncDimensions;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.world.WorldProviderEndJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;
import io.netty.buffer.ByteBuf;

public class DimensionConfig
{
    private static DimensionConfig instance;
    private final File configDirConfigs;
    private final File dimensionFileConfigs;
    private final List<DimensionEntry> dimensions = new ArrayList<DimensionEntry>();
    private final Map<Integer, NBTTagCompound> customWorldInfo = new HashMap<Integer, NBTTagCompound>(8);
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

    public List<DimensionEntry> getRegisteredDimensions()
    {
        return ImmutableList.<DimensionEntry>copyOf(this.dimensions);
    }

    public boolean useCustomWorldInfoFor(int dimension)
    {
        return this.customWorldInfo.containsKey(dimension);
    }

    public NBTTagCompound getWorldInfoValues(int dimension, NBTTagCompound tagIn)
    {
        NBTTagCompound dimNBT = this.customWorldInfo.get(dimension);

        if (dimNBT != null)
        {
            for (String key : dimNBT.getKeySet())
            {
                NBTBase tag = dimNBT.getTag(key);

                if (tag != null)
                {
                    tagIn.setTag(key, tag.copy());
                }
            }
        }

        return tagIn;
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
        File file = null;

        if (worldDir != null)
        {
            file = new File(new File(worldDir, Reference.MOD_ID), "dimensions.json");
        }

        if (file == null || file.exists() == false || file.isFile() == false || file.canRead() == false)
        {
            file = this.dimensionFileConfigs;
        }

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
        else if (this.configDirConfigs.isDirectory() == false)
        {
            this.configDirConfigs.mkdirs();
        }
    }

    public void registerDimensions()
    {
        for (DimensionEntry entry : this.dimensions)
        {
            this.registerDimension(entry.getId(), entry);
        }
    }

    public void registerOverriddenDimensions()
    {
        if (Configs.enableReplacingRegisteredDimensions)
        {
            for (DimensionEntry entry : this.dimensions)
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

    private boolean registerDimension(int dimension, DimensionEntry entry)
    {
        if (entry.getUnregister())
        {
            return false;
        }

        if (DimensionManager.isDimensionRegistered(dimension) == false)
        {
            JustEnoughDimensions.logInfo("Registering a dimension with ID {}...", dimension);
            DimensionManager.registerDimension(dimension, entry.registerDimensionType());
            return true;
        }
        else if (Configs.enableReplacingRegisteredDimensions && entry.getOverride())
        {
            if (DimensionManager.getWorld(dimension) == null)
            {
                JustEnoughDimensions.logInfo("Overriding dimension {}...", dimension);
                DimensionManager.unregisterDimension(dimension);
                DimensionManager.registerDimension(dimension, entry.registerDimensionType());
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
        for (DimensionEntry entry : this.dimensions)
        {
            if (entry.getId() == dimension)
            {
                if (entry.getUnregister())
                {
                    CommandJED.throwNumber("register.from.config.unregister.set", Integer.valueOf(dimension));
                }

                if (this.registerDimension(dimension, entry) == false)
                {
                    CommandJED.throwNumber("register.from.config", Integer.valueOf(dimension));
                }
                else
                {
                    return entry.getDescription();
                }
            }
        }

        CommandJED.throwNumber("register.not.in.config", Integer.valueOf(dimension));
        return "";
    }

    public String registerNewDimension(int dimension) throws CommandException
    {
        DimensionEntry entry = this.createDefaultDimensionEntry(dimension);
        return this.registerNewDimension(dimension, entry);
    }

    public String registerNewDimension(int dimension, String name, String suffix, boolean keepLoaded,
            String providerClassName, boolean override) throws CommandException
    {
        Class<? extends WorldProvider> providerClass = this.getProviderClass(providerClassName);

        if (providerClass == null)
        {
            CommandJED.throwCommand("invalid.worldprovider.name", providerClassName);
        }

        DimensionEntry entry = new DimensionEntry(dimension, name, suffix, keepLoaded, providerClass);
        entry.setOverride(override);

        return this.registerNewDimension(dimension, entry);
    }

    private String registerNewDimension(int dimension, DimensionEntry entry) throws CommandException
    {
        boolean success = this.registerDimension(dimension, entry);

        if (success == false)
        {
            CommandJED.throwNumber("dimension.already.registered", Integer.valueOf(dimension));
        }

        this.dimensions.add(entry);
        this.saveConfig();
        PacketHandler.INSTANCE.sendToAll(new MessageSyncDimensions(this.getRegisteredDimensions()));

        return entry.getDescription();
    }

    public void removeDimensionAndSaveConfig(int dimension)
    {
        for (int i = 0; i < this.dimensions.size(); i++)
        {
            DimensionEntry entry = this.dimensions.get(i);

            if (entry.getId() == dimension)
            {
                this.dimensions.remove(i);
                i--;
            }
        }

        this.customWorldInfo.remove(dimension);
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
            FileWriter writer = new FileWriter(this.dimensionFileConfigs);
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
        int count = 0;

        JustEnoughDimensions.logInfo("Reading the dimensions.json config...");
        this.customWorldInfo.clear();
        this.dimensions.clear();

        array = root.get("dimensions").getAsJsonArray();

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
        boolean override = object.has("override") && object.get("override").isJsonPrimitive() &&
                object.get("override").getAsBoolean();
        boolean unregister = object.has("unregister") && object.get("unregister").isJsonPrimitive() &&
                object.get("unregister").getAsBoolean();
        DimensionEntry entry = null;

        if (object.has("dimensiontype") && object.get("dimensiontype").isJsonObject())
        {
            entry = this.parseDimensionType(dimension, object.get("dimensiontype").getAsJsonObject());
        }
        else
        {
            JustEnoughDimensions.logInfo("Using default values for the DimensionType of dimension {}", dimension);
            entry = this.createDefaultDimensionEntry(dimension);
        }

        if (entry != null)
        {
            entry.setOverride(override);
            entry.setUnregister(unregister);

            if (object.has("worldinfo") && object.get("worldinfo").isJsonObject())
            {
                JsonObject obj = object.get("worldinfo").getAsJsonObject();
                entry.setWorldInfoJson(obj);
                this.customWorldInfo.put(dimension, this.parseAndGetCustomWorldInfoValues(dimension, obj));
            }

            this.dimensions.add(entry);
        }
    }

    public void dimbuilderClear()
    {
        this.dimBuilderData = new JsonObject();
    }

    public void dimbuilderDimtype(String name, String suffix, String keepLoaded, String worldProvider)
    {
        JsonObject dimType = this.getOrCreateNestedObject(this.dimBuilderData, "dimensiontype");
        dimType.add("name", new JsonPrimitive(name));
        dimType.add("suffix", new JsonPrimitive(suffix));
        dimType.add("keeploaded", new JsonPrimitive(keepLoaded));
        dimType.add("worldprovider", new JsonPrimitive(worldProvider));
    }

    public void dimbuilderSet(String key, String value)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") || key.equals("unregister"))
        {
            obj.add(key, new JsonPrimitive(value));
        }
        else if (key.equals("name") || key.equals("suffix") || key.equals("keeploaded") || key.equals("worldprovider"))
        {
            this.getOrCreateNestedObject(obj, "dimensiontype").add(key, new JsonPrimitive(value));
        }
        else
        {
            this.getOrCreateNestedObject(obj, "worldinfo").add(key, new JsonPrimitive(value));
        }
    }

    public boolean dimbuilderRemove(String key)
    {
        JsonObject obj = this.dimBuilderData;

        if (key.equals("override") || key.equals("unregister"))
        {
            return obj.remove(key) != null;
        }
        else if (key.equals("name") || key.equals("suffix") || key.equals("keeploaded") || key.equals("worldprovider"))
        {
            obj = this.getNestedObject(obj, "dimensiontype", false);
            return obj != null ? obj.remove(key) != null : false;
        }
        else
        {
            obj = this.getNestedObject(obj, "worldinfo", false);
            return obj != null ? obj.remove(key) != null : false;
        }
    }

    public void dimbuilderList(@Nullable String key, ICommandSender sender) throws CommandException
    {
        if (key != null)
        {
            JsonPrimitive prim = this.getDimbuilderPrimitive(key);

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

    public void dimbuilderSaveAs(int dimension)
    {
        this.removeDimensionAndSaveConfig(dimension);
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
    private JsonPrimitive getDimbuilderPrimitive(String key)
    {
        JsonObject obj = this.dimBuilderData;

        if ((key.equals("override") || key.equals("unregister")) && obj.has(key) && obj.get(key).isJsonPrimitive())
        {
            return obj.get(key).getAsJsonPrimitive();
        }

        if (key.equals("name") || key.equals("suffix") || key.equals("keeploaded") || key.equals("worldprovider"))
        {
            if (obj.has("dimensiontype") && obj.get("dimensiontype").isJsonObject())
            {
                obj = obj.get("dimensiontype").getAsJsonObject();
                return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
            }
        }

        if (obj.has("worldinfo") && obj.get("worldinfo").isJsonObject())
        {
            obj = obj.get("worldinfo").getAsJsonObject();
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsJsonPrimitive() : null;
        }

        return null;
    }

    private NBTTagCompound parseAndGetCustomWorldInfoValues(int dimension, JsonObject object) throws IllegalStateException
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

        return nbt;
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
        return new DimensionEntry(dimension, "DIM" + dimension, "dim_" + dimension, false, WorldProviderSurfaceJED.class);
    }

    private DimensionEntry parseDimensionType(int dimension, JsonObject dimType)
    {
        String name = (dimType.has("name") && dimType.get("name").isJsonPrimitive()) ?
                dimType.get("name").getAsString() : "DIM" + dimension;
        if (StringUtils.isBlank(name)) { name = "DIM" + dimension; }

        String suffix = (dimType.has("suffix") && dimType.get("suffix").isJsonPrimitive()) ?
                dimType.get("suffix").getAsString() : name.toLowerCase().replace(" ", "_");

        boolean keepLoaded = dimType.has("keeploaded") && dimType.get("keeploaded").isJsonPrimitive() && dimType.get("keeploaded").getAsBoolean();

        Class<? extends WorldProvider> providerClass = WorldProviderSurfaceJED.class;

        if (dimType.has("worldprovider") && dimType.get("worldprovider").isJsonPrimitive())
        {
            String providerName = dimType.get("worldprovider").getAsString();

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

            providerClass = this.getProviderClass(providerName);
        }

        if (providerClass == null)
        {
            providerClass = WorldProviderSurfaceJED.class;

            JustEnoughDimensions.logger.warn("Failed to get a WorldProvider for name '{}', using {} as a fall-back",
                    dimType.get("worldprovider").getAsString(), getNameForWorldProvider(providerClass));
        }

        JustEnoughDimensions.logInfo("Creating a customized DimensionType for dimension {}", dimension);

        return new DimensionEntry(dimension, name, suffix, keepLoaded, providerClass);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends WorldProvider> getProviderClass(String providerClassName)
    {
        Class<? extends WorldProvider> providerClass;

        if (providerClassName.equals("WorldProviderSurfaceJED"))
        {
            providerClass = WorldProviderSurfaceJED.class;
        }
        else if (providerClassName.equals("WorldProviderHellJED"))
        {
            providerClass = WorldProviderHellJED.class;
        }
        else if (providerClassName.equals("WorldProviderEndJED"))
        {
            providerClass = WorldProviderEndJED.class;
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
            provName.equals(WorldProviderSurfaceJED.class.getName()) ||
            provName.equals(WorldProviderHellJED.class.getName()) ||
            provName.equals(WorldProviderEndJED.class.getName()))
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
        private boolean override;
        private boolean unregister;
        private JsonObject worldInfojson;

        public DimensionEntry(int id, String name, String suffix, boolean keepLoaded, @Nonnull Class<? extends WorldProvider> providerClass)
        {
            this.id = id;
            this.name = name;
            this.suffix = suffix;
            this.keepLoaded = id == 0 ? true : keepLoaded;
            this.providerClass = providerClass;
        }

        public int getId()
        {
            return this.id;
        }

        public boolean getOverride()
        {
            return this.override;
        }

        public boolean getUnregister()
        {
            return this.unregister;
        }

        public Class<? extends WorldProvider> getProviderClass()
        {
            return this.providerClass;
        }

        public void setOverride(boolean override)
        {
            this.override = override;
        }

        public void setUnregister(boolean unregister)
        {
            // Don't allow unregistering the overworld, or bad thing will happen!
            this.unregister = unregister && this.id != 0;
        }

        public DimensionEntry setWorldInfoJson(JsonObject obj)
        {
            this.worldInfojson = obj;
            return this;
        }

        public DimensionType registerDimensionType()
        {
            JustEnoughDimensions.logInfo("Registering a DimensionType with values: {}", this.getDescription());

            return DimensionType.register(this.name, this.suffix, this.id, this.providerClass, this.keepLoaded);
        }

        public void writeToByteBuf(ByteBuf buf)
        {
            buf.writeInt(this.id);
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.suffix);
            ByteBufUtils.writeUTF8String(buf, this.providerClass.getName());
            buf.writeBoolean(this.unregister);
        }

        public static DimensionEntry fromByteBuf(ByteBuf buf)
        {
            int id = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            String suffix = ByteBufUtils.readUTF8String(buf);
            String providerClassName = ByteBufUtils.readUTF8String(buf);
            boolean unregister = buf.readBoolean();

            try
            {
                @SuppressWarnings("unchecked")
                Class<? extends WorldProvider> providerClass = (Class<? extends WorldProvider>) Class.forName(providerClassName);
                DimensionEntry entry = new DimensionEntry(id, name, suffix, false, providerClass);
                entry.setUnregister(unregister);
                return entry;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("Failed to read dimension info from packet for dimension {}" +
                    " - WorldProvider class {} not found", id, providerClassName);
                return null;
            }
        }

        public JsonObject toJson()
        {
            JsonObject jsonEntry = new JsonObject();
            jsonEntry.addProperty("dim", this.getId());

            if (this.override)
            {
                jsonEntry.addProperty("override", true);
            }

            if (this.unregister)
            {
                jsonEntry.addProperty("unregister", true);
            }

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

        public String getDescription()
        {
            return String.format("{id: %d, name: \"%s\", suffix: \"%s\", keepLoaded: %s, WorldProvider: %s}",
                    this.id, this.name, this.suffix, this.keepLoaded, getNameForWorldProvider(this.providerClass));
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
