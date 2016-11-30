package fi.dy.masa.justenoughdimensions.command;

import java.util.List;
import java.util.Random;
import com.google.common.base.Predicates;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

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
                "'/jed defaultgametype <dimension> [value]' OR " +
                "'/jed difficulty <dimension> [value]' OR " +
                "'/jed gamerule <dimension> <key> [value]' OR " +
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
            return getListOfStringsMatchingLastWord(args, "defaultgamemode", "difficulty", "gamerule", "time", "weather", "reload", "register", "unregister", "unregister-remove");
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
            else if (args[0].equals("defaultgametype") && args.length == 3)
            {
                return getListOfStringsMatchingLastWord(args, "survival", "creative", "adventure", "spectator");
            }
            else if (args[0].equals("difficulty") && args.length == 3)
            {
                return getListOfStringsMatchingLastWord(args, "peaceful", "easy", "normal", "hard");
            }
            else if (args[0].equals("gamerule"))
            {
                if (args.length == 3)
                {
                    return getListOfStringsMatchingLastWord(args, this.getOverWorldGameRules(server).getRules());
                }
                else if (args.length == 4 && this.getOverWorldGameRules(server).areSameType(args[2], GameRules.ValueType.BOOLEAN_VALUE))
                {
                    return getListOfStringsMatchingLastWord(args, "true", "false");
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
            this.commandWeather(parseInt(args[1]), dropFirstStrings(args, 2), sender);
        }
        else if (args[0].equals("time") && args.length >= 2)
        {
            this.commandTime(parseInt(args[1]), dropFirstStrings(args, 2), sender);
        }
        else if (args[0].equals("defaultgametype") && args.length >= 2)
        {
            this.commandDefaultGameType(parseInt(args[1]), dropFirstStrings(args, 2), server, sender);
        }
        else if (args[0].equals("difficulty") && args.length >= 2)
        {
            this.commandDifficulty(parseInt(args[1]), dropFirstStrings(args, 2), sender);
        }
        else if (args[0].equals("gamerule") && args.length >= 2)
        {
            this.commandGameRule(parseInt(args[1]), dropFirstStrings(args, 2), server, sender);
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

    private void commandWeather(int dimension, String[] args, ICommandSender sender) throws CommandException
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
                    int i = (int)(world.getWorldTime() % 24000L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    notifyCommandListener(sender, this, "jed.commands.time.query.daytime", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else if ("day".equals(args[1]))
                {
                    int i = (int)(world.getWorldTime() / 24000L % 2147483647L);
                    sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, i);
                    notifyCommandListener(sender, this, "jed.commands.time.query.day", Integer.valueOf(dimension), Integer.valueOf(i));
                }
                else if ("gametime".equals(args[1]))
                {
                    int i = (int)(world.getTotalWorldTime() % 2147483647L);
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

    private void commandDefaultGameType(int dimension, String[] args, MinecraftServer server, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            if (args.length == 0)
            {
                GameType type = world.getWorldInfo().getGameType();
                notifyCommandListener(sender, this, "jed.commands.defaultgamemode.print", type, Integer.valueOf(dimension));
                return;
            }

            GameType type = this.getGameModeFromCommand(sender, args[0]);

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

            notifyCommandListener(sender, this, "jed.commands.defaultgamemode.success", type, Integer.valueOf(dimension));
        }
        else
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }
    }

    private GameType getGameModeFromCommand(ICommandSender sender, String str) throws CommandException, NumberInvalidException
    {
        GameType gametype = GameType.parseGameTypeWithDefault(str, GameType.NOT_SET);
        return gametype == GameType.NOT_SET ? WorldSettings.getGameTypeById(parseInt(str, 0, GameType.values().length - 2)) : gametype;
    }

    private void commandDifficulty(int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            if (args.length == 0)
            {
                EnumDifficulty diff = world.getWorldInfo().getDifficulty();
                notifyCommandListener(sender, this, "jed.commands.difficulty.print", diff, Integer.valueOf(dimension));
                return;
            }

            EnumDifficulty diff = this.getDifficultyFromCommand(args[0]);

            if (world.getWorldInfo() instanceof WorldInfoJED)
            {
                ((WorldInfoJED) world.getWorldInfo()).setDifficultyJED(diff);
            }
            else
            {
                world.getWorldInfo().setDifficulty(diff);
            }

            notifyCommandListener(sender, this, "jed.commands.difficulty.success", diff, Integer.valueOf(dimension));
        }
        else
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }
    }

    private EnumDifficulty getDifficultyFromCommand(String str) throws CommandException, NumberInvalidException
    {
        if (str.equalsIgnoreCase("peaceful") || str.equalsIgnoreCase("p")) { return EnumDifficulty.PEACEFUL; }
        if (str.equalsIgnoreCase("easy")     || str.equalsIgnoreCase("e")) { return EnumDifficulty.EASY;     }
        if (str.equalsIgnoreCase("normal")   || str.equalsIgnoreCase("n")) { return EnumDifficulty.NORMAL;   }
        if (str.equalsIgnoreCase("hard")     || str.equalsIgnoreCase("h")) { return EnumDifficulty.HARD;     }

        return EnumDifficulty.getDifficultyEnum(parseInt(str, 0, 3));
    }

    private void commandGameRule(int dimension, String[] args, MinecraftServer server, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world != null)
        {
            GameRules rules = world.getGameRules();
            String key = args.length > 0 ? args[0] : "";

            if (args.length == 0)
            {
                sender.sendMessage(new TextComponentString(joinNiceString(rules.getRules())));
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
                String value = args.length > 1 ? buildString(args, 1) : "";

                if (rules.areSameType(key, GameRules.ValueType.BOOLEAN_VALUE) && value.equals("true") == false && value.equals("false") == false)
                {
                    throw new CommandException("commands.generic.boolean.invalid", value);
                }

                rules.setOrCreateGameRule(key, value);
                this.notifyGameRuleChange(rules, key, world);
                notifyCommandListener(sender, this, "jed.commands.gamerule.success", key, value, Integer.valueOf(dimension));
            }
        }
        else
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }
    }

    private void notifyGameRuleChange(GameRules rules, String key, World world)
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

    private GameRules getOverWorldGameRules(MinecraftServer server)
    {
        return server.worldServerForDimension(0).getGameRules();
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
