package fi.dy.masa.justenoughdimensions.command.utils;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;

public class CommandJEDWorldBorder
{
    public static void execute(CommandJED cmd, int dimension, String[] args, MinecraftServer server, ICommandSender sender) throws CommandException
    {
        if (args.length < 1)
        {
            CommandJED.throwUsage("worldborder");
        }

        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            CommandJED.throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }

        String cmdName = args[0];
        WorldBorder border = world.getWorldBorder();

        if (cmdName.equals("set"))
        {
            if (args.length != 2 && args.length != 3)
            {
                CommandJED.throwUsage("worldborder.set");
            }

            double oldSize = border.getTargetSize();
            double newSize = CommandBase.parseDouble(args[1], 1.0D, 6.0E7D);
            long i = args.length > 2 ? CommandBase.parseLong(args[2], 0L, 9223372036854775L) * 1000L : 0L;

            if (i > 0L)
            {
                border.setTransition(oldSize, newSize, i);

                if (oldSize > newSize)
                {
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.set.slowly.shrink.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(i / 1000L));
                }
                else
                {
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.set.slowly.grow.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(i / 1000L));
                }
            }
            else
            {
                border.setTransition(newSize);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.set.success",
                        Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize));
            }
        }
        else if (cmdName.equals("add"))
        {
            if (args.length != 2 && args.length != 3)
            {
                CommandJED.throwUsage("worldborder.add");
            }

            double oldSize = border.getDiameter();
            double newSize = oldSize + CommandBase.parseDouble(args[1], -oldSize, 6.0E7D - oldSize);
            long time = border.getTimeUntilTarget() + (args.length > 2 ? CommandBase.parseLong(args[2], 0L, 9223372036854775L) * 1000L : 0L);

            if (time > 0L)
            {
                border.setTransition(oldSize, newSize, time);

                if (oldSize > newSize)
                {
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.set.slowly.shrink.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(time / 1000L));
                }
                else
                {
                    CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.set.slowly.grow.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(time / 1000L));
                }
            }
            else
            {
                border.setTransition(newSize);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.set.success",
                        Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize));
            }
        }
        else if (cmdName.equals("center"))
        {
            if (args.length != 3)
            {
                CommandJED.throwUsage("worldborder.center");
            }

            BlockPos blockpos = sender.getPosition();
            double centerX = CommandBase.parseDouble(blockpos.getX() + 0.5D, args[1], true);
            double centerZ = CommandBase.parseDouble(blockpos.getZ() + 0.5D, args[2], true);
            border.setCenter(centerX, centerZ);
            CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.center.success",
                    Integer.valueOf(dimension), Double.valueOf(centerX), Double.valueOf(centerZ));
        }
        else if (cmdName.equals("damage"))
        {
            if (args.length >= 2 && args[1].equals("buffer"))
            {
                if (args.length != 3)
                {
                    CommandJED.throwUsage("worldborder.damage.buffer");
                }

                double bufferSize = CommandBase.parseDouble(args[2], 0.0D);
                double oldSize = border.getDamageBuffer();
                border.setDamageBuffer(bufferSize);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.damage.buffer.success",
                        Integer.valueOf(dimension), String.format("%.1f", bufferSize), String.format("%.1f", oldSize));
            }
            else if (args.length >= 2 && args[1].equals("amount"))
            {
                if (args.length != 3)
                {
                    CommandJED.throwUsage("worldborder.damage.amount");
                }

                double damage = CommandBase.parseDouble(args[2], 0.0D);
                double oldDamage = border.getDamageAmount();
                border.setDamageAmount(damage);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.damage.amount.success",
                        Integer.valueOf(dimension), String.format("%.2f", damage), String.format("%.2f", oldDamage));
            }
            else
            {
                CommandJED.throwUsage("worldborder.damage");
            }
        }
        else if (cmdName.equals("warning"))
        {
            if (args.length >= 2 && args[1].equals("time"))
            {
                if (args.length != 3)
                {
                    CommandJED.throwUsage("worldborder.warning.time");
                }

                int time = CommandBase.parseInt(args[2], 0);
                int oldTime = border.getWarningTime();
                border.setWarningTime(time);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.warning.time.success",
                        Integer.valueOf(dimension), Integer.valueOf(time), Integer.valueOf(oldTime));
            }
            else if (args.length >= 2 && args[1].equals("distance"))
            {
                if (args.length != 3)
                {
                    CommandJED.throwUsage("worldborder.warning.distance");
                }

                int distance = CommandBase.parseInt(args[2], 0);
                int oldDistance = border.getWarningDistance();
                border.setWarningDistance(distance);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.worldborder.warning.distance.success",
                        Integer.valueOf(dimension), Integer.valueOf(distance), Integer.valueOf(oldDistance));
            }
            else
            {
                CommandJED.throwUsage("worldborder.warning");
            }
        }
        else if (cmdName.equals("get"))
        {
            double diameter = border.getDiameter();
            double centerX = border.getCenterX();
            double centerZ = border.getCenterZ();
            sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, MathHelper.floor(diameter + 0.5D));
            sender.sendMessage(new TextComponentTranslation("jed.commands.worldborder.get.success",
                    Integer.valueOf(dimension), String.format("%.2f", diameter),
                    String.format("%.1f", centerX), String.format("%.1f", centerZ)));
        }
        else
        {
            CommandJED.throwUsage("worldborder");
        }
    }
}
