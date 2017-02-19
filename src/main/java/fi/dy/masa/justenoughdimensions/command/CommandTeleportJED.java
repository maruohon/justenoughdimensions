package fi.dy.masa.justenoughdimensions.command;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.end.DragonFightManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToAccessFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.util.MethodHandleUtils;
import fi.dy.masa.justenoughdimensions.util.MethodHandleUtils.UnableToFindMethodHandleException;
import fi.dy.masa.justenoughdimensions.world.WorldInfoJED;

public class CommandTeleportJED extends CommandBase
{
    private MethodHandle methodHandle_Entity_copyDataFromOld;

    public CommandTeleportJED()
    {
        try
        {
            this.methodHandle_Entity_copyDataFromOld = MethodHandleUtils.getMethodHandleVirtual(
                    Entity.class, new String[] { "func_180432_n", "copyDataFromOld" }, Entity.class);
        }
        catch (UnableToFindMethodHandleException e)
        {
            JustEnoughDimensions.logger.error("CommandTeleportJED: Failed to get MethodHandle for Entity#copyDataFromOld()", e);
        }
    }

    @Override
    public String getName()
    {
        return "tpj";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "jed.commands.usage.tp";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
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
        TeleportData data = this.parseArguments(server, sender, args);
        Entity entity = this.teleportEntityToLocation(data.getEntity(), data, server);

        if (entity != null)
        {
            notifyCommandListener(sender, this, "jed.commands.teleport.success.coordinates",
                    entity.getName(),
                    String.format("%.1f", entity.posX),
                    String.format("%.1f", entity.posY),
                    String.format("%.1f", entity.posZ),
                    Integer.valueOf(entity.getEntityWorld().provider.getDimension()));
        }
    }

    private TeleportData parseArguments(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length == 0)
        {
            if ((sender.getCommandSenderEntity() instanceof Entity))
            {
                int dim = sender.getCommandSenderEntity().getEntityWorld().provider.getDimension();
                sender.sendMessage(new TextComponentTranslation("jed.commands.info.current.dimension", Integer.valueOf(dim)));
            }

            CommandJED.throwUsage("tp");
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
                CommandJED.throwUsage("invalid.entity", args[0]);
            }

            if (entityDest != null)
            {
                return new TeleportData(sender.getCommandSenderEntity(), entityDest);
            }
            else
            {
                return new TeleportData(sender.getCommandSenderEntity(), parseInt(args[0]), true, server);
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
                CommandJED.throwUsage("no.targetentity");
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
                return new TeleportData(target, destEntity);
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
                CommandJED.throwUsage("tp");
            }

            return new TeleportData(target, dimension, x, y, z, yaw, pitch);
        }
        else if (args.length > argIndex)
        {
            CommandJED.throwUsage("tp");
        }

        return new TeleportData(target, dimension, true, server);
    }

    private Entity teleportEntityToLocation(Entity entity, TeleportData data, MinecraftServer server) throws CommandException
    {
        // TODO hook up the mounted entity TP code from Ender Utilities?
        entity.dismountRidingEntity();
        entity.removePassengers();

        if (entity.getEntityWorld().provider.getDimension() != data.getDimension())
        {
            return this.teleportEntityToDimension(entity, data, server);
        }
        else
        {
            return this.teleportEntityInsideSameDimension(entity, data);
        }
    }

    private Entity teleportEntityInsideSameDimension(Entity entity, TeleportData data)
    {
        Vec3d pos = data.getPosition(entity.getEntityWorld());

        // Load the chunk first
        entity.getEntityWorld().getChunkFromChunkCoords((int) Math.floor(pos.xCoord / 16D), (int) Math.floor(pos.zCoord / 16D));

        entity.setLocationAndAngles(pos.xCoord, pos.yCoord, pos.zCoord, data.getYaw(), data.getPitch());
        entity.setPositionAndUpdate(pos.xCoord, pos.yCoord, pos.zCoord);
        return entity;
    }

    private Entity teleportEntityToDimension(Entity entity, TeleportData data, MinecraftServer server) throws CommandException
    {
        WorldServer worldDst = server.worldServerForDimension(data.getDimension());

        if (worldDst == null)
        {
            CommandJED.throwNumber("unable.to.load.world", Integer.valueOf(data.getDimension()));
        }

        Vec3d pos = data.getPosition(worldDst);
        double x = pos.xCoord;
        double y = pos.yCoord;
        double z = pos.zCoord;

        // Load the chunk first
        worldDst.getChunkFromChunkCoords((int) Math.floor(x / 16D), (int) Math.floor(z / 16D));

        if (entity instanceof EntityPlayerMP)
        {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            World worldOld = player.getEntityWorld();
            TeleporterJED teleporter = new TeleporterJED(worldDst, data);

            player.setLocationAndAngles(x, y, z, player.rotationYaw, player.rotationPitch);
            server.getPlayerList().transferPlayerToDimension(player, data.getDimension(), teleporter);

            // See PlayerList#transferEntityToWorld()
            if (worldOld.provider.getDimension() == 1)
            {
                worldDst.spawnEntity(player);
            }

            // Teleporting FROM The End
            if (worldOld.provider instanceof WorldProviderEnd)
            {
                this.removeDragonBossBarHack(player, (WorldProviderEnd) worldOld.provider);
            }

            player.setPositionAndUpdate(x, y, z);
            worldDst.updateEntityWithOptionalForce(player, false);
            player.addExperience(0);
            player.setPlayerHealthUpdated();
        }
        else
        {
            WorldServer worldSrc = (WorldServer) entity.getEntityWorld();

            worldSrc.removeEntity(entity);
            entity.isDead = false;
            worldSrc.updateEntityWithOptionalForce(entity, false);

            Entity entityNew = EntityList.newEntity(entity.getClass(), worldDst);

            if (entityNew != null)
            {
                this.copyDataFromOld(entityNew, entity);
                entityNew.setLocationAndAngles(x, y, z, data.getYaw(), data.getPitch());

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

    public static Vec3d getClampedDestinationPosition(Vec3d posIn, World worldDst)
    {
        return getClampedDestinationPosition(posIn.xCoord, posIn.yCoord, posIn.zCoord, worldDst);
    }

    public static Vec3d getClampedDestinationPosition(double x, double y, double z, World worldDst)
    {
        WorldBorder border = worldDst.getWorldBorder();

        x = MathHelper.clamp(x, border.minX() + 2, border.maxX() - 2);
        z = MathHelper.clamp(z, border.minZ() + 2, border.maxZ() - 2);

        return new Vec3d(x, y, z);
    }

    private void removeDragonBossBarHack(EntityPlayerMP player, WorldProviderEnd provider)
    {
        // FIXME 1.9 - Somewhat ugly way to clear the Boss Info stuff when teleporting FROM The End
        DragonFightManager manager = provider.getDragonFightManager();

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

    private void copyDataFromOld(Entity target, Entity old)
    {
        try
        {
            this.methodHandle_Entity_copyDataFromOld.invokeExact(target, old);
        }
        catch (Throwable e)
        {
            JustEnoughDimensions.logger.error("Error while trying invoke Entity#copyDataFromOld()", e);
        }
    }

    public static class TeleportData
    {
        private final Entity entity;
        private int dimension;
        private double posX;
        private double posY;
        private double posZ;
        private float yaw;
        private float pitch;

        public TeleportData(Entity entity, int dimension, boolean useSpawn, MinecraftServer server)
        {
            this.entity = entity;
            this.dimension = dimension;
            this.posX = entity.posX;
            this.posY = entity.posY;
            this.posZ = entity.posZ;
            this.yaw = entity.rotationYaw;
            this.pitch = entity.rotationPitch;

            if (useSpawn)
            {
                WorldServer world = server.worldServerForDimension(dimension);

                if (world != null)
                {
                    BlockPos spawn = world.getSpawnCoordinate();

                    // If the spawn point of End type dimensions (that are using separate WorldInfo) has been moved,
                    // then use that manually set spawn point instead
                    if (spawn == null || (world.getSpawnPoint().equals(spawn) == false && world.getWorldInfo() instanceof WorldInfoJED))
                    {
                        spawn = world.getSpawnPoint();
                    }

                    if (spawn != null)
                    {
                        this.posX = spawn.getX() + 0.5;
                        this.posY = spawn.getY();
                        this.posZ = spawn.getZ() + 0.5;
                    }
                }
            }
        }

        public TeleportData(Entity entity, int dimension, double x, double y, double z)
        {
            this.entity = entity;
            this.dimension = dimension;
            this.posX = x;
            this.posY = y;
            this.posZ = z;
            this.yaw = entity.rotationYaw;
            this.pitch = entity.rotationPitch;
        }

        public TeleportData(Entity entity, int dimension, double x, double y, double z, float yaw, float pitch)
        {
            this.entity = entity;
            this.dimension = dimension;
            this.posX = x;
            this.posY = y;
            this.posZ = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public TeleportData(Entity entity, Entity otherEntity)
        {
            this.entity = entity;
            this.dimension = otherEntity.getEntityWorld().provider.getDimension();
            this.posX = otherEntity.posX;
            this.posY = otherEntity.posY;
            this.posZ = otherEntity.posZ;
            this.yaw = otherEntity.rotationYaw;
            this.pitch = otherEntity.rotationPitch;
        }

        public Entity getEntity() { return this.entity;    }
        public int getDimension() { return this.dimension; }
        public double getX()      { return this.posX;      }
        public double getY()      { return this.posY;      }
        public double getZ()      { return this.posZ;      }
        public float getYaw()     { return this.yaw;       }
        public float getPitch()   { return this.pitch;     }

        public Vec3d getPosition(World world)
        {
            return getClampedDestinationPosition(this.posX, this.posY, this.posZ, world);
        }
    }

    private static class TeleporterJED extends Teleporter
    {
        private final WorldServer world;
        private final TeleportData data;

        public TeleporterJED(WorldServer worldIn, TeleportData data)
        {
            super(worldIn);

            this.world = worldIn;
            this.data = data;
        }

        @Override
        public boolean makePortal(Entity entityIn)
        {
            return true;
        }

        @Override
        public boolean placeInExistingPortal(Entity entityIn, float rotationYaw)
        {
            Vec3d pos = this.data.getPosition(entityIn.getEntityWorld());
            entityIn.setLocationAndAngles(pos.xCoord, pos.yCoord, pos.zCoord, this.data.getYaw(), this.data.getPitch());
            return true;
        }

        @Override
        public void removeStalePortalLocations(long worldTime)
        {
            // NO-OP
        }

        @Override
        public void placeInPortal(Entity entityIn, float rotationYaw)
        {
            BlockPos spawnCoord = this.world.provider.getSpawnCoordinate();

            // For End type dimensions, generate the platform and place the entity there,
            // UNLESS the world has a different spawn point set.
            if ((this.world.provider.getDimensionType().getId() == 1 || this.world.provider instanceof WorldProviderEnd)
                 && this.world.getSpawnPoint().equals(spawnCoord))
            {
                IBlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
                IBlockState air = Blocks.AIR.getDefaultState();
                int spawnX = spawnCoord.getX();
                int spawnY = spawnCoord.getY();
                int spawnZ = spawnCoord.getZ();

                for (int zOff = -2; zOff <= 2; ++zOff)
                {
                    for (int xOff = -2; xOff <= 2; ++xOff)
                    {
                        for (int yOff = -1; yOff < 3; yOff++)
                        {
                            this.world.setBlockState(new BlockPos(spawnX + xOff, spawnY + yOff, spawnZ + zOff), yOff < 0 ? obsidian : air);
                        }
                    }
                }

                entityIn.setLocationAndAngles((double)spawnX, (double)spawnY, (double)spawnZ, entityIn.rotationYaw, 0.0F);
                entityIn.motionX = 0.0D;
                entityIn.motionY = 0.0D;
                entityIn.motionZ = 0.0D;
            }
        }
    }
}
