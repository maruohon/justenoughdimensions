package fi.dy.masa.justenoughdimensions.command.utils;

import com.google.common.base.Predicates;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

public class CommandJEDDefaultGameType
{
    public static void execute(CommandJED cmd, int dimension, String[] args, MinecraftServer server, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            if (args.length == 0)
            {
                GameType type = world.getWorldInfo().getGameType();
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.defaultgamemode.print", Integer.valueOf(dimension), type);
                return;
            }

            GameType type = getGameModeFromCommand(sender, args[0]);

            if (world.getWorldInfo() instanceof WorldInfoJED)
            {
                ((WorldInfoJED) world.getWorldInfo()).setGameTypeJED(type);
            }
            else
            {
                world.getWorldInfo().setGameType(type);
            }

            if (server.getForceGamemode())
            {
                for (EntityPlayerMP player : world.getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue()))
                {
                    player.setGameType(type);
                }
            }

            CommandBase.notifyCommandListener(sender, cmd, "jed.commands.defaultgamemode.success", Integer.valueOf(dimension), type);
        }
        else
        {
            CommandJED.throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }
    }

    static GameType getGameModeFromCommand(ICommandSender sender, String str) throws CommandException
    {
        GameType gametype = GameType.parseGameTypeWithDefault(str, GameType.NOT_SET);
        return gametype == GameType.NOT_SET ? WorldSettings.getGameTypeById(CommandBase.parseInt(str, 0, GameType.values().length - 2)) : gametype;
    }
}
