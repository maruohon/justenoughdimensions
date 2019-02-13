package fi.dy.masa.justenoughdimensions.event;

import net.minecraftforge.client.event.EntityViewRenderEvent.RenderFogEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;

public class JEDEventHandlerClientFog
{
    @SubscribeEvent
    public void onRenderFog(RenderFogEvent event)
    {
        JEDWorldProperties props = JEDWorldProperties.getClientProperties();

        /*
        if (props)
        {
            GlStateManager.setFogStart(f * 0.05F);
            GlStateManager.setFogEnd(Math.min(f, 192.0F) * 0.5F);
        }
        */
    }
}
