package fi.dy.masa.justenoughdimensions.command;

import java.util.List;
import java.util.Random;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;

public class CommandJED extends CommandBase
{

    @Override
    public String getName()
    {
        return "jed";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return  "'/jed weather <dimension> <clear|rain|thunder> [duration]' OR " +
                "'/jed time <dimension> <add|set> <amount>' OR " +
                "'/jed time <dimension> <query> <day|daytime|gametime>' OR " +
                "'/jed reload' OR " +
                "'/jed unregister' OR " +
                "'/jed unregister-remove (only removes from the json, doesn't delete the world)' OR " +
                "'/jed register <dim id> (uses defaults for the DimensionType)' OR " +
                "'/jed register <dim id> <name> <suffix> <keep loaded true/false> <worldprovider>'";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, "time", "weather", "reload", "register", "unregister", "unregister-remove");
        }
        else if (args.length >= 3)
        {
            if (args[0].equals("weather"))
            {
                return getListOfStringsMatchingLastWord(args, "clear", "rain", "thunder");
            }
            else if (args[0].equals("time"))
            {
                if (args.length == 3)
                {
                    return getListOfStringsMatchingLastWord(args, "add", "set", "query");
                }
                else if (args.length == 4 && args[2].equals("set"))
                {
                    return getListOfStringsMatchingLastWord(args, "day", "night");
                }
                else if (args.length == 4 && args[2].equals("query"))
                {
                    return getListOfStringsMatchingLastWord(args, "day", "daytime", "gametime");
                }
            }
        }

        return super.getTabCompletions(server, sender, args, pos);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length == 0)
        {
            throw new WrongUsageException(this.getUsage(sender));
        }

        if (args[0].equals("weather") && args.length >= 2)
        {
            this.changeWeather(parseInt(args[1]), dropFirstStrings(args, 2), sender);
        }
        else if (args[0].equals("time") && args.length >= 2)
        {
            this.commandTime(parseInt(args[1]), dropFirstStrings(args, 2), sender);
        }
        else if (args[0].equals("reload") && args.length == 1)
        {
            DimensionConfig.instance().readDimensionConfig();
            DimensionConfig.instance().registerDimensions();
            notifyCommandListener(sender, this, "jed.commands.reloaded");
        }
        else if (args[0].equals("unregister") && args.length == 2)
        {
            int dimension = parseInt(args[1]);
            this.unregister(dimension);
            notifyCommandListener(sender, this, "jed.commands.unregister", Integer.valueOf(dimension));
        }
        else if (args[0].equals("unregister-remove"))
        {
            int dimension = parseInt(args[1]);
            this.unregister(dimension);
            DimensionConfig.instance().removeDimensionAndSaveConfig(dimension);
            notifyCommandListener(sender, this, "jed.commands.unregister.remove", Integer.valueOf(dimension));
        }
        else if (args[0].equals("register") && args.length >= 2)
        {
            int dimension = parseInt(args[1]);

            if (args.length == 2)
            {
                DimensionConfig.instance().registerDimension(dimension);
                notifyCommandListener(sender, this, "jed.commands.register.default", Integer.valueOf(dimension));
            }
            else if (args.length == 6)
            {
                DimensionConfig.instance().registerDimension(dimension, args[2], args[3], Boolean.parseBoolean(args[4]), args[5]);
                notifyCommandListener(sender, this, "jed.commands.register.custom", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException(this.getUsage(sender));
            }
        }
        else
        {
            throw new WrongUsageException(this.getUsage(sender));
        }
    }

    private void changeWeather(int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            throw new WrongUsageException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }

        if (args.length >= 1 && args.length <= 2)
        {
            int time = (300 + (new Random()).nextInt(600)) * 20;

            if (args.length >= 2)
            {
                time = parseInt(args[1], -10000000, 10000000) * 20;
            }

            WorldInfo worldinfo = world.getWorldInfo();

            if ("clear".equalsIgnoreCase(args[0]))
            {
                worldinfo.setCleanWeatherTime(time);
                worldinfo.setRainTime(0);
                worldinfo.setThunderTime(0);
                worldinfo.setRaining(false);
                worldinfo.setThundering(false);
                notifyCommandListener(sender, this, "jed.commands.weather.clear", Integer.valueOf(dimension));
            }
            else if ("rain".equalsIgnoreCase(args[0]))
            {
                worldinfo.setCleanWeatherTime(0);
                worldinfo.setRainTime(time);
                worldinfo.setThunderTime(time);
                worldinfo.setRaining(true);
                worldinfo.setThundering(false);
                notifyCommandListener(sender, this, "jed.commands.weather.rain", Integer.valueOf(dimension));
            }
            else if ("thunder".equalsIgnoreCase(args[0]))
            {
                worldinfo.setCleanWeatherTime(0);
                worldinfo.setRainTime(time);
                worldinfo.setThunderTime(time);
                worldinfo.setRaining(true);
                worldinfo.setThundering(true);
                notifyCommandListener(sender, this, "jed.commands.weather.thunder", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("'/jed weather <dimension> <clear|rain|thunder> [duration]'");
            }
        }
        else
        {
            throw new WrongUsageException("'/jed weather <dimension> <clear|rain|thunder> [duration]'");
        }
    }

    private void commandTime(int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }

        if (args.length > 1)
        {
            if ("set".equals(args[0]))
            {
                int time;

                if ("day".equals(args[1]))
                {
                    time = 1000;
                }
                else if ("night".equals(args[1]))
                {
                    time = 13000;
                }
                else
                {
                    time = parseInt(args[1], 0);
                }

                world.setWorldTime(time);
                notifyCommandListener(sender, this, "jed.commands.time.set", Integer.valueOf(time), Integer.valueOf(dimension));
            }
            else if ("add".equals(args[0]))
            {
                int amount = parseInt(args[1], -24000, 24000);
                world.setWorldTime(world.getWorldTime() + amount);
                notifyCommandListener(sender, this, "jed.commands.time.add", Integer.valueOf(amount), Integer.valueOf(dimension));
            }
            else if ("query".equals(args[0]))
            {
                if ("daytime".equals(args[1]))
                {
                    int i = (int)(sender.getEntityWorld().getWorldTime() % 24000L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    notifyCommandListener(sender, this, "jed.commands.time.query.daytime", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else if ("day".equals(args[1]))
                {
                    int i = (int)(sender.getEntityWorld().getWorldTime() / 24000L % 2147483647L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    notifyCommandListener(sender, this, "jed.commands.time.query.day", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else if ("gametime".equals(args[1]))
                {
                    int i = (int)(sender.getEntityWorld().getTotalWorldTime() % 2147483647L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    notifyCommandListener(sender, this, "jed.commands.time.query.gametime", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else
                {
                    throw new WrongUsageException("/jed time <dimension> <add|set> <amount>' OR '/jed time <dimension> <query> <day|daytime|gametime>'");
                }
            }
        }
        else
        {
            throw new WrongUsageException("/jed time <dimension> <add|set> <amount>' OR '/jed time <dimension> <query> <day|daytime|gametime>'");
        }
    }

    private void unregister(int dimension) throws CommandException
    {
        if (DimensionManager.isDimensionRegistered(dimension))
        {
            if (DimensionManager.getWorld(dimension) == null)
            {
                DimensionManager.unregisterDimension(dimension);
            }
            else
            {
                throw new NumberInvalidException("jed.commands.error.dimension.loaded", Integer.valueOf(dimension));
            }
        }
        else
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notregistered", Integer.valueOf(dimension));
        }
    }

    public static String[] dropFirstStrings(String[] input, int toDrop)
    {
        if (toDrop > input.length)
        {
            return new String[0];
        }

        String[] arr = new String[input.length - toDrop];
        System.arraycopy(input, toDrop, arr, 0, input.length - toDrop);
        return arr;
    }
}
