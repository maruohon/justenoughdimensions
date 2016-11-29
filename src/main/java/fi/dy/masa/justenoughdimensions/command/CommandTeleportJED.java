package fi.dy.masa.justenoughdimensions.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldServer;
import net.minecraft.world.end.DragonFightManager;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToAccessFieldException;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindMethodException;
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
        return "/tpj <to-entity> or " +
               "/tpj <entity> <to-entity> or " +
               "/tpj <dimensionId> [x y z] [yaw] [pitch] or " +
               "/tpj <entity> <dimensionId> [x y z] [yaw] [pitch]";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 4;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index)
    {
        return index == 0;
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
        CommandParser parser = CommandParser.parse(this, server, sender, args);
        parser.execute(this, sender, server);
    }



    private static class CommandParser
    {
        private CommandVariant variant = CommandVariant.INVALID;
        private Entity target;
        private Entity destEntity;
        private int dimension;
        private Vec3d destPos;
        private float yaw;
        private float pitch;
        private boolean hasPosition;

        private CommandParser()
        {
        }

        private CommandParser(Entity target, Entity destEntity)
        {
            this.variant = CommandVariant.ENTITY_TO_ENTITY;
            this.target = target;
            this.destEntity = destEntity;
        }

        private CommandParser(Entity target, int dimension)
        {
            this.variant = CommandVariant.ENTITY_TO_DIMENSION;
            this.target = target;
            this.dimension = dimension;
        }

        private CommandParser(Entity target, int dimension, Vec3d pos, float yaw, float pitch)
        {
            this.variant = CommandVariant.ENTITY_TO_DIMENSION;
            this.target = target;
            this.dimension = dimension;
            this.destPos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.hasPosition = true;
        }

        public static CommandParser parse(CommandTeleportJED cmd, MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
        {
            if (args.length == 0)
            {
                if ((sender.getCommandSenderEntity() instanceof Entity))
                {
                    sender.sendMessage(new TextComponentString("Currently in dimension " + sender.getCommandSenderEntity().getEntityWorld().provider.getDimension()));
                }

                throw new WrongUsageException(cmd.getUsage(sender));
            }

            // <to-entity> OR <dimensionId>
            if (args.length == 1)
            {
                Entity entityDest = null;

                try
                {
                    entityDest = getEntity(server, sender, args[0]);
                }
                catch (Exception e) { }

                // Used from the console and an invalid entity selector for the first entity
                if (entityDest == null && (sender.getCommandSenderEntity() instanceof Entity) == false)
                {
                    throw new WrongUsageException("Invalid entity '" + args[0] + "'");
                }

                if (entityDest != null)
                {
                    return new CommandParser(sender.getCommandSenderEntity(), entityDest);
                }
                else
                {
                    int dimension = parseInt(args[0]);
                    return new CommandParser(sender.getCommandSenderEntity(), dimension);
                }
            }

            // args.length >= 2 at this point
            Entity target = null;
            int dimension = 0;
            int argIndex = 0;

            try
            {
                target = getEntity(server, sender, args[argIndex++]);
            }
            catch (Exception e)
            {
                if (sender.getCommandSenderEntity() == null)
                {
                    throw new WrongUsageException("No target entity specified");
                }

                argIndex--;
                target = sender.getCommandSenderEntity();
            }

            // <entity> <to-entity>
            if (argIndex == 1 && args.length == 2)
            {
                try
                {
                    Entity destEntity = getEntity(server, sender, args[1]);
                    return new CommandParser(target, destEntity);
                }
                // The second argument is not an entity, but possibly the dimension
                catch (Exception e) { }
            }

            if (args.length >= (argIndex + 1))
            {
                dimension = parseInt(args[argIndex++]);
            }

            if (args.length >= (argIndex + 3))
            {
                Vec3d pos = target.getPositionVector();
                double x = parseCoordinate(pos.xCoord, args[argIndex++], true).getResult();
                double y = parseCoordinate(pos.yCoord, args[argIndex++], false).getResult();
                double z = parseCoordinate(pos.zCoord, args[argIndex++], true).getResult();
                float yaw = target.rotationYaw;
                float pitch = target.rotationPitch;

                if (args.length >= (argIndex + 1))
                {
                    yaw = (float) parseDouble(args[argIndex++]);
                }

                if (args.length >= (argIndex + 1))
                {
                    pitch = (float) parseDouble(args[argIndex++]);
                }

                if (args.length > argIndex)
                {
                    throw new WrongUsageException(cmd.getUsage(sender));
                }

                return new CommandParser(target, dimension, new Vec3d(x, y, z), yaw, pitch);
            }
            else if (args.length > argIndex)
            {
                throw new WrongUsageException(cmd.getUsage(sender));
            }

            return new CommandParser(target, dimension);
        }

        public void execute(CommandTeleportJED cmd, ICommandSender sender, MinecraftServer server) throws CommandException
        {
            if (this.variant == CommandVariant.INVALID)
            {
                return;
            }

            // TODO hook up the mounted entity TP code from Ender Utilities?
            this.target.dismountRidingEntity();
            this.target.removePassengers();

            Entity entity = null;

            if (this.variant == CommandVariant.ENTITY_TO_ENTITY)
            {
                this.teleportToEntity(this.target, this.destEntity, server);
                entity = this.destEntity;
            }
            else if (this.variant == CommandVariant.ENTITY_TO_DIMENSION)
            {
                entity = this.teleportToDimension(this.target, this.dimension, server);
            }

            if (entity != null)
            {
                notifyCommandListener(sender, cmd, "commands.teleport.success.coordinates",
                        new Object[] {
                                this.target.getName(),
                                Double.valueOf(entity.posX),
                                Double.valueOf(entity.posY),
                                Double.valueOf(entity.posZ)
                            });
            }
        }

        private void teleportToEntity(Entity target, Entity destEntity, MinecraftServer server) throws CommandException
        {
            int dimTgt = target.getEntityWorld().provider.getDimension();
            int dimDst = destEntity.getEntityWorld().provider.getDimension();

            if (dimTgt != dimDst)
            {
                target = this.changeToDimension(target, dimDst, false, server);
            }

            this.teleportEntityTo(target, destEntity.getPositionVector(), destEntity.rotationYaw, destEntity.rotationPitch);
        }

        private Entity teleportToDimension(Entity entity, int dimension, MinecraftServer server) throws CommandException
        {
            if (DimensionManager.isDimensionRegistered(dimension) == false)
            {
                throw new NumberInvalidException("Not a valid dimension ID: " + dimension, new Object[0]);
            }

            if (entity.getEntityWorld().provider.getDimension() != dimension)
            {
                return this.changeToDimension(entity, dimension, this.hasPosition == false, server);
            }
            else
            {
                if (this.hasPosition)
                {
                    this.teleportEntityTo(entity, this.destPos, this.yaw, this.pitch);
                }
                else
                {
                    this.teleportEntityTo(entity, entity.getEntityWorld().getSpawnPoint(), entity.rotationYaw, entity.rotationPitch);
                }
            }

            return entity;
        }

        private void teleportEntityTo(Entity entity, BlockPos pos, float yaw, float pitch)
        {
            this.teleportEntityTo(entity, new Vec3d(pos), yaw, pitch);
        }

        private void teleportEntityTo(Entity entity, Vec3d pos, float yaw, float pitch)
        {
            double x = pos.xCoord;
            double y = pos.yCoord;
            double z = pos.zCoord;

            entity.setLocationAndAngles(x, y, z, yaw, pitch);
            entity.setPositionAndUpdate(x, y, z);
        }

        private Entity changeToDimension(Entity entity, int dimension, boolean useSpawnPoint, MinecraftServer server) throws CommandException
        {
            WorldServer worldDst = server.worldServerForDimension(dimension);
            if (worldDst == null)
            {
                throw new WrongUsageException("Unable to load world for dimension " + dimension);
            }

            double x = entity.posX;
            double y = entity.posY;
            double z = entity.posZ;
            float yaw = entity.rotationYaw;
            float pitch = entity.rotationPitch;

            if (this.hasPosition)
            {
                x = this.destPos.xCoord;
                y = this.destPos.yCoord;
                z = this.destPos.zCoord;
                yaw = this.yaw;
                pitch = this.pitch;
            }
            else if (useSpawnPoint)
            {
                BlockPos spawn = worldDst.provider instanceof WorldProviderEnd ? worldDst.getSpawnCoordinate() : worldDst.getSpawnPoint();

                if (spawn != null)
                {
                    x = spawn.getX() + 0.5;
                    y = spawn.getY();
                    z = spawn.getZ() + 0.5;
                }
            }

            if (entity instanceof EntityPlayerMP)
            {
                EntityPlayerMP player = (EntityPlayerMP) entity;
                int oldDim = player.getEntityWorld().provider.getDimension();
                // Set the yaw and pitch at this point
                entity.setLocationAndAngles(x, y, z, yaw, pitch);
                server.getPlayerList().transferPlayerToDimension(player, dimension, new DummyTeleporter(worldDst));
                player.setPositionAndUpdate(x, y, z);

                // Teleporting FROM The End
                if (oldDim == 1)
                {
                    player.setPositionAndUpdate(x, y, z);
                    worldDst.spawnEntity(player);
                    worldDst.updateEntityWithOptionalForce(player, false);
                    this.removeDragonBossBarHack(player, (WorldServer) player.getEntityWorld());
                }
            }
            else
            {
                int dimSrc = entity.getEntityWorld().provider.getDimension();
                WorldServer worldSrc = server.worldServerForDimension(dimSrc);
                World worldEntity = entity.getEntityWorld();

                worldEntity.removeEntity(entity);
                entity.isDead = false;
                worldEntity.updateEntityWithOptionalForce(entity, false);

                Entity entityNew = EntityList.newEntity(entity.getClass(), worldDst);

                if (entityNew != null)
                {
                    copyDataFromOld(entityNew, entity);
                    entityNew.setLocationAndAngles(x, y, z, yaw, pitch);

                    boolean flag = entityNew.forceSpawn;
                    entityNew.forceSpawn = true;
                    worldDst.spawnEntity(entityNew);
                    entityNew.forceSpawn = flag;

                    worldDst.updateEntityWithOptionalForce(entityNew, false);
                    entity.isDead = true;

                    worldSrc.resetUpdateEntityTick();
                    worldDst.resetUpdateEntityTick();
                }

                entity = entityNew;
            }

            return entity;
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
    }

    public static void copyDataFromOld(Entity target, Entity old)
    {
        Method method = ReflectionHelper.findMethod(Entity.class, target, new String[] {"func_180432_n", "copyDataFromOld", "a"}, Entity.class);
        try
        {
            method.invoke(target, old);
        }
        catch (UnableToFindMethodException e)
        {
            JustEnoughDimensions.logger.error("Error while trying reflect Entity.copyDataFromOld() (UnableToFindMethodException)");
            e.printStackTrace();
        }
        catch (InvocationTargetException e)
        {
            JustEnoughDimensions.logger.error("Error while trying reflect Entity.copyDataFromOld() (InvocationTargetException)");
            e.printStackTrace();
        }
        catch (IllegalAccessException e)
        {
            JustEnoughDimensions.logger.error("Error while trying reflect Entity.copyDataFromOld() (IllegalAccessException)");
            e.printStackTrace();
        }
    }

    private enum CommandVariant
    {
        INVALID,
        ENTITY_TO_ENTITY,
        ENTITY_TO_DIMENSION;
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
