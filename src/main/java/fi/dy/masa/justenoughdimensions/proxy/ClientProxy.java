package fi.dy.masa.justenoughdimensions.proxy;

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.storage.SaveFormatOld;
import net.minecraftforge.common.MinecraftForge;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandlerClient;
import fi.dy.masa.justenoughdimensions.util.world.WorldFileUtils;

public class ClientProxy extends CommonProxy
{
    private static JEDEventHandlerClient clientEventHandler = new JEDEventHandlerClient();
    private static boolean registered;

    @Override
    public void registerEventHandlers()
    {
        super.registerEventHandlers();

        MinecraftForge.EVENT_BUS.register(new Configs());
    }

    @Override
    public void registerClientEventHandler()
    {
        if (registered == false)
        {
            JustEnoughDimensions.logInfo("Registering the client event handler (for Grass/Foliage/Water colors)");
            MinecraftForge.EVENT_BUS.register(clientEventHandler);
            registered = true;
        }
    }

    @Override
    public void unregisterClientEventHandler()
    {
        if (registered)
        {
            JustEnoughDimensions.logInfo("Un-registering the client event handler (for Grass/Foliage/Water colors)");
            MinecraftForge.EVENT_BUS.unregister(clientEventHandler);
            registered = false;
        }
    }

    @Override
    public void overrideServerGeneratorSettings(MinecraftServer server)
    {
        String settings = Configs.generatorSettingsOverride;

        if (StringUtils.isBlank(settings) == false &&
            server instanceof IntegratedServer)
        {
            File savesDir = ((SaveFormatOld) server.getActiveAnvilConverter()).savesDirectory;
            File file = savesDir.toPath().resolve(server.getWorldName()).resolve("level.dat").toFile();
            JustEnoughDimensions.logInfo("WorldInfoUtils.overrideServerGeneratorSettings: Overriding the generatorOptions value with '{}' in the level file '{}'",
                                         settings, file.getAbsolutePath());
            WorldFileUtils.overrideValuesInWorldInfo(file, server, (tag) -> tag.setString("generatorOptions", settings));
        }
    }
}
