package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.justenoughdimensions.reference.Reference;

public class Configs
{
    public static File configurationFile;
    public static Configuration config;
    
    public static final String CATEGORY_GENERIC = "Generic";

    public static boolean enableReplacingRegisteredDimensions;
    public static boolean enableSeparateWorldBorders;
    public static boolean enableSeparateWorldInfo;

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
        configurationFile = configFile;
        config = new Configuration(configFile, null, false);
        config.load();

        loadConfigs(config);
    }

    public static void loadConfigs(Configuration conf)
    {
        Property prop;

        prop = conf.get(CATEGORY_GENERIC, "enableReplacingRegisteredDimensions", false).setRequiresMcRestart(false);
        prop.setComment("If enabled, then an 'override: true' boolean value for the dimension in the dimensions.json config can be used to override an existing registered dimension.");
        enableReplacingRegisteredDimensions = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldBorders", false).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, adds custom WorldBorder syncing and removes default linking from other dimensions to the overworld border.");
        enableSeparateWorldBorders = prop.getBoolean();

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldInfo", false).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, all dimensions that exist in dimensions.json and have the \"worldinfo\" key present (an empty object is enough - works even if the dimension isn't registered by this mod because it already exist!), will use separate WorldInfo instances (separate time, weather, world border, gamerules etc.)");
        enableSeparateWorldInfo = prop.getBoolean();

        if (conf.hasChanged() == true)
        {
            conf.save();
        }
    }
}
