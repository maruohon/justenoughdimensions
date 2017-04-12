package fi.dy.masa.justenoughdimensions.command;

import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDDefaultGameType;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDDifficulty;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDGameRule;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDSetWorldSpawn;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDTime;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDWeather;
import fi.dy.masa.justenoughdimensions.command.utils.CommandJEDWorldBorder;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig.WorldInfoType;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;
import fi.dy.masa.justenoughdimensions.world.util.DimensionDump;
import fi.dy.masa.justenoughdimensions.world.util.WorldUtils;

public class CommandJED extends CommandBase
{

    @Override
    public String getName()
    {
        return "jed";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
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
                    "defaultgamemode",
                    "difficulty",
                    "dimbuilder",
                    "gamerule",
                    "listloadeddimensions",
                    "listregistereddimensions",
                    "register",
                    "reload",
                    "seed",
                    "setworldspawn",
                    "time",
                    "unloademptydimensions",
                    "unregister",
                    "unregister-remove",
                    "weather",
                    "worldborder"
                );
        }
        else if (args.length >= 2)
        {
            if (args[0].equals("dimbuilder"))
            {
                if (args.length == 2)
                {
                    return getListOfStringsMatchingLastWord(args,
                            "clear", "create-as", "dimtype", "list", "list-onetime", "read-from",
                            "remove", "remove-onetime", "save-as", "set", "set-onetime");
                }
                else
                {
                    return super.getTabCompletions(server, sender, args, pos);
                }
            }

            String cmd = args[0];
            args = dropFirstStrings(args, 1);

            try
            {
                parseInt(args[0]); // dimension
                args = dropFirstStrings(args, 1);
            }
            catch (NumberInvalidException e)
            {
                if (sender.getCommandSenderEntity() == null)
                {
                    return super.getTabCompletions(server, sender, args, pos);
                }
            }

            if (cmd.equals("weather") && args.length == 1)
            {
                return getListOfStringsMatchingLastWord(args, "clear", "rain", "thunder");
            }
            else if (cmd.equals("time"))
            {
                if (args.length == 1)
                {
                    return getListOfStringsMatchingLastWord(args, "add", "set", "query");
                }
                else if (args.length == 2 && args[0].equals("set"))
                {
                    return getListOfStringsMatchingLastWord(args, "day", "night");
                }
                else if (args.length == 2 && args[0].equals("query"))
                {
                    return getListOfStringsMatchingLastWord(args, "day", "daytime", "gametime");
                }
            }
            else if (cmd.equals("defaultgamemode") && args.length == 1)
            {
                return getListOfStringsMatchingLastWord(args, "survival", "creative", "adventure", "spectator");
            }
            else if (cmd.equals("difficulty") && args.length == 1)
            {
                return getListOfStringsMatchingLastWord(args, "peaceful", "easy", "normal", "hard");
            }
            else if (cmd.equals("gamerule"))
            {
                if (args.length == 1)
                {
                    return getListOfStringsMatchingLastWord(args, this.getOverWorldGameRules(server).getRules());
                }
                else if (args.length == 2 && this.getOverWorldGameRules(server).areSameType(args[0], GameRules.ValueType.BOOLEAN_VALUE))
                {
                    return getListOfStringsMatchingLastWord(args, "true", "false");
                }
            }
            else if (cmd.equals("setworldspawn") && args.length == 1)
            {
                return getListOfStringsMatchingLastWord(args, "query");
            }
            else if (cmd.equals("unloademptydimensions") && args.length == 1)
            {
                return getListOfStringsMatchingLastWord(args, "true");
            }
            else if (cmd.equals("worldborder"))
            {
                if (args.length == 1)
                {
                    return getListOfStringsMatchingLastWord(args, "set", "center", "damage", "warning", "add", "get");
                }
                else if (args[0].equals("damage") && args.length == 2)
                {
                    return getListOfStringsMatchingLastWord(args, "buffer", "amount");
                }
                else if (args[0].equals("warning") && args.length == 2)
                {
                    return getListOfStringsMatchingLastWord(args, "time", "distance");
                }
                else if (args[0].equals("center") && args.length <= 3)
                {
                    return getTabCompletionCoordinateXZ(args, 1, pos);
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
            throwUsage("generic");
        }

        String cmd = args[0];
        args = dropFirstStrings(args, 1);

        if (cmd.equals("reload"))
        {
            DimensionConfig.instance().readDimensionConfig();
            DimensionConfig.instance().registerDimensions();
            notifyCommandListener(sender, this, "jed.commands.reloaded");
        }
        else if (cmd.equals("listregistereddimensions"))
        {
            Integer[] dims = DimensionManager.getStaticDimensionIDs();
            String[] dimsStr = new String[dims.length];
            for (int i = 0; i < dimsStr.length; i++) { dimsStr[i] = String.valueOf(dims[i]); }

            for (String line : DimensionDump.getFormattedRegisteredDimensionsDump())
            {
                JustEnoughDimensions.logger.info(line);
            }

            sender.sendMessage(new TextComponentTranslation("jed.commands.listdims.list", String.join(", ", dimsStr)));
            sender.sendMessage(new TextComponentTranslation("jed.commands.info.output.printed.to.console.full"));
        }
        else if (cmd.equals("listloadeddimensions"))
        {
            for (String line : DimensionDump.getFormattedLoadedDimensionsDump())
            {
                JustEnoughDimensions.logger.info(line);
            }

            sender.sendMessage(new TextComponentTranslation("jed.commands.info.output.printed.to.console"));
        }
        else if (cmd.equals("unloademptydimensions"))
        {
            int count = WorldUtils.unloadEmptyDimensions(args.length == 1 && args[0].equals("true"));
            sender.sendMessage(new TextComponentTranslation("jed.commands.info.unloaded.dimensions", String.valueOf(count)));
        }
        else if (cmd.equals("dimbuilder"))
        {
            this.dimBuilder(args, sender);
        }
        else if (cmd.equals("register"))
        {
            this.register(args, sender);
        }
        else if (cmd.equals("unregister"))
        {
            if (args.length == 1)
            {
                int dimension = parseInt(args[0]);

                if (this.unregister(dimension))
                {
                    notifyCommandListener(sender, this, "jed.commands.unregister", Integer.valueOf(dimension));
                }
                else
                {
                    throwCommand("dimension.cannotunregisteroverworld");
                }
            }
            else
            {
                throw new WrongUsageException("/jed unregister <dimension id>");
            }
        }
        else if (cmd.equals("unregister-remove"))
        {
            if (args.length == 1)
            {
                int dimension = parseInt(args[0]);
                this.unregister(dimension);
                DimensionConfig.instance().removeDimensionAndSaveConfig(dimension);
                notifyCommandListener(sender, this, "jed.commands.unregister.remove", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("/jed unregister-remove <dimension id>");
            }
        }
        else if (cmd.equals("debug"))
        {
            World world = null;

            if (args.length == 1)
            {
                try
                {
                    int dim = parseInt(args[0]);
                    world = DimensionManager.getWorld(dim);
                }
                catch (Exception e) { }
            }

            if (world == null)
            {
                Entity ent = sender.getCommandSenderEntity();
                if (ent != null) { world = ent.getEntityWorld(); }
            }

            if (world != null)
            {
                IChunkProvider cp = world.getChunkProvider();
                JustEnoughDimensions.logger.info("============= JED DEBUG START ==========");
                JustEnoughDimensions.logger.info("DIM: {}", world.provider.getDimension());
                JustEnoughDimensions.logger.info("Seed: {}", world.getWorldInfo().getSeed());
                JustEnoughDimensions.logger.info("World {}", world.getClass().getName());
                WorldType type = world.getWorldInfo().getTerrainType();
                JustEnoughDimensions.logger.info("WorldType: '{}' - {}", type.getName(), type.getClass().getName());
                JustEnoughDimensions.logger.info("WorldProvider: {}", world.provider.getClass().getName());
                JustEnoughDimensions.logger.info("ChunkProvider: {}", cp.getClass().getName());
                JustEnoughDimensions.logger.info("ChunkProviderServer.chunkGenerator: {}",
                        ((cp instanceof ChunkProviderServer) ? ((ChunkProviderServer) cp).chunkGenerator.getClass().getName() : "null"));
                JustEnoughDimensions.logger.info("BiomeProvider: {}", world.getBiomeProvider().getClass().getName());
                if (world.getWorldInfo() instanceof WorldInfoJED)
                {
                    JustEnoughDimensions.logger.info("JED NBT tag: {}", ((WorldInfoJED) world.getWorldInfo()).getJEDTag().toString());
                }
                NBTTagCompound tag = world.getWorldInfo().cloneNBTCompound(new NBTTagCompound());
                tag.removeTag("JED");
                JustEnoughDimensions.logger.info("Vanilla level NBT: {}", tag.toString());
                JustEnoughDimensions.logger.info("============= JED DEBUG END ==========");

                sender.sendMessage(new TextComponentTranslation("jed.commands.info.output.printed.to.console"));
            }
        }
        else
        {
            Entity entity = sender.getCommandSenderEntity();
            boolean dimensionKnown = entity != null;
            int dimension = entity != null ? entity.getEntityWorld().provider.getDimension() : 0;

            if (args.length >= 1)
            {
                try
                {
                    dimension = parseInt(args[0]);
                    args = dropFirstStrings(args, 1);
                    dimensionKnown = true;
                }
                catch (NumberInvalidException e) { }
            }

            if (dimensionKnown == false)
            {
                throwUsage("generic");
            }

            if (cmd.equals("defaultgamemode"))
            {
                CommandJEDDefaultGameType.execute(this, dimension, args, server, sender);
            }
            else if (cmd.equals("difficulty"))
            {
                CommandJEDDifficulty.execute(this, dimension, args, sender);
            }
            else if (cmd.equals("gamerule"))
            {
                CommandJEDGameRule.execute(this, dimension, args, server, sender);
            }
            else if (cmd.equals("seed"))
            {
                this.commandSeed(dimension, sender);
            }
            else if (cmd.equals("setworldspawn"))
            {
                CommandJEDSetWorldSpawn.execute(this, dimension, args, sender);
            }
            else if (cmd.equals("time"))
            {
                CommandJEDTime.execute(this, dimension, args, sender);
            }
            else if (cmd.equals("weather"))
            {
                CommandJEDWeather.execute(this, dimension, args, sender);
            }
            else if (cmd.equals("worldborder"))
            {
                CommandJEDWorldBorder.execute(this, dimension, args, server, sender);
            }
            else
            {
                throwUsage("generic");
            }
        }
    }

    private void commandSeed(int dimension, ICommandSender sender) throws CommandException
    {
        World world = DimensionManager.getWorld(dimension);

        if (world == null)
        {
            throwNumber("dimension.notloaded", Integer.valueOf(dimension));
        }

        sender.sendMessage(new TextComponentString("DIM: " + dimension + " - Seed: " + world.getWorldInfo().getSeed()));
    }

    private GameRules getOverWorldGameRules(MinecraftServer server)
    {
        return server.worldServerForDimension(0).getGameRules();
    }

    private void register(String[] args, ICommandSender sender) throws CommandException
    {
        if (args.length == 1)
        {
            int dimension = parseInt(args[0]);
            String str = DimensionConfig.instance().registerDimensionFromConfig(dimension);
            notifyCommandListener(sender, this, "jed.commands.register.from.config", Integer.valueOf(dimension), str);
        }
        else if (args.length == 2 && args[1].equals("create"))
        {
            int dimension = parseInt(args[0]);
            String str = DimensionConfig.instance().registerNewDimension(dimension);
            notifyCommandListener(sender, this, "jed.commands.register.create.simple", Integer.valueOf(dimension), str);
        }
        else if (args.length == 5 || args.length == 6)
        {
            int dimension = parseInt(args[0]);
            boolean keepLoaded = Boolean.parseBoolean(args[3]);
            boolean override = args.length == 6 ? Boolean.parseBoolean(args[5]) : false;
            String str = DimensionConfig.instance().registerNewDimension(dimension, args[1], args[2], keepLoaded, args[4], override);
            notifyCommandListener(sender, this, "jed.commands.register.custom", Integer.valueOf(dimension), str);
        }
        else
        {
            throwUsage("register");
        }
    }

    private boolean unregister(int dimension) throws CommandException
    {
        if (DimensionManager.isDimensionRegistered(dimension))
        {
            if (DimensionManager.getWorld(dimension) == null)
            {
                if (dimension != 0)
                {
                    DimensionManager.unregisterDimension(dimension);
                    return true;
                }
            }
            else
            {
                throwNumber("dimension.loaded", Integer.valueOf(dimension));
            }
        }
        else
        {
            throwNumber("dimension.notregistered", Integer.valueOf(dimension));
        }

        return false;
    }

    private void dimBuilder(String[] args, ICommandSender sender) throws CommandException
    {
        if (args.length < 1)
        {
            this.dimBuilderPrintHelp(sender);
        }
        else if (args[0].equals("dimtype"))
        {
            if (args.length == 6)
            {
                DimensionConfig.instance().dimbuilderDimtype(parseInt(args[1]), args[2], args[3], args[4], args[5]);
                notifyCommandListener(sender, this, "jed.commands.dimbuilder.dimtype.success");
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder dimtype <DimensionType id> <name> <suffix> <keeploaded true/false> <worldprovidername>");
            }
        }
        else if (args[0].equals("clear"))
        {
            if (args.length == 1)
            {
                DimensionConfig.instance().dimbuilderClear();
                notifyCommandListener(sender, this, "jed.commands.dimbuilder.clear.success");
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder clear");
            }
        }
        else if (args[0].equals("set") || args[0].equals("set-onetime"))
        {
            if (args.length >= 3)
            {
                WorldInfoType type = args[0].equals("set-onetime") ? WorldInfoType.ONE_TIME : WorldInfoType.REGULAR;
                String[] valueParts = dropFirstStrings(args, 2);
                String value = String.join(" ", valueParts);
                DimensionConfig.instance().dimbuilderSet(args[1], value, type);
                notifyCommandListener(sender, this, "jed.commands.dimbuilder.set.success", args[1], value);
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder <set | set-onetime> <key> <value>");
            }
        }
        else if (args[0].equals("remove") || args[0].equals("remove-onetime"))
        {
            if (args.length >= 2)
            {
                WorldInfoType type = args[0].equals("remove-onetime") ? WorldInfoType.ONE_TIME : WorldInfoType.REGULAR;

                for (int i = 1; i < args.length; i++)
                {
                    if (DimensionConfig.instance().dimbuilderRemove(args[i], type))
                    {
                        notifyCommandListener(sender, this, "jed.commands.dimbuilder.remove.success", args[i]);
                    }
                    else
                    {
                        throwCommand("dimbuilder.remove.fail", args[i]);
                    }
                }
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder <remove | remove-onetime> <key> [key] ...");
            }
        }
        else if (args[0].equals("list") || args[0].equals("list-onetime"))
        {
            WorldInfoType type = args[0].equals("list-onetime") ? WorldInfoType.ONE_TIME : WorldInfoType.REGULAR;

            if (args.length > 1)
            {
                for (int i = 1; i < args.length; i++)
                {
                    DimensionConfig.instance().dimbuilderList(args[i], type, sender);
                }
            }
            else
            {
                DimensionConfig.instance().dimbuilderList(null, type, sender);
            }
        }
        else if (args[0].equals("read-from"))
        {
            if (args.length == 2)
            {
                int dimension = parseInt(args[1]);
                if (DimensionConfig.instance().dimbuilderReadFrom(dimension))
                {
                    notifyCommandListener(sender, this, "jed.commands.dimbuilder.read.from.success", Integer.valueOf(dimension));
                }
                else
                {
                    notifyCommandListener(sender, this, "jed.commands.dimbuilder.read.from.fail", Integer.valueOf(dimension));
                }
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder read-from <dimension id>");
            }
        }
        else if (args[0].equals("save-as"))
        {
            if (args.length == 2)
            {
                int dimension = parseInt(args[1]);
                DimensionConfig.instance().dimbuilderSaveAs(dimension);
                notifyCommandListener(sender, this, "jed.commands.dimbuilder.save.as.success", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder save-as <dimension id>");
            }
        }
        else if (args[0].equals("create-as"))
        {
            if (args.length == 2)
            {
                int dimension = parseInt(args[1]);
                DimensionConfig.instance().dimbuilderCreateAs(dimension);
                notifyCommandListener(sender, this, "jed.commands.dimbuilder.create.as.success", Integer.valueOf(dimension));
            }
            else
            {
                throw new WrongUsageException("/jed dimbuilder create-as <dimension id>");
            }
        }
        else
        {
            this.dimBuilderPrintHelp(sender);
        }
    }

    private void dimBuilderPrintHelp(ICommandSender sender)
    {
        sender.sendMessage(new TextComponentString("/jed dimbuilder clear"));
        sender.sendMessage(new TextComponentString("/jed dimbuilder create-as <dim id>"));
        sender.sendMessage(new TextComponentString("/jed dimbuilder dimtype <DimensionType ID> <name> <suffix> <keeploaded> <worldprovider>"));
        sender.sendMessage(new TextComponentString("/jed dimbuilder <list | list-onetime> [key1] [key2] ..."));
        sender.sendMessage(new TextComponentString("/jed dimbuilder <remove | remove-onetime> <key>"));
        sender.sendMessage(new TextComponentString("/jed dimbuilder read-from <dim id>"));
        sender.sendMessage(new TextComponentString("/jed dimbuilder save-as <dim id>"));
        sender.sendMessage(new TextComponentString("/jed dimbuilder <set | set-onetime> <key> <value which can have spaces>"));
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

    public static void throwUsage(String type, Object... params) throws CommandException
    {
        throw new WrongUsageException("jed.commands.usage." + type, params);
    }

    public static void throwNumber(String type, Object... params) throws CommandException
    {
        throw new NumberInvalidException("jed.commands.error." + type, params);
    }

    public static void throwCommand(String type, Object... params) throws CommandException
    {
        throw new CommandException("jed.commands.error." + type, params);
    }
}
