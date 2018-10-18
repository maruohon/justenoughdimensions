package fi.dy.masa.justenoughdimensions.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

public class DummyRenderer extends net.minecraftforge.client.IRenderHandler
{
    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc)
    {
        // NO-OP
    }
}
