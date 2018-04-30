package fi.dy.masa.justenoughdimensions.proxy;

import net.minecraftforge.common.MinecraftForge;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandlerClient;

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
}
