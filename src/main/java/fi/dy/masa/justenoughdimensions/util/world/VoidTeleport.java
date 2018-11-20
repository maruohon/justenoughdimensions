package fi.dy.masa.justenoughdimensions.util.world;

import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.command.CommandTeleportJED;
import fi.dy.masa.justenoughdimensions.command.CommandTeleportJED.TeleportData;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;

public class VoidTeleport
{
    public static void tryVoidTeleportEntities(World world, @Nullable VoidTeleportData voidTeleport, @Nullable VoidTeleportData skyTeleport)
    {
        if (voidTeleport != null || skyTeleport != null)
        {
            MinecraftServer server = world.getMinecraftServer();

            for (int i = 0; i < world.loadedEntityList.size(); i++)
            {
                Entity entity = world.loadedEntityList.get(i);

                if (voidTeleport != null && entity.posY < voidTeleport.getTriggerY())
                {
                    tryVoidTeleportEntity(entity, voidTeleport, server);
                }
                else if (skyTeleport != null && entity.posY > skyTeleport.getTriggerY())
                {
                    tryVoidTeleportEntity(entity, skyTeleport, server);
                }
            }
        }
    }

    private static void tryVoidTeleportEntity(Entity entity, VoidTeleportData voidTeleport, MinecraftServer server)
    {
        final int originalDimension = entity.getEntityWorld().provider.getDimension();

        try
        {
            TeleportData data = voidTeleport.getTeleportDataFor(entity, server);

            if (data != null)
            {
                if (voidTeleport.getRemoveFallDamage())
                {
                    entity.fallDistance = 0f;
                }
                else if (voidTeleport.getFallDistance() >= 0)
                {
                    entity.fallDistance = voidTeleport.getFallDistance();
                }

                CommandTeleportJED.instance().teleportEntityToLocation(entity, data, server);
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to \"void teleport\" entity '{}' (@ {}) from dimension '{}'",
                    entity.getName(), entity.getPositionVector(), originalDimension);
        }
    }

    public static class VoidTeleportData
    {
        private final TeleportType type;
        private final int destDimension;
        private double triggerY = -10;
        private float fallDistance = -1;
        private Vec3d scale;
        private Vec3d offset;
        private Vec3d targetPosition;
        private boolean findSurface;
        private boolean removeFallDamage;

        private VoidTeleportData(TeleportType type, int destDimension)
        {
            this.type = type;
            this.destDimension = destDimension;
        }

        public double getTriggerY()
        {
            return this.triggerY;
        }

        public int getDestinationDimension()
        {
            return this.destDimension;
        }

        public boolean getRemoveFallDamage()
        {
            return this.removeFallDamage;
        }

        /**
         * Returns a value < 0 if the value hasn't been set
         * @return
         */
        public float getFallDistance()
        {
            return this.fallDistance;
        }

        public Vec3d getTargetPosition(Vec3d originalPosition, WorldServer targetWorld)
        {
            Vec3d newPos = originalPosition;

            switch (this.type)
            {
                case SAME_LOCATION:
                    break;

                case OFFSET_LOCATION:
                    newPos = originalPosition.add(this.offset);
                    break;

                case SCALED_LOCATION:
                    newPos =  new Vec3d(originalPosition.x * this.scale.x,
                                        originalPosition.y * this.scale.y,
                                        originalPosition.z * this.scale.z);
                    break;

                case FIXED_LOCATION:
                    newPos =  this.targetPosition;
                    break;

                case SPAWN:
                    BlockPos spawn = WorldUtils.getWorldSpawn(targetWorld);
                    newPos =  new Vec3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                    break;
            }

            if (this.findSurface)
            {
                BlockPos pos = new BlockPos(newPos);
                pos = WorldUtils.getSuitableSpawnBlockInColumn(targetWorld, pos, true);
                newPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }

            return newPos;
        }

        private void setTriggerY(double triggerY)
        {
            this.triggerY = triggerY;
        }

        private void setScale(Vec3d scale)
        {
            this.scale = scale;
        }

        private void setOffset(Vec3d offset)
        {
            this.offset = offset;
        }

        private void setTargetPosition(Vec3d position)
        {
            this.targetPosition = position;
        }

        @Nullable
        public TeleportData getTeleportDataFor(Entity entity, MinecraftServer server)
        {
            WorldServer world = server.getWorld(this.destDimension);

            if (world != null)
            {
                Vec3d pos = this.getTargetPosition(entity.getPositionVector(), world);
                return new TeleportData(entity, this.destDimension, pos.x, pos.y, pos.z);
            }

            return null;
        }

        @Nullable
        public static VoidTeleportData fromJson(@Nullable JsonObject obj, int originalDimension)
        {
            if (obj != null)
            {
                try
                {
                    if (obj.has("dimension"))
                    {
                        int dimension = obj.get("dimension").getAsInt();
                        TeleportType type = TeleportType.SAME_LOCATION;

                        if (obj.has("teleport_type"))
                        {
                            type = TeleportType.fromName(obj.get("teleport_type").getAsString());
                        }

                        VoidTeleportData data = new VoidTeleportData(type, dimension);

                        if (obj.has("trigger_y"))
                        {
                            data.setTriggerY(obj.get("trigger_y").getAsDouble());
                        }

                        data.findSurface = JEDJsonUtils.getBooleanOrDefault(obj, "find_surface", false);
                        data.removeFallDamage = JEDJsonUtils.getBooleanOrDefault(obj, "remove_fall_damage", false);
                        data.fallDistance = JEDJsonUtils.getFloatOrDefault(obj, "fall_distance", -1);

                        if (type == TeleportType.SCALED_LOCATION)
                        {
                            data.setScale(JEDJsonUtils.getVec3dOrDefault(obj, "scale", new Vec3d(1, 1, 1)));
                        }
                        else if (type == TeleportType.OFFSET_LOCATION)
                        {
                            data.setOffset(JEDJsonUtils.getVec3dOrDefault(obj, "offset", new Vec3d(0, 128, 0)));
                        }
                        else if (type == TeleportType.FIXED_LOCATION)
                        {
                            data.setTargetPosition(JEDJsonUtils.getVec3dOrDefault(obj, "target_position", new Vec3d(0, 64, 0)));
                        }

                        return data;
                    }
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.warn("Invalid or incomplete VoidTeleportData for dimension '{}'", originalDimension);
                }
            }

            return null;
        }
    }

    public enum TeleportType
    {
        SAME_LOCATION,
        OFFSET_LOCATION,
        SCALED_LOCATION,
        FIXED_LOCATION,
        SPAWN;

        public static TeleportType fromName(String name)
        {
            for (TeleportType type : values())
            {
                if (type.name().equalsIgnoreCase(name))
                {
                    return type;
                }
            }

            JustEnoughDimensions.logger.warn("Invalid TeleportType name: '{}'", name);
            return SAME_LOCATION;
        }
    }
}
