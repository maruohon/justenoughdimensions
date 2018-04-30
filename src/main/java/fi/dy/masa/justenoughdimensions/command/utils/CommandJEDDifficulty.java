package fi.dy.masa.justenoughdimensions.command.utils;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

public class CommandJEDDifficulty
{
    public static void execute(CommandJED cmd, int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        WorldServer world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            if (args.length == 0)
            {
                EnumDifficulty diff = world.getWorldInfo().getDifficulty();
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.difficulty.print", Integer.valueOf(dimension), diff);
                return;
            }

            EnumDifficulty diff = getDifficultyFromCommand(args[0]);

            if (world.getWorldInfo() instanceof WorldInfoJED)
            {
                ((WorldInfoJED) world.getWorldInfo()).setDifficultyJED(diff);
            }
            else
            {
                world.getWorldInfo().setDifficulty(diff);

                if (world.provider.getDimension() == 0)
                {
                    world.getMinecraftServer().setDifficultyForAllWorlds(diff);
                }
            }

            world.setAllowedSpawnTypes(diff != EnumDifficulty.PEACEFUL, world.getMinecraftServer().getCanSpawnAnimals());

            CommandBase.notifyCommandListener(sender, cmd, "jed.commands.difficulty.success", Integer.valueOf(dimension), diff);

            if (world.getWorldInfo() instanceof DerivedWorldInfo)
            {
                CommandJED.throwCommand("command_in_derived_world_info_dimension");
            }
        }
        else
        {
            CommandJED.throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }
    }

    static EnumDifficulty getDifficultyFromCommand(String str) throws CommandException
    {
        if (str.equalsIgnoreCase("peaceful") || str.equalsIgnoreCase("p")) { return EnumDifficulty.PEACEFUL; }
        if (str.equalsIgnoreCase("easy")     || str.equalsIgnoreCase("e")) { return EnumDifficulty.EASY;     }
        if (str.equalsIgnoreCase("normal")   || str.equalsIgnoreCase("n")) { return EnumDifficulty.NORMAL;   }
        if (str.equalsIgnoreCase("hard")     || str.equalsIgnoreCase("h")) { return EnumDifficulty.HARD;     }

        return EnumDifficulty.getDifficultyEnum(CommandBase.parseInt(str, 0, 3));
    }
}
