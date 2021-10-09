package fi.dy.masa.justenoughdimensions.proxy;

import org.apache.commons.lang3.StringUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;

public class ServerProxy extends CommonProxy
{
    @Override
    public void overrideServerGeneratorSettings(MinecraftServer server)
    {
        String settings = Configs.generatorSettingsOverride;

        if (StringUtils.isBlank(settings) == false &&
            server instanceof DedicatedServer)
        {
            JustEnoughDimensions.logInfo("WorldInfoUtils.overrideServerGeneratorSettings: Overriding the server's generator-settings value with '{}'", settings);
            ((DedicatedServer) server).setProperty("generator-settings", settings);
        }
    }
}
