package fi.dy.masa.justenoughdimensions.proxy;

import net.minecraftforge.common.MinecraftForge;
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
            MinecraftForge.EVENT_BUS.register(clientEventHandler);
            registered = true;
        }
    }

    @Override
    public void unregisterClientEventHandler()
    {
        if (registered)
        {
            MinecraftForge.EVENT_BUS.unregister(clientEventHandler);
            registered = false;
        }
    }
}
