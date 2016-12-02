package fi.dy.masa.justenoughdimensions.command.utils;

import java.util.Random;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;

public class CommandJEDWeather
{
    public static void execute(CommandJED cmd, int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            CommandJED.throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }

        if (args.length >= 1 && args.length <= 2)
        {
            int time = (300 + (new Random()).nextInt(600)) * 20;

            if (args.length >= 2)
            {
                time = CommandBase.parseInt(args[1], -10000000, 10000000) * 20;
            }

            String cmdName = args[0];
            WorldInfo worldinfo = world.getWorldInfo();

            if (cmdName.equals("clear"))
            {
                worldinfo.setCleanWeatherTime(time);
                worldinfo.setRainTime(0);
                worldinfo.setThunderTime(0);
                worldinfo.setRaining(false);
                worldinfo.setThundering(false);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.weather.clear", Integer.valueOf(dimension));
            }
            else if (cmdName.equals("rain"))
            {
                worldinfo.setCleanWeatherTime(0);
                worldinfo.setRainTime(time);
                worldinfo.setThunderTime(time);
                worldinfo.setRaining(true);
                worldinfo.setThundering(false);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.weather.rain", Integer.valueOf(dimension));
            }
            else if (cmdName.equals("thunder"))
            {
                worldinfo.setCleanWeatherTime(0);
                worldinfo.setRainTime(time);
                worldinfo.setThunderTime(time);
                worldinfo.setRaining(true);
                worldinfo.setThundering(true);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.weather.thunder", Integer.valueOf(dimension));
            }
            else
            {
                CommandJED.throwUsage("weather");
            }
        }
        else
        {
            CommandJED.throwUsage("weather");
        }
    }
}
