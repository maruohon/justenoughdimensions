package fi.dy.masa.justenoughdimensions.proxy;

import net.minecraftforge.common.MinecraftForge;
import fi.dy.masa.justenoughdimensions.event.PlayerEventHandler;

public class ServerProxy implements IProxy
{
    @Override
    public void registerEventHandlers()
    {
        MinecraftForge.EVENT_BUS.register(new PlayerEventHandler());
    }
}
