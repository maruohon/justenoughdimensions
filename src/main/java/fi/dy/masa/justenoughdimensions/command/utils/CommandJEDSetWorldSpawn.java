package fi.dy.masa.justenoughdimensions.command.utils;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;

public class CommandJEDSetWorldSpawn
{
    public static void execute(CommandJED cmd, int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            BlockPos pos = null;

            if (args.length == 0)
            {
                pos = sender instanceof EntityPlayer ? ((EntityPlayer) sender).getPosition() : null;
            }
            else if (args.length == 3)
            {
                pos = new BlockPos(CommandBase.parseInt(args[0]), CommandBase.parseInt(args[1]), CommandBase.parseInt(args[2]));
            }
            else if (args.length == 1 && args[0].equals("query"))
            {
                pos = world.getSpawnPoint();
                sender.sendMessage(new TextComponentTranslation("jed.commands.setworldspawn.query",
                        Integer.valueOf(dimension), Integer.valueOf(pos.getX()), Integer.valueOf(pos.getY()), Integer.valueOf(pos.getZ())));
                return;
            }

            if (pos != null)
            {
                world.setSpawnPoint(pos);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.setworldspawn.success",
                        Integer.valueOf(dimension), Integer.valueOf(pos.getX()), Integer.valueOf(pos.getY()), Integer.valueOf(pos.getZ()));

                if (world.getWorldInfo() instanceof DerivedWorldInfo)
                {
                    CommandJED.throwCommand("command_in_derived_world_info_dimension");
                }
            }
            else
            {
                CommandJED.throwUsage("setworldspawn");
            }
        }
        else
        {
            CommandJED.throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }
    }
}
