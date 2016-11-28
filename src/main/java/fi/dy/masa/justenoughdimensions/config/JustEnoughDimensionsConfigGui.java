package fi.dy.masa.justenoughdimensions.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import fi.dy.masa.justenoughdimensions.reference.Reference;

public class JustEnoughDimensionsConfigGui extends GuiConfig
{
    public JustEnoughDimensionsConfigGui(GuiScreen parent)
    {
        super(parent, getConfigElements(), Reference.MOD_ID, false, false, getTitle(parent));
    }

    private static List<IConfigElement> getConfigElements()
    {
        List<IConfigElement> configElements = new ArrayList<IConfigElement>();

        configElements.addAll(new ConfigElement(Configs.config.getCategory(Configs.CATEGORY_GENERIC)).getChildElements());

        return configElements;
    }

    private static String getTitle(GuiScreen parent)
    {
        return GuiConfig.getAbridgedConfigPath(Configs.configurationFile.toString());
    }
}
