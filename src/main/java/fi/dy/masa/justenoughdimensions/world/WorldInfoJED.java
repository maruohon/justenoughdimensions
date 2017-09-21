package fi.dy.masa.justenoughdimensions.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;

public class WorldInfoJED extends WorldInfo
{
    private NBTTagCompound fullJEDTag;
    private boolean forceGameMode;
    private boolean useCustomDayCycle;
    private int dayLength = 12000;
    private int nightLength = 12000;
    private int cloudHeight = 128;
    private int skyRenderType;
    private int skyDisableFlags;
    private Vec3d skyColor = null;
    private Vec3d cloudColor = null;
    private Vec3d fogColor = null;
    private float[] customLightBrightnessTable = null;
    protected Boolean canRespawnHere = null;
    protected Integer respawnDimension = null;

    private int debugTiles;
    private World world;
    private Map<BlockPos, TileEntity> tiles = new HashMap<>();
    private Set<ChunkPos> chunkPos = new HashSet<>();
    private long lastTick;

    public void setDebugEnabled(@Nullable World world, int mask)
    {
        this.world = world;
        this.debugTiles = mask;
    }

    @Override
    public long getWorldTotalTime()
    {
        long tick = super.getWorldTotalTime();

        if (this.debugTiles != 0 && this.world instanceof WorldServer && this.lastTick != tick)
        {
            WorldServer world = (WorldServer) this.world;
            String prof = ReflectionHelper.getPrivateValue(Profiler.class, this.world.profiler, "field_76323_d", "profilingSection");

            // Should be in WorldServer#tick() before this.chunkProvider.tick(), which is one of the problematic places
            if (prof.endsWith("mobSpawner"))
            {
                //JustEnoughDimensions.logger.warn("============== JED DEBUG START MARKER FOR TICK {} ==========", super.getWorldTotalTime());

                for (Chunk chunk : world.getChunkProvider().getLoadedChunks())
                {
                    this.chunkPos.add(chunk.getPos());

                    for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet())
                    {
                        BlockPos pos = entry.getKey();

                        if (this.tiles.containsKey(pos))
                        {
                            JustEnoughDimensions.logger.warn("{} - Duplicate TileEntity @ {}; old: {}, current: {}",
                                    tick, pos, this.tiles.get(pos).getClass().getName(), entry.getValue().getClass().getName());
                        }

                        this.tiles.put(pos, entry.getValue());
                    }
                }

                this.lastTick = super.getWorldTotalTime();
            }
        }

        return super.getWorldTotalTime();
    }

    @Override
    public void setWorldTotalTime(long time)
    {
        super.setWorldTotalTime(time);

        if (this.debugTiles != 0 && this.world instanceof WorldServer && this.chunkPos.isEmpty() == false)
        {
            long tick = super.getWorldTotalTime();
            WorldServer world = (WorldServer) this.world;
            Set<ChunkPos> chunks = new HashSet<>();

            for (Chunk chunk : world.getChunkProvider().getLoadedChunks())
            {
                chunks.add(chunk.getPos());

                if (this.chunkPos.contains(chunk.getPos()) == false)
                {
                    JustEnoughDimensions.logger.warn("{} - New chunk loaded @ {}", tick, chunk.getPos());
                }

                for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet())
                {
                    BlockPos pos = entry.getKey();

                    if (this.tiles.containsKey(pos) == false)
                    {
                        JustEnoughDimensions.logger.warn("{} - Added TileEntity @ {} - {}",
                                tick, pos, entry.getValue().getClass().getName());
                    }
                    // Compare by reference
                    else if (this.tiles.get(pos) != entry.getValue())
                    {
                        JustEnoughDimensions.logger.warn("{} - Changed TileEntity @ {}; old: {}, current: {}",
                                tick, pos, this.tiles.get(pos).getClass().getName(), entry.getValue().getClass().getName());
                    }

                    this.tiles.remove(pos);
                }

                this.chunkPos.remove(chunk.getPos());
            }

            for (Map.Entry<BlockPos, TileEntity> entry : this.tiles.entrySet())
            {
                BlockPos pos = entry.getKey();

                // This chunk is still loaded, but the TileEntity is gone from the world
                if (chunks.contains(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)))
                {
                    JustEnoughDimensions.logger.warn("{} - Removed TileEntity @ {} - {}",
                            tick, pos, entry.getValue().getClass().getName());
                }
            }

            if ((this.debugTiles & 0x2) != 0)
            {
                for (ChunkPos pos : this.chunkPos)
                {
                    JustEnoughDimensions.logger.info("{} - Unloaded chunk @ {}", tick, pos);
                }
            }

            this.chunkPos.clear();
            this.tiles.clear();
        }
    }

    public WorldInfoJED(NBTTagCompound nbt)
    {
        super(nbt);

        if (nbt.hasKey("JED", Constants.NBT.TAG_COMPOUND))
        {
            NBTTagCompound tag = nbt.getCompoundTag("JED");
            this.fullJEDTag = tag;
            if (tag.hasKey("ForceGameMode", Constants.NBT.TAG_BYTE))   { this.forceGameMode = tag.getBoolean("ForceGameMode"); }
            if (tag.hasKey("CustomDayCycle", Constants.NBT.TAG_BYTE))  { this.useCustomDayCycle = tag.getBoolean("CustomDayCycle"); }
            if (tag.hasKey("DayLength",     Constants.NBT.TAG_INT))    { this.dayLength   = tag.getInteger("DayLength"); }
            if (tag.hasKey("NightLength",   Constants.NBT.TAG_INT))    { this.nightLength = tag.getInteger("NightLength"); }
            if (tag.hasKey("CloudHeight",   Constants.NBT.TAG_INT))    { this.cloudHeight = tag.getInteger("CloudHeight"); }
            if (tag.hasKey("SkyRenderType", Constants.NBT.TAG_BYTE))   { this.skyRenderType = tag.getByte("SkyRenderType"); }
            if (tag.hasKey("SkyDisableFlags", Constants.NBT.TAG_BYTE)) { this.skyDisableFlags = tag.getByte("SkyDisableFlags"); }

            if (tag.hasKey("SkyColor",      Constants.NBT.TAG_STRING)) { this.skyColor   = JEDStringUtils.hexStringToColor(tag.getString("SkyColor")); }
            if (tag.hasKey("CloudColor",    Constants.NBT.TAG_STRING)) { this.cloudColor = JEDStringUtils.hexStringToColor(tag.getString("CloudColor")); }
            if (tag.hasKey("FogColor",      Constants.NBT.TAG_STRING)) { this.fogColor   = JEDStringUtils.hexStringToColor(tag.getString("FogColor")); }

            if (tag.hasKey("LightBrightness", Constants.NBT.TAG_LIST))
            {
                NBTTagList list = tag.getTagList("LightBrightness", Constants.NBT.TAG_FLOAT);

                if (list.tagCount() == 16)
                {
                    this.customLightBrightnessTable = new float[16];

                    for (int i = 0; i < 16; i++)
                    {
                        this.customLightBrightnessTable[i] = list.getFloatAt(i);
                    }
                }
            }

            if (tag.hasKey("CanRespawnHere", Constants.NBT.TAG_BYTE))   { this.canRespawnHere = tag.getBoolean("CanRespawnHere"); }
            if (tag.hasKey("RespawnDimension", Constants.NBT.TAG_INT))  { this.respawnDimension = tag.getInteger("RespawnDimension"); }
        }

        if (this.dayLength   <= 0) { this.dayLength = 1; }
        if (this.nightLength <= 0) { this.nightLength = 1; }
    }

    // Commented out the JED property saving to NBT, because doing that would mean
    // that the properties can't be removed via the dimbuilder command or by
    // removing them from the dimensions.json config file.

    /*@Override
    public NBTTagCompound cloneNBTCompound(NBTTagCompound nbt)
    {
        nbt = super.cloneNBTCompound(nbt);
        nbt.setTag("JED", this.getJEDTag());

        return nbt;
    }*/

    public NBTTagCompound getJEDTagForClientSync()
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("DayLength", this.dayLength);
        tag.setInteger("NightLength", this.nightLength);
        tag.setInteger("CloudHeight", this.cloudHeight);
        tag.setByte("SkyRenderType", (byte) this.skyRenderType);
        tag.setByte("SkyDisableFlags", (byte) this.skyDisableFlags);

        if (this.useCustomDayCycle)  { tag.setBoolean("CustomDayCycle", this.useCustomDayCycle); }
        if (this.skyColor != null)   { tag.setString("SkyColor",   JEDStringUtils.colorToHexString(this.skyColor)); }
        if (this.cloudColor != null) { tag.setString("CloudColor", JEDStringUtils.colorToHexString(this.cloudColor)); }
        if (this.fogColor != null)   { tag.setString("FogColor",   JEDStringUtils.colorToHexString(this.fogColor)); }
        if (this.customLightBrightnessTable != null) { tag.setTag("LightBrightness", writeFloats(this.customLightBrightnessTable)); }

        return tag;
    }

    public NBTTagCompound getFullJEDTag()
    {
        return this.fullJEDTag;
    }

    @Nonnull
    private static NBTTagList writeFloats(float... values)
    {
        NBTTagList tagList = new NBTTagList();

        for (float f : values)
        {
            tagList.appendTag(new NBTTagFloat(f));
        }

        return tagList;
    }

    @Override
    public void setDifficulty(EnumDifficulty newDifficulty)
    {
        // NO-OP to prevent the MinecraftServer from reseting this
    }

    @Override
    public void setGameType(GameType type)
    {
        // NO-OP to prevent the MinecraftServer from reseting this
    }

    public void setDifficultyJED(EnumDifficulty newDifficulty)
    {
        super.setDifficulty(newDifficulty);
    }

    public void setGameTypeJED(GameType type)
    {
        super.setGameType(type);
    }

    public boolean getForceGamemode()
    {
        return this.forceGameMode;
    }

    public boolean getUseCustomDayCycle()
    {
        return this.useCustomDayCycle;
    }

    public int getDayLength()
    {
        return this.dayLength;
    }

    public int getNightLength()
    {
        return this.nightLength;
    }

    public float[] getCustomLightBrightnessTable()
    {
        return this.customLightBrightnessTable;
    }

    @Nullable
    public Boolean canRespawnHere()
    {
        return this.canRespawnHere;
    }

    @Nullable
    public Integer getRespawnDimension()
    {
        return this.respawnDimension;
    }
}
