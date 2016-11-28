package fi.dy.masa.justenoughdimensions;

import java.io.File;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import fi.dy.masa.justenoughdimensions.command.CommandTeleportJED;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.proxy.IProxy;
import fi.dy.masa.justenoughdimensions.reference.Reference;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.MOD_VERSION,
    guiFactory = "fi.dy.masa.justenoughdimensions.config.JustEnoughDimensionsGuiFactory",
    updateJSON = "https://raw.githubusercontent.com/maruohon/justenoughdimensions/master/update.json",
    acceptableRemoteVersions = "*", acceptedMinecraftVersions = "1.10.2")
public class JustEnoughDimensions
{
    @Mod.Instance(Reference.MOD_ID)
    public static JustEnoughDimensions instance;

    @SidedProxy(clientSide = Reference.PROXY_CLIENT, serverSide = Reference.PROXY_SERVER)
    public static IProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        instance = this;
        logger = event.getModLog();
        Configs.loadConfigsFromFile(event.getSuggestedConfigurationFile());
        DimensionConfig.create(new File(event.getModConfigurationDirectory(), Reference.MOD_ID));
        proxy.registerEventHandlers();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event)
    {
        DimensionConfig.instance().readConfigAndRegisterDimensions();
        event.registerServerCommand(new CommandTeleportJED());
    }
}
