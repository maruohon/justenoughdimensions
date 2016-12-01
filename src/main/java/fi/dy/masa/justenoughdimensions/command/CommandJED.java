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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
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
        return  "jed.commands.usage.generic";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args,
                    "debug",
                    "defaultgametype",
                    "difficulty",
                    "gamerule",
                    "register",
                    "reload",
                    "time",
                    "unregister",
                    "unregister-remove",
                    "weather",
                    "worldborder"
                );
        }
        else if (args.length >= 2)
        {
            String cmd = args[0];
            int trim = 2;
            int len = args.length;

            try { parseInt(args[1]); }
            catch (NumberInvalidException e)
            {
                if (sender.getCommandSenderEntity() == null)
                {
                    return super.getTabCompletions(server, sender, args, pos);
                }
                trim = 1;
            }
            len -= trim;

            if (cmd.equals("weather"))
            {
                return getListOfStringsMatchingLastWord(args, "clear", "rain", "thunder");
            }
            else if (cmd.equals("time"))
            {
                if (len == 1)
                {
                    return getListOfStringsMatchingLastWord(args, "add", "set", "query");
                }
                else if (len == 2 && args[args.length - 2].equals("set"))
                {
                    return getListOfStringsMatchingLastWord(args, "day", "night");
                }
                else if (len == 2 && args[args.length - 2].equals("query"))
                {
                    return getListOfStringsMatchingLastWord(args, "day", "daytime", "gametime");
                }
            }
            else if (cmd.equals("defaultgametype") && len == 1)
            {
                return getListOfStringsMatchingLastWord(args, "survival", "creative", "adventure", "spectator");
            }
            else if (cmd.equals("difficulty") && len == 1)
            {
                return getListOfStringsMatchingLastWord(args, "peaceful", "easy", "normal", "hard");
            }
            else if (cmd.equals("gamerule"))
            {
                if (len == 1)
                {
                    return getListOfStringsMatchingLastWord(args, this.getOverWorldGameRules(server).getRules());
                }
                else if (len == 2 && this.getOverWorldGameRules(server).areSameType(args[args.length - 2], GameRules.ValueType.BOOLEAN_VALUE))
                {
                    return getListOfStringsMatchingLastWord(args, "true", "false");
                }
            }
            else if (cmd.equals("worldborder"))
            {
                if (len == 1)
                {
                    return getListOfStringsMatchingLastWord(args, "set", "center", "damage", "warning", "add", "get");
                }
                else if (len >= 2)
                {
                    if (args[args.length - 2].equals("damage"))
                    {
                        return getListOfStringsMatchingLastWord(args, "buffer", "amount");
                    }
                    else if (args[args.length - 2].equals("warning"))
                    {
                        return getListOfStringsMatchingLastWord(args, "time", "distance");
                    }
                    else if (args[args.length - len].equals("center") && len <= 3)
                    {
                        return getTabCompletionCoordinateXZ(args, trim + 1, pos);
                    }
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

        String cmd = args[0];

        if (cmd.equals("reload"))
        {
            DimensionConfig.instance().readDimensionConfig();
            DimensionConfig.instance().registerDimensions();
            notifyCommandListener(sender, this, "jed.commands.reloaded");
        }
        else if (cmd.equals("unregister"))
        {
            if (args.length == 2)
            {
                int dimension = parseInt(args[1]);
                this.unregister(dimension);
                notifyCommandListener(sender, this, "jed.commands.unregister", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("jed.commands.usage.unregister");
            }
        }
        else if (cmd.equals("unregister-remove"))
        {
            if (args.length == 2)
            {
                int dimension = parseInt(args[1]);
                this.unregister(dimension);
                DimensionConfig.instance().removeDimensionAndSaveConfig(dimension);
                notifyCommandListener(sender, this, "jed.commands.unregister.remove", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("jed.commands.usage.unregister.remove");
            }
        }
        else if (cmd.equals("register"))
        {
            if (args.length == 2)
            {
                int dimension = parseInt(args[1]);
                DimensionConfig.instance().registerNewDimension(dimension);
                notifyCommandListener(sender, this, "jed.commands.register.default", Integer.valueOf(dimension));
            }
            else if (args.length == 6 || args.length == 7)
            {
                int dimension = parseInt(args[1]);
                boolean keepLoaded = Boolean.parseBoolean(args[4]);
                boolean override = args.length == 7 ? Boolean.parseBoolean(args[6]) : false;
                DimensionConfig.instance().registerNewDimension(dimension, args[2], args[3], keepLoaded, args[5], override);
                notifyCommandListener(sender, this, "jed.commands.register.custom", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("jed.commands.usage.register");
            }
        }
        else if (cmd.equals("debug"))
        {
            World world = null;
            try { int dim = parseInt(args[1]); world = DimensionManager.getWorld(dim); }
            catch (Exception e) { Entity ent = sender.getCommandSenderEntity(); if (ent != null) world = ent.getEntityWorld(); }

            if (world != null)
            {
                IChunkProvider cp = world.getChunkProvider();
                JustEnoughDimensions.logger.info("============= JED DEBUG START ==========");
                JustEnoughDimensions.logger.info("DIM: {}", world.provider.getDimension());
                JustEnoughDimensions.logger.info("World {}", world.getClass().getName());
                WorldType type = world.getWorldInfo().getTerrainType();
                JustEnoughDimensions.logger.info("WorldType: {} - {}", type.getName(), type.getClass().getName());
                JustEnoughDimensions.logger.info("WorldProvider: {}", world.provider.getClass().getName());
                JustEnoughDimensions.logger.info("ChunkProvider: {}", cp.getClass().getName());
                JustEnoughDimensions.logger.info("ChunkProviderServer.chunkGenerator: {}",
                        ((cp instanceof ChunkProviderServer) ? ((ChunkProviderServer) cp).chunkGenerator.getClass().getName() : "null"));
                JustEnoughDimensions.logger.info("BiomeProvider: {}", world.getBiomeProvider().getClass().getName());
                JustEnoughDimensions.logger.info("============= JED DEBUG END ==========");

                sender.sendMessage(new TextComponentString("Debug output printed to console"));
            }
        }
        else
        {
            Entity entity = sender.getCommandSenderEntity();
            int dimension = entity != null ? entity.getEntityWorld().provider.getDimension() : 0;
            int trim = args.length >= 2 ? 2 : 1;

            if (args.length >= 2)
            {
                try { dimension = parseInt(args[1]); }
                catch (NumberInvalidException e)
                {
                    if (entity == null) { throw new WrongUsageException("jed.commands.usage.generic"); }
                    trim = 1;
                }
            }

            args = dropFirstStrings(args, trim);

            if (cmd.equals("weather"))
            {
                this.commandWeather(dimension, args, sender);
            }
            else if (cmd.equals("time"))
            {
                this.commandTime(dimension, args, sender);
            }
            else if (cmd.equals("defaultgametype"))
            {
                this.commandDefaultGameType(dimension, args, server, sender);
            }
            else if (cmd.equals("difficulty"))
            {
                this.commandDifficulty(dimension, args, sender);
            }
            else if (cmd.equals("gamerule"))
            {
                this.commandGameRule(dimension, args, server, sender);
            }
            else if (cmd.equals("worldborder"))
            {
                this.commandWorldBorder(dimension, args, server, sender);
            }
            else
            {
                throw new WrongUsageException("jed.commands.usage.generic");
            }
        }
    }

    private void commandWeather(int dimension, String[] args, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }

        if (args.length >= 1 && args.length <= 2)
        {
            int time = (300 + (new Random()).nextInt(600)) * 20;

            if (args.length >= 2)
            {
                time = parseInt(args[1], -10000000, 10000000) * 20;
            }

            String cmd = args[0];
            WorldInfo worldinfo = world.getWorldInfo();

            if (cmd.equals("clear"))
            {
                worldinfo.setCleanWeatherTime(time);
                worldinfo.setRainTime(0);
                worldinfo.setThunderTime(0);
                worldinfo.setRaining(false);
                worldinfo.setThundering(false);
                notifyCommandListener(sender, this, "jed.commands.weather.clear", Integer.valueOf(dimension));
            }
            else if (cmd.equals("rain"))
            {
                worldinfo.setCleanWeatherTime(0);
                worldinfo.setRainTime(time);
                worldinfo.setThunderTime(time);
                worldinfo.setRaining(true);
                worldinfo.setThundering(false);
                notifyCommandListener(sender, this, "jed.commands.weather.rain", Integer.valueOf(dimension));
            }
            else if (cmd.equals("thunder"))
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
                throw new WrongUsageException("jed.commands.usage.weather");
            }
        }
        else
        {
            throw new WrongUsageException("jed.commands.usage.weather");
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
            String cmd = args[0];

            if (cmd.equals("set"))
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
            else if (cmd.equals("add"))
            {
                int amount = parseInt(args[1], -24000, 24000);
                world.setWorldTime(world.getWorldTime() + amount);
                notifyCommandListener(sender, this, "jed.commands.time.add", Integer.valueOf(amount), Integer.valueOf(dimension));
            }
            else if (cmd.equals("query"))
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
                    throw new WrongUsageException("jed.commands.usage.time");
                }
            }
        }
        else
        {
            throw new WrongUsageException("jed.commands.usage.time");
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

    private void commandWorldBorder(int dimension, String[] args, MinecraftServer server, ICommandSender sender) throws CommandException
    {
        if (args.length < 1)
        {
            throw new WrongUsageException("jed.commands.usage.worldborder");
        }

        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            throw new NumberInvalidException("jed.commands.error.dimension.notloaded", Integer.valueOf(dimension));
        }

        String cmd = args[0];
        WorldBorder border = world.getWorldBorder();

        if (cmd.equals("set"))
        {
            if (args.length != 2 && args.length != 3)
            {
                throw new WrongUsageException("jed.commands.usage.worldborder.set");
            }

            double oldSize = border.getTargetSize();
            double newSize = parseDouble(args[1], 1.0D, 6.0E7D);
            long i = args.length > 2 ? parseLong(args[2], 0L, 9223372036854775L) * 1000L : 0L;

            if (i > 0L)
            {
                border.setTransition(oldSize, newSize, i);

                if (oldSize > newSize)
                {
                    notifyCommandListener(sender, this, "jed.commands.worldborder.setslowly.shrink.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(i / 1000L));
                }
                else
                {
                    notifyCommandListener(sender, this, "jed.commands.worldborder.setslowly.grow.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(i / 1000L));
                }
            }
            else
            {
                border.setTransition(newSize);
                notifyCommandListener(sender, this, "jed.commands.worldborder.set.success",
                        Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize));
            }
        }
        else if (cmd.equals("add"))
        {
            if (args.length != 2 && args.length != 3)
            {
                throw new WrongUsageException("jed.commands.usage.worldborder.add");
            }

            double oldSize = border.getDiameter();
            double newSize = oldSize + parseDouble(args[1], -oldSize, 6.0E7D - oldSize);
            long time = border.getTimeUntilTarget() + (args.length > 2 ? parseLong(args[2], 0L, 9223372036854775L) * 1000L : 0L);

            if (time > 0L)
            {
                border.setTransition(oldSize, newSize, time);

                if (oldSize > newSize)
                {
                    notifyCommandListener(sender, this, "jed.commands.worldborder.setslowly.shrink.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(time / 1000L));
                }
                else
                {
                    notifyCommandListener(sender, this, "jed.commands.worldborder.setslowly.grow.success",
                            Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize), Long.toString(time / 1000L));
                }
            }
            else
            {
                border.setTransition(newSize);
                notifyCommandListener(sender, this, "jed.commands.worldborder.set.success",
                        Integer.valueOf(dimension), String.format("%.1f", newSize), String.format("%.1f", oldSize));
            }
        }
        else if (cmd.equals("center"))
        {
            if (args.length != 3)
            {
                throw new WrongUsageException("jed.commands.usage.worldborder.center");
            }

            BlockPos blockpos = sender.getPosition();
            double centerX = parseDouble(blockpos.getX() + 0.5D, args[1], true);
            double centerZ = parseDouble(blockpos.getZ() + 0.5D, args[2], true);
            border.setCenter(centerX, centerZ);
            notifyCommandListener(sender, this, "jed.commands.worldborder.center.success",
                    Integer.valueOf(dimension), Double.valueOf(centerX), Double.valueOf(centerZ));
        }
        else if (cmd.equals("damage"))
        {
            if (args.length >= 2 && args[1].equals("buffer"))
            {
                if (args.length != 3)
                {
                    throw new WrongUsageException("jed.commands.usage.worldborder.damage.buffer");
                }

                double bufferSize = parseDouble(args[2], 0.0D);
                double oldSize = border.getDamageBuffer();
                border.setDamageBuffer(bufferSize);
                notifyCommandListener(sender, this, "jed.commands.worldborder.damage.buffer.success",
                        Integer.valueOf(dimension), String.format("%.1f", bufferSize), String.format("%.1f", oldSize));
            }
            else if (args.length >= 2 && args[1].equals("amount"))
            {
                if (args.length != 3)
                {
                    throw new WrongUsageException("jed.commands.usage.worldborder.damage.amount");
                }

                double damage = parseDouble(args[2], 0.0D);
                double oldDamage = border.getDamageAmount();
                border.setDamageAmount(damage);
                notifyCommandListener(sender, this, "jed.commands.worldborder.damage.amount.success",
                        Integer.valueOf(dimension), String.format("%.2f", damage), String.format("%.2f", oldDamage));
            }
            else
            {
                throw new WrongUsageException("jed.commands.usage.worldborder.damage");
            }
        }
        else if (cmd.equals("warning"))
        {
            if (args.length >= 2 && args[1].equals("time"))
            {
                if (args.length != 3)
                {
                    throw new WrongUsageException("jed.commands.usage.worldborder.warning.time");
                }

                int time = parseInt(args[2], 0);
                int oldTime = border.getWarningTime();
                border.setWarningTime(time);
                notifyCommandListener(sender, this, "jed.commands.worldborder.warning.time.success",
                        Integer.valueOf(dimension), Integer.valueOf(time), Integer.valueOf(oldTime));
            }
            else if (args.length >= 2 && args[1].equals("distance"))
            {
                if (args.length != 3)
                {
                    throw new WrongUsageException("jed.commands.usage.worldborder.warning.distance");
                }

                int distance = parseInt(args[2], 0);
                int oldDistance = border.getWarningDistance();
                border.setWarningDistance(distance);
                notifyCommandListener(sender, this, "jed.commands.worldborder.warning.distance.success",
                        Integer.valueOf(dimension), Integer.valueOf(distance), Integer.valueOf(oldDistance));
            }
            else
            {
                throw new WrongUsageException("jed.commands.usage.worldborder.warning");
            }
        }
        else if (cmd.equals("get"))
        {
            double diameter = border.getDiameter();
            sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, MathHelper.floor(diameter + 0.5D));
            sender.sendMessage(new TextComponentTranslation("jed.commands.worldborder.get.success",
                    Integer.valueOf(dimension), String.format("%.0f", diameter)));
        }
        else
        {
            throw new WrongUsageException("jed.commands.worldborder.usage");
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
