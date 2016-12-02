package fi.dy.masa.justenoughdimensions.command.utils;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;

public class CommandJEDTime
{
    public static void execute(CommandJED cmd, int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            CommandJED.throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }

        if (args.length > 1)
        {
            String cmdName = args[0];

            if (cmdName.equals("set"))
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
                    time = CommandBase.parseInt(args[1], 0);
                }

                world.setWorldTime(time);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.time.set", Integer.valueOf(dimension), Integer.valueOf(time));
            }
            else if (cmdName.equals("add"))
            {
                int amount = CommandBase.parseInt(args[1], -24000, 24000);
                world.setWorldTime(world.getWorldTime() + amount);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.time.add", Integer.valueOf(dimension), Integer.valueOf(amount));
            }
            else if (cmdName.equals("query"))
            {
                if ("daytime".equals(args[1]))
                {
                    int i = (int)(world.getWorldTime() % 24000L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.time.query.daytime", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else if ("day".equals(args[1]))
                {
                    int i = (int)(world.getWorldTime() / 24000L % 2147483647L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.time.query.day", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else if ("gametime".equals(args[1]))
                {
                    int i = (int)(world.getTotalWorldTime() % 2147483647L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.time.query.gametime", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else
                {
                    CommandJED.throwUsage("time");
                }
            }
        }
        else
        {
            CommandJED.throwUsage("time");
        }
    }
}
