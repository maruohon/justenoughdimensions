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

        prop = conf.get(CATEGORY_GENERIC, "enableSeparateWorldInfo", false).setRequiresWorldRestart(true).setRequiresMcRestart(false);
        prop.setComment("If enabled, all dimensions that exist in dimensions.json and have the \"worldinfo\" key (even if the dimension isn't registered by this mod!) will use separate WorldInfo instances (separate time, weather, gamerules etc.)");
        enableSeparateWorldInfo = prop.getBoolean();

        if (conf.hasChanged() == true)
        {
            conf.save();
        }
    }
}
