package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import javax.annotation.Nullable;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.util.ConfigFileUtils;
import fi.dy.masa.justenoughdimensions.util.ConfigFileUtils.ConfigComparator;
import fi.dy.masa.justenoughdimensions.util.ConfigFileUtils.FileAction;

public class Configs
{
    private static File configFileGlobal;
    public static String configurationFileName;
    public static Configuration config;
    
    public static final String CATEGORY_CLIENT = "Client";
    public static final String CATEGORY_CONFIG_HANDLING = "ConfigHandling";
    public static final String CATEGORY_GENERIC = "Generic";
    public static final String CATEGORY_VERSION = "Version";

    public static boolean enableColorOverrides;
    public static boolean enableCommandRedirecting;
    public static boolean enableForcedGamemodes;
    public static boolean enableInitialSpawnDimensionOverride;
    public static boolean enableLoggingInfo;
    public static boolean enableOverrideBiomeProvider;
    public static boolean enableReplacingRegisteredDimensions;
    public static boolean enableSeparateWorldBorders;
    public static boolean enableSeparateWorldInfo;
    public static boolean enableUnregisteringDimensions;

    public static boolean copyDimensionConfigToWorld;
    public static boolean copyMainConfigToWorld;
    public static boolean usePerWorldDimensionConfig;
    public static boolean usePerWorldMainConfig;

    public static int initialSpawnDimensionId;

    @SubscribeEvent
    public void onConfigChangedEvent(OnConfigChangedEvent event)
    {
        if (Reference.MOD_ID.equals(event.getModID()) == true)
        {
            loadConfigs(config);
        }
    }

    public static void loadConfigsFromMainConfigFile(File configDirCommon)
    {
        File configDir = new File(configDirCommon, Reference.MOD_ID);
        File configFile = new File(configDir, Reference.MOD_ID + ".cfg");
        configFileGlobal = configFile;

        ConfigFileUtils.createDirIfNotExists(configDir);

        // Automatic config moving from the old location to the new location
        File oldFile = new File(configDirCommon, "justenoughdimensions.cfg");
        ConfigFileUtils.tryCopyOrMoveConfigIfMissingOrOlder(configFile, oldFile, FileAction.MOVE, new ConfigComparatorMainConfig());

        loadConfigsFromFile(configFile);
    }

    public static void loadConfigsFromPerWorldConfigIfEnabled(@Nullable File worldDir)
    {
        if (worldDir != null)
        {
            File configDir = new File(new File(worldDir, "data"), Reference.MOD_ID);
            File configFile = new File(configDir, Reference.MOD_ID + ".cfg");

            if (copyMainConfigToWorld)
            {
                ConfigFileUtils.createDirIfNotExists(configDir);
                ConfigFileUtils.tryCopyOrMoveConfigIfMissingOrOlder(configFile, configFileGlobal, FileAction.COPY, new ConfigComparatorMainConfig());
            }

            if (usePerWorldMainConfig && configFile.exists() && configFile.isFile() && configFile.canRead())
            {
                loadConfigsFromFile(configFile);
                return;
            }
        }

        loadConfigsFromFile(configFileGlobal);
    }

    public static void loadConfigsFromGlobalConfigFile()
    {
        loadConfigsFromFile(configFileGlobal);
    }

    private static void loadConfigsFromFile(File configFile)
    {
        configurationFileName = configFile.toString();
        config = new Configuration(configFile, null, true);

        reloadConfigsFromFile();
    }

    public static class ConfigComparatorMainConfig extends ConfigComparator
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
                Configuration confOld = new Configuration(fileToReplace, null, true);
                Configuration confNew = new Configuration(replacementFile, null, true);

                if (confOld.hasKey(CATEGORY_VERSION, "configId") && confNew.hasKey(CATEGORY_VERSION, "configId"))
                {
                    String idOld = confOld.get(CATEGORY_VERSION, "configId", "").getString();
                    String idNew = confNew.get(CATEGORY_VERSION, "configId", "").getString();
                    int versionOld = confOld.get(CATEGORY_VERSION, "version", 0).getInt();
                    int versionNew = confNew.get(CATEGORY_VERSION, "version", 0).getInt();

                    return idOld.equals(idNew) && versionNew > versionOld;
                }
            }

            return false;
        }
    }

    public static boolean reloadConfigsFromFile()
    {
        if (config != null)
        {
            JustEnoughDimensions.logger.info("Reloading the main configs from file '{}'", config.getConfigFile().getAbsolutePath());
            config.load();
            loadConfigs(config);

            return true;
        }

        return false;
    }

    public static void loadConfigs(Configuration conf)
    {
        Property prop;

        prop = conf.get(CATEGORY_CONFIG_HANDLING, "copyDimensionConfigToWorld", true).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, then the dimension config ('dimensions.json')\n" +
                        "will be copied to each save and used from there, to avoid changes in the\n" +
                        "global/pack config from breaking the saves due to possibly differing settings between worlds.\n" +
                        "NOTE: This option ONLY affects whether or not the config is automatically _copied to_ each world.");
        copyDimensionConfigToWorld = prop.getBoolean();

        prop = conf.get(CATEGORY_CONFIG_HANDLING, "copyMainConfigToWorld", true).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, then the \"main config\" ('justenoughdimensions.cfg')\n" +
                        "will be copied to each save and used from there, to avoid changes in the\n" +
                        "global/pack config from breaking the saves due to possibly differing settings between worlds.\n" +
                        "NOTE: This option ONLY affects whether or not the config is automatically _copied to_ each world.");
        copyMainConfigToWorld = prop.getBoolean();

        prop = conf.get(CATEGORY_CONFIG_HANDLING, "usePerWorldDimensionConfig", true).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, then the dimension config ('dimensions.json')\n" +
                        "will read from within each world/save, if it exists there.\n" +
                        "Also see the option 'copyDimensionConfigToWorld' to enable automatically copying it there.");
        usePerWorldDimensionConfig = prop.getBoolean();

        prop = conf.get(CATEGORY_CONFIG_HANDLING, "usePerWorldMainConfig", true).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, then the \"main config\" ('justenoughdimensions.cfg')\n" +
                        "will read from within each world/save, if it exists there.\n" +
                        "Also see the option 'copyMainConfigToWorld' to enable automatically copying it there.");
        usePerWorldMainConfig = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableCommandRedirecting", true).setRequiresMcRestart(false);
        prop.setComment("Enables redirecting the vanilla /time, /weather etc. commands to the JED variants in WorldInfo-overridden dimensions");
        enableCommandRedirecting = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableForcedGameModes", false).setRequiresMcRestart(false);
        prop.setComment("Enables switching players' gamemode when they enter a dimension which has the ForceGamemode option set to true");
        enableForcedGamemodes = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableInitialSpawnDimensionOverride", false).setRequiresMcRestart(false);
        prop.setComment("Enables overriding the initial spawning dimension to something other than dim 0 (overworld).");
        enableInitialSpawnDimensionOverride = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableLoggingInfo", false).setRequiresMcRestart(false);
        prop.setComment("Enables a bunch of extra logging on the INFO level for registrations etc.");
        enableLoggingInfo = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableOverrideBiomeProvider", true).setRequiresMcRestart(false);
        prop.setComment("If enabled, then a '\"biome\": \"registrynameofbiome\"' value in the dimensions.json config will override the\n" +
                        "BiomeProvider of that dimension with BiomeProviderSingle, using the biome given as the value.\n" +
                        "This means that the entire dimension will use only that one biome set in the config.\n" +
                        "To get the registry names of biomes, you can use the TellMe mod (the command '/tellme dump biomes').");
        enableOverrideBiomeProvider = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableReplacingRegisteredDimensions", true).setRequiresMcRestart(false);
        prop.setComment("If enabled, then an 'override: true' boolean value for the dimension in\n" +
                        "the dimensions.json config can be used to override an existing dimension.");
        enableReplacingRegisteredDimensions = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableUnregisteringDimensions", false).setRequiresMcRestart(false);
        prop.setComment("If enabled, then an 'unregister: true' boolean value for the dimension in\n" +
                        "the dimensions.json config can be used to unregister existing dimension.");
        enableUnregisteringDimensions = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldBorders", false).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, adds custom WorldBorder syncing and removes default linking from other dimensions to the overworld border.");
        enableSeparateWorldBorders = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldInfo", true).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, all dimensions that exist in dimensions.json and have either a" +
                        "\"worldinfo\" or a \"worldinfo_onetime\" value present (an empty object is enough),\n" +
                        "will use separate WorldInfo instances (separate time, weather, world border, gamerules etc.).\n" +
                        "This works even if the dimension in question isn't registered by this mod\n" +
                        "(so vanilla, or other mod dimensions can have it too).");
        enableSeparateWorldInfo = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "initialSpawnDimensionId", 0).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled with the enableInitialSpawnDimensionOverride option, this will be used as the initial spawn dimension ID");
        initialSpawnDimensionId = prop.getInt();

        prop = conf.get(CATEGORY_VERSION, "configId", "__default").setRequiresMcRestart(false);
        prop.setComment("For the config file copying/replacement to happen, this id\n" +
                        "in the old per-world config must match the id in the current global/common config,\n" +
                        "for the automatic config upgrade/override from the global config to the per-world config to happen.");

        prop = conf.get(CATEGORY_VERSION, "version", 0).setRequiresMcRestart(false);
        prop.setComment("Config version tracking.\nIf you are a mod pack developer and need to force a config change\n" +
                        "for users (when using the per-world configs option), increase the version number here\n" +
                        "to tell the mod to copy this config version over an existing, older, per-world config.\n" +
                        "Also note that the 'configId' value should be set to something mod pack specific,\n" +
                        "basically to tie the per-world configs to the pack in question,\n" +
                        "and not overwrite the per-world config with one from a different pack.\n" +
                        "(Although it probably would be extremely rare for anyone to try to load the same world in a different pack...)");

        // Client stuff

        prop = conf.get(CATEGORY_CLIENT, "enableColorOverrides", true).setRequiresMcRestart(false);
        prop.setComment("Enables the Grass/Foliage/Water color customizations. This controls whether or not the event handlers get registered.");
        enableColorOverrides = prop.getBoolean();

        if (enableColorOverrides)
        {
            JustEnoughDimensions.proxy.registerClientEventHandler();
        }
        else
        {
            JustEnoughDimensions.proxy.unregisterClientEventHandler();
        }

        if (conf.hasChanged())
        {
            conf.save();
        }
    }
}
