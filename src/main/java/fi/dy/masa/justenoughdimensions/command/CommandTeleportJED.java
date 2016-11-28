package fi.dy.masa.justenoughdimensions.command;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldServer;
import net.minecraft.world.end.DragonFightManager;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToAccessFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class CommandTeleportJED extends CommandBase
{
    public CommandTeleportJED()
    {
    }

    @Override
    public String getName()
    {
        return "tpj";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/tpj <to-player> or /tpj <player> <to-player> or /tpj <dimensionId> [x y z] [yaw] [pitch]";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 4;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] strArr, BlockPos pos)
    {
        if (strArr.length == 1 || strArr.length == 2)
        {
            return getListOfStringsMatchingLastWord(strArr, server.getOnlinePlayerNames());
        }

        return new ArrayList<String>();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if ((sender instanceof EntityPlayerMP) == false)
        {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length == 1 || args.length == 2)
        {
            if (args.length == 2)
            {
                player = server.getPlayerList().getPlayerByUsername(args[0]);
            }

            EntityPlayer otherPlayer = server.getPlayerList().getPlayerByUsername(args.length == 2 ? args[1] : args[0]);

            if (player != null && otherPlayer != null)
            {
                player.setPositionAndRotation(player.posX, player.posY, player.posZ, otherPlayer.rotationYaw, otherPlayer.rotationPitch);
                player.changeDimension(otherPlayer.getEntityWorld().provider.getDimension());
                player.setPositionAndUpdate(otherPlayer.posX, otherPlayer.posY, otherPlayer.posZ);
            }
            else if (args.length == 1)
            {
                int dimension = parseInt(args[0]);

                if (dimension != player.getEntityWorld().provider.getDimension() && DimensionManager.isDimensionRegistered(dimension))
                {
                    this.changeToDimension(player, dimension, server);
                    player.setPositionAndUpdate(player.posX, player.posY, player.posZ);
                }
            }
        }
        else if (args.length >= 4 && args.length <= 6)
        {
            int dimension = parseInt(args[0]);

            if (DimensionManager.isDimensionRegistered(dimension) == false)
            {
                throw new WrongUsageException("Not a valid dimension ID: " + dimension, new Object[0]);
            }

            Vec3d pos = player.getPositionVector();
            double x = parseCoordinate(pos.xCoord, args[1], true).getResult();
            double y = parseCoordinate(pos.yCoord, args[2], false).getResult();
            double z = parseCoordinate(pos.zCoord, args[3], true).getResult();
            float yaw   = args.length >= 5 ? (float) parseDouble(args[4]) : player.rotationYaw;
            float pitch = args.length >= 6 ? (float) parseDouble(args[5]) : player.rotationPitch;

            if (args.length >= 5)
            {
                player.setPositionAndRotation(x, y, z, yaw, pitch);
            }

            if (dimension != player.getEntityWorld().provider.getDimension())
            {
                this.changeToDimension(player, dimension, x, yaw, z, server);
                player.setPositionAndUpdate(x, y, z);
            }
            else
            {
                player.setPositionAndUpdate(x, y, z);
            }
        }
        else
        {
            sender.sendMessage(new TextComponentString("Currently in dimension " + player.getEntityWorld().provider.getDimension()));
            throw new WrongUsageException("Usage: '" + this.getUsage(sender) + "'", new Object[0]);
        }
    }

    private void changeToDimension(EntityPlayerMP player, int dimension, MinecraftServer server)
    {
        WorldServer world = server.worldServerForDimension(dimension);
        BlockPos spawn = world.getSpawnPoint();
        this.changeToDimension(player, dimension, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, server);
    }

    private void changeToDimension(EntityPlayerMP player, int dimension, double x, double y, double z, MinecraftServer server)
    {
        WorldServer world = server.worldServerForDimension(dimension);
        server.getPlayerList().transferPlayerToDimension(player, dimension, new DummyTeleporter(world));
        player.setPositionAndUpdate(x, y, z);

        // Teleporting FROM The End
        if (player.getEntityWorld().provider.getDimension() == 1)
        {
            player.setPositionAndUpdate(x, y, z);
            world.spawnEntity(player);
            world.updateEntityWithOptionalForce(player, false);
            this.removeDragonBossBarHack(player, (WorldServer) player.getEntityWorld());
        }
    }

    private void removeDragonBossBarHack(EntityPlayerMP player, WorldServer worldSrc)
    {
        // FIXME 1.9 - Somewhat ugly way to clear the Boss Info stuff when teleporting FROM The End
        if (worldSrc.provider instanceof WorldProviderEnd)
        {
            DragonFightManager manager = ((WorldProviderEnd) worldSrc.provider).getDragonFightManager();

            if (manager != null)
            {
                try
                {
                    BossInfoServer bossInfo = ReflectionHelper.getPrivateValue(DragonFightManager.class, manager, "field_186109_c", "bossInfo");
                    if (bossInfo != null)
                    {
                        bossInfo.removePlayer(player);
                    }
                }
                catch (UnableToAccessFieldException e)
                {
                    JustEnoughDimensions.logger.warn("tpj: Failed to get DragonFightManager#bossInfo");
                }
            }
        }
    }
    private static class DummyTeleporter extends Teleporter
    {
        public DummyTeleporter(WorldServer worldIn)
        {
            super(worldIn);
        }

        @Override
        public boolean makePortal(Entity entityIn)
        {
            return true;
        }

        @Override
        public boolean placeInExistingPortal(Entity entityIn, float rotationYaw)
        {
            return true;
        }

        @Override
        public void placeInPortal(Entity entityIn, float rotationYaw)
        {
        }
    }
}
