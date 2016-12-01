package fi.dy.masa.justenoughdimensions;

import java.io.File;
import java.util.EnumMap;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.command.CommandTeleportJED;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandler;
import fi.dy.masa.justenoughdimensions.network.DimensionSyncChannelHandler;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.proxy.IProxy;
import fi.dy.masa.justenoughdimensions.reference.Reference;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION,
    guiFactory = "fi.dy.masa.justenoughdimensions.config.JustEnoughDimensionsGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/justenoughdimensions/master/update.json",
    acceptedMinecraftVersions = "[1.10.2,1.11]")
public class JustEnoughDimensions
{
    @Mod.Instance(Reference.MOD_ID)
    public static JustEnoughDimensions instance;

    @SidedProxy(clientSide = Reference.PROXY_CLIENT, serverSide = Reference.PROXY_SERVER)
    public static IProxy proxy;

    public static Logger logger;
    public static EnumMap<Side, FMLEmbeddedChannel> channels;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        instance = this;
        logger = event.getModLog();

        Configs.loadConfigsFromFile(event.getSuggestedConfigurationFile());
        PacketHandler.init();
        proxy.registerEventHandlers();
        channels = NetworkRegistry.INSTANCE.newChannel("JEDChannel", DimensionSyncChannelHandler.instance);

        DimensionConfig.create(new File(event.getModConfigurationDirectory(), Reference.MOD_ID));
    }

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event)
    {
        DimensionConfig.instance().readDimensionConfig();

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
        JEDEventHandler.instance().removeDefaultBorderListeners();

        // This is here again so that we can read the per-world configuration file, if one exists.
        // That can only be done after the overworld has loaded, so that we know the save directory.
        DimensionConfig.instance().readDimensionConfig();

        // Register our custom (non-override) dimensions. This is in this event so that our custom dimensions
        // won't get auto-loaded on server start as 'static' dimensions.
        DimensionConfig.instance().registerDimensions();
    }
}
