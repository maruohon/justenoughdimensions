package fi.dy.masa.justenoughdimensions.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class ChunkProviderServerDebug extends ChunkProviderServer
{
    private int debugMask;
    private Map<BlockPos, TileEntity> tiles = new HashMap<>();
    private Set<ChunkPos> chunkPos = new HashSet<>();

    public ChunkProviderServerDebug(ChunkProviderServer parent)
    {
        super(parent.world, parent.chunkLoader, parent.chunkGenerator);
    }

    public void setDebugMask(int mask)
    {
        this.debugMask = mask;
        this.chunkPos.clear();
        this.tiles.clear();
    }

    @Override
    public boolean saveChunks(boolean all)
    {
        this.preSave();

        boolean ret = super.saveChunks(all);
        this.postSave("saveChunks");

        return ret;
    }

    @Override
    public boolean tick()
    {
        this.preSave();

        boolean ret = super.tick();
        this.postSave("tick");

        return ret;
    }

    private void preSave()
    {
        long tick = this.world.getTotalWorldTime();
        String pre = String.format("DIM: %6d, tick: %6d", this.world.provider.getDimension(), tick);

        if (this.debugMask != 0)
        {
            //JustEnoughDimensions.logger.warn("============== JED DEBUG START MARKER FOR TICK {} ==========", super.getWorldTotalTime());

            for (Chunk chunk : this.getLoadedChunks())
            {
                this.chunkPos.add(chunk.getPos());

                for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet())
                {
                    BlockPos pos = entry.getKey();

                    if (this.tiles.containsKey(pos))
                    {
                        JustEnoughDimensions.logger.warn("{} - Duplicate TileEntity @ {}; old: {}, current: {}",
                                pre, pos, this.tiles.get(pos).getClass().getName(), entry.getValue().getClass().getName());
                    }

                    this.tiles.put(pos, entry.getValue());
                }
            }
        }
    }

    private void postSave(String pre)
    {
        if (this.debugMask != 0 && this.chunkPos.isEmpty() == false)
        {
            long tick = this.world.getTotalWorldTime();
            pre = String.format("DIM: %6d, tick: %6d, m: %s", this.world.provider.getDimension(), tick, pre);
            Set<ChunkPos> chunks = new HashSet<>();

            for (Chunk chunk : this.getLoadedChunks())
            {
                chunks.add(chunk.getPos());

                if (this.chunkPos.contains(chunk.getPos()) == false)
                {
                    JustEnoughDimensions.logger.warn("{} - New chunk loaded @ {}", pre, chunk.getPos());
                }

                for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet())
                {
                    BlockPos pos = entry.getKey();

                    if (this.tiles.containsKey(pos) == false)
                    {
                        JustEnoughDimensions.logger.warn("{} - Added TileEntity @ {} - {}",
                                pre, pos, entry.getValue().getClass().getName());
                    }
                    else
                    {
                        // Compare by reference
                        if (this.tiles.get(pos) != entry.getValue())
                        {
                            JustEnoughDimensions.logger.warn("{} - Changed TileEntity @ {}; old: {}, current: {}",
                                    pre, pos, this.tiles.get(pos).getClass().getName(), entry.getValue().getClass().getName());
                        }

                        this.tiles.remove(pos);
                    }
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
                            pre, pos, entry.getValue().getClass().getName());
                }
            }

            if ((this.debugMask & 0x2) != 0)
            {
                for (ChunkPos pos : this.chunkPos)
                {
                    JustEnoughDimensions.logger.info("{} - Unloaded chunk @ {}", pre, pos);
                }
            }

            this.chunkPos.clear();
            this.tiles.clear();
        }
    }
}
