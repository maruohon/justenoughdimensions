package fi.dy.masa.justenoughdimensions.proxy;

import net.minecraftforge.common.MinecraftForge;
import fi.dy.masa.justenoughdimensions.event.JEDEventHandler;

public class CommonProxy
{
    public void registerEventHandlers()
    {
        MinecraftForge.EVENT_BUS.register(new JEDEventHandler());
    }

    public void registerClientEventHandler()
    {
    }

    public void unregisterClientEventHandler()
    {
    }
}
