package fi.dy.masa.justenoughdimensions;

import java.io.File;
import java.util.EnumMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.DimensionType;
import net.minecraft.world.chunk.storage.AnvilSaveConverter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.command.CommandTeleportJED;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.event.GamemodeTracker;
import fi.dy.masa.justenoughdimensions.network.DimensionSyncChannelHandler;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.proxy.CommonProxy;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.util.world.WorldBorderUtils;
import fi.dy.masa.justenoughdimensions.world.WorldProviderEndJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION, certificateFingerprint = Reference.FINGERPRINT,
    guiFactory = "fi.dy.masa.justenoughdimensions.config.JustEnoughDimensionsGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/justenoughdimensions/master/update.json",
    acceptedMinecraftVersions = "1.12")
public class JustEnoughDimensions
{
    @Mod.Instance(Reference.MOD_ID)
    public static JustEnoughDimensions instance;

    @SidedProxy(clientSide = Reference.PROXY_CLIENT, serverSide = Reference.PROXY_SERVER)
    public static CommonProxy proxy;

    public static final Logger logger = LogManager.getLogger(Reference.MOD_ID);
    public static EnumMap<Side, FMLEmbeddedChannel> channels;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        instance = this;

        Configs.loadConfigsFromFile(event.getSuggestedConfigurationFile());
        PacketHandler.init();
        proxy.registerEventHandlers();
        channels = NetworkRegistry.INSTANCE.newChannel("JEDChannel", DimensionSyncChannelHandler.instance);

        DimensionConfig.create(new File(event.getModConfigurationDirectory(), Reference.MOD_ID));
    }

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event)
    {
        File worldDir = new File(((AnvilSaveConverter) event.getServer().getActiveAnvilConverter()).savesDirectory, event.getServer().getFolderName());
        DimensionConfig.instance().readDimensionConfig(worldDir);

        // This needs to be here so that we are able to override existing dimensions before
        // they get loaded during server start.
        // But on the other hand we don't want to register the rest of the dimensions yet,
        // otherwise they would be considered 'static dimensions' and get loaded on server start.
        DimensionConfig.instance().registerOverriddenDimensions();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event)
    {
        event.registerServerCommand(new CommandJED());
        event.registerServerCommand(new CommandTeleportJED());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event)
    {
        // This removes the WorldBorder listeners that WorldServerMulti adds from other dimensions to the overworld border.
        // Thus this needs to be called after the static dimensions have loaded, ie. from this event specifically.
        WorldBorderUtils.removeDefaultBorderListeners();

        // Register our custom (non-override) dimensions. This is in this event so that our custom dimensions
        // won't get auto-loaded on server start as 'static' dimensions.
        DimensionConfig.instance().registerNonOverrideDimensions();

        GamemodeTracker.getInstance().readFromDisk();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event)
    {
        // Unregister custom dimensions. This is only useful in single player,
        // so that all the dimensions won't immediately load when joining a world again.
        DimensionConfig.instance().unregisterCustomDimensions();
    }

    public static void logInfo(String message, Object... params)
    {
        if (Configs.enableLoggingInfo)
        {
            logger.info(message, params);
        }
        else
        {
            logger.trace(message, params);
        }
    }

    @Mod.EventHandler
    public void onFingerPrintViolation(FMLFingerprintViolationEvent event)
    {
        // Not running in a dev environment
        if (event.isDirectory() == false)
        {
            logger.warn("*********************************************************************************************");
            logger.warn("*****                                    WARNING                                        *****");
            logger.warn("*****                                                                                   *****");
            logger.warn("*****   The signature of the mod file '{}' does not match the expected fingerprint!     *****", event.getSource().getName());
            logger.warn("*****   This might mean that the mod file has been tampered with!                       *****");
            logger.warn("*****   If you did not download the mod {} directly from Curse/CurseForge,       *****", Reference.MOD_NAME);
            logger.warn("*****   or using one of the well known launchers, and you did not                       *****");
            logger.warn("*****   modify the mod file at all yourself, then it's possible,                        *****");
            logger.warn("*****   that it may contain malware or other unwanted things!                           *****");
            logger.warn("*********************************************************************************************");
        }
    }

    // Register some default DimensionType entries early on, to try to avoid some issues
    // with mods that use a switch() with the DimensionType values (Optifine...).
    // Note that these still have some potential issues, the suffix being one.
    static
    {
        DimensionType.register("JED Surface",               "_dim7891", 7891, WorldProviderSurfaceJED.class, false);
        DimensionType.register("JED Hell",                  "_dim7892", 7892, WorldProviderHellJED.class, false);
        DimensionType.register("JED End",                   "_dim7893", 7893, WorldProviderEndJED.class, false);
        DimensionType.register("JED Surface (keep loaded)", "_dim7894", 7894, WorldProviderSurfaceJED.class, true);
        DimensionType.register("JED Hell (keep loaded)",    "_dim7895", 7895, WorldProviderHellJED.class, true);
        DimensionType.register("JED End (keep loaded)",     "_dim7896", 7896, WorldProviderEndJED.class, true);
    }
}
