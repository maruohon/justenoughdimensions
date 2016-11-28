package fi.dy.masa.justenoughdimensions.proxy;

import net.minecraftforge.common.MinecraftForge;
import fi.dy.masa.justenoughdimensions.config.Configs;

public class ClientProxy implements IProxy
{
    @Override
    public void registerEventHandlers()
    {
        MinecraftForge.EVENT_BUS.register(new Configs());
    }
}
