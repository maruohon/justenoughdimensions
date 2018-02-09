package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;

public class Configs
{
    public static String configurationFileName;
    public static Configuration config;
    
    public static final String CATEGORY_CLIENT = "Client";
    public static final String CATEGORY_GENERIC = "Generic";

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

    public static int initialSpawnDimensionId;

    @SubscribeEvent
    public void onConfigChangedEvent(OnConfigChangedEvent event)
    {
        if (Reference.MOD_ID.equals(event.getModID()) == true)
        {
            loadConfigs(config);
        }
    }

    public static void loadConfigsFromFile(File configFile)
    {
        configurationFileName = configFile.toString();
        config = new Configuration(configFile, null, false);

        reloadConfigsFromFile();
    }

    public static boolean reloadConfigsFromFile()
    {
        if (config != null)
        {
            config.load();
            loadConfigs(config);

            JustEnoughDimensions.logger.info("Reloaded configs from file");
            return true;
        }

        return false;
    }

    public static void loadConfigs(Configuration conf)
    {
        Property prop;

        prop = conf.get(CATEGORY_GENERIC, "enableCommandRedirecting", true).setRequiresMcRestart(false);
        prop.setComment("Enables redirecting the vanilla /time, /weather etc. commands to the JED variants in WorldInfo-overridden dimensions");
        enableCommandRedirecting = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableForcedGamemodes", false).setRequiresMcRestart(false);
        prop.setComment("Enables switching players' gamemode when they enter a dimension which has the ForceGamemode option set to true");
        enableForcedGamemodes = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableInitialSpawnDimensionOverride", false).setRequiresMcRestart(false);
        prop.setComment("Enables overriding the initial spawning dimension to something other than dim 0 (overworld).");
        enableInitialSpawnDimensionOverride = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableLoggingInfo", false).setRequiresMcRestart(false);
        prop.setComment("Enables a bunch of extra logging on the INFO level for registrations etc.");
        enableLoggingInfo = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableOverrideBiomeProvider", false).setRequiresMcRestart(false);
        prop.setComment("If enabled, then a '\"biome\": \"registrynameofbiome\"' value in the dimensions.json config will override the\n" +
                        "BiomeProvider of that dimension with BiomeProviderSingle, using the biome given as the value.\n" +
                        "This means that the entire dimension will use only that one biome set in the config.\n" +
                        "To get the registry names of biomes, you can use the TellMe mod (the command '/tellme dump biomes').");
        enableOverrideBiomeProvider = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableReplacingRegisteredDimensions", false).setRequiresMcRestart(false);
        prop.setComment("If enabled, then an 'override: true' boolean value for the dimension in the dimensions.json config can be used to override an existing dimension.");
        enableReplacingRegisteredDimensions = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableUnregisteringDimensions", false).setRequiresMcRestart(false);
        prop.setComment("If enabled, then an 'unregister: true' boolean value for the dimension in the dimensions.json config can be used to unregister existing dimension.");
        enableUnregisteringDimensions = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldBorders", false).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, adds custom WorldBorder syncing and removes default linking from other dimensions to the overworld border.");
        enableSeparateWorldBorders = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldInfo", false).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, all dimensions that exist in dimensions.json and have either a \"worldinfo\" or a \"worldinfo_onetime\" value present\n" +
                        "(an empty object is enough), will use separate WorldInfo instances (separate time, weather, world border, gamerules etc.).\n" +
                        "This works even if the dimension in question isn't registered by this mod (so vanilla, or other mod dimensions can have it too).");
        enableSeparateWorldInfo = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "initialSpawnDimensionId", 0).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled with the enableInitialSpawnDimensionOverride option, this will be used as the initial spawn dimension ID");
        initialSpawnDimensionId = prop.getInt();

        // Client stuff

        prop = conf.get(CATEGORY_CLIENT, "enableColorOverrides", true).setRequiresMcRestart(false);
        prop.setComment("Enables the Grass/Foliage/Water color customizations. This controls whether or not the event handlers get registered.");
        enableColorOverrides = prop.getBoolean();

        if (enableColorOverrides)
        {
            JustEnoughDimensions.logInfo("Registering the client event handler (for Grass/Foliage/Water colors)");
            JustEnoughDimensions.proxy.registerClientEventHandler();
        }
        else
        {
            JustEnoughDimensions.logInfo("Un-registering the client event handler (for Grass/Foliage/Water colors)");
            JustEnoughDimensions.proxy.unregisterClientEventHandler();
        }

        if (conf.hasChanged())
        {
            conf.save();
        }
    }
}
