package fi.dy.masa.justenoughdimensions.command.utils;

import com.google.common.base.Predicates;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.command.CommandJED;

public class CommandJEDGameRule
{
    public static void execute(CommandJED cmd, int dimension, String[] args, MinecraftServer server, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            GameRules rules = world.getGameRules();
            String key = args.length > 0 ? args[0] : "";

            if (args.length == 0)
            {
                sender.sendMessage(new TextComponentString(CommandBase.joinNiceString(rules.getRules())));
            }
            else if (args.length == 1)
            {
                if (rules.hasRule(key) == false)
                {
                    throw new CommandException("jed.commands.gamerule.norule", key, Integer.valueOf(dimension));
                }

                String value = rules.getString(key);
                sender.sendMessage(new TextComponentString("DIM " + dimension + ": " + key + " = " + value));
                sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, rules.getInt(key));
            }
            else
            {
                String value = args.length > 1 ? CommandBase.buildString(args, 1) : "";

                if (rules.areSameType(key, GameRules.ValueType.BOOLEAN_VALUE) && value.equals("true") == false && value.equals("false") == false)
                {
                    throw new CommandException("commands.generic.boolean.invalid", value);
                }

                rules.setOrCreateGameRule(key, value);
                notifyGameRuleChange(rules, key, world);
                CommandBase.notifyCommandListener(sender, cmd, "jed.commands.gamerule.success", key, value, Integer.valueOf(dimension));
            }
        }
        else
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }
    }

    static void notifyGameRuleChange(GameRules rules, String key, World world)
    {
        if (key.equals("reducedDebugInfo"))
        {
            byte opCode = (byte)(rules.getBoolean(key) ? 22 : 23);

            for (EntityPlayerMP player : world.getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue()))
            {
                player.connection.sendPacket(new SPacketEntityStatus(player, opCode));
            }
        }
    }
}
