package fi.dy.masa.justenoughdimensions.util.world;

import java.util.List;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import fi.dy.masa.justenoughdimensions.config.DimensionTypeEntry;

public class DimensionDump extends DataDump
{
    private DimensionDump(int columns)
    {
        super(columns);

        this.setSort(false);
    }

    public static List<String> getFormattedRegisteredDimensionsDump()
    {
        DimensionDump dimensionDump = new DimensionDump(6);
        Integer[] ids = DimensionManager.getStaticDimensionIDs();

        for (int i = 0; i < ids.length; i++)
        {
            DimensionType type = DimensionManager.getProviderType(ids[i]);

            if (type == null)
            {
                continue;
            }

            String dimId = ids[i].toString();
            String typeId = String.valueOf(type.getId());
            String name = type.getName();
            String shouldLoadSpawn = String.valueOf(type.shouldLoadSpawn());
            String worldProviderClass;
            String currentlyLoaded = String.valueOf(DimensionManager.getWorld(ids[i]) != null);

            try
            {
                worldProviderClass = DimensionTypeEntry.getProviderClassFrom(type).getSimpleName();
            }
            catch (Exception e)
            {
                worldProviderClass = "ERROR";
            }

            dimensionDump.addData(dimId, typeId, name, shouldLoadSpawn, worldProviderClass, currentlyLoaded);
        }

        dimensionDump.addTitle("ID", "Type ID", "Name", "Load Spawn?", "WorldProvider class", "Loaded?");
        dimensionDump.setColumnAlignment(0, Alignment.RIGHT); // dim ID
        dimensionDump.setColumnAlignment(1, Alignment.RIGHT); // type ID
        dimensionDump.setColumnAlignment(3, Alignment.RIGHT); // shouldLoadSpawn
        dimensionDump.setColumnAlignment(5, Alignment.RIGHT); // currentlyLoaded
        dimensionDump.setUseColumnSeparator(true);

        return dimensionDump.getLines();
    }

    public static List<String> getFormattedLoadedDimensionsDump()
    {
        DimensionDump dimensionDump = new DimensionDump(7);
        Integer[] ids = DimensionManager.getIDs();

        for (int i = 0; i < ids.length; i++)
        {
            WorldServer world = DimensionManager.getWorld(ids[i]);
            DimensionType type = DimensionManager.getProviderType(ids[i]);

            if (world == null || type == null)
            {
                continue;
            }

            String dimId = ids[i].toString();
            String typeId = String.valueOf(type.getId());
            String name = type.getName();
            String shouldLoadSpawn = String.valueOf(type.shouldLoadSpawn());
            String worldProviderClass;
            String loadedChunks = String.valueOf(WorldUtils.getLoadedChunkCount(world));
            String loadedEntities = String.valueOf(world.loadedEntityList.size());

            try
            {
                worldProviderClass = DimensionTypeEntry.getProviderClassFrom(type).getSimpleName();
            }
            catch (Exception e)
            {
                worldProviderClass = "ERROR";
            }

            dimensionDump.addData(dimId, typeId, name, shouldLoadSpawn, worldProviderClass, loadedChunks, loadedEntities);
        }

        dimensionDump.addTitle("ID", "Type ID", "Name", "Load Spawn?", "WorldProvider class", "Loaded chunks", "Loaded entities");
        dimensionDump.setColumnAlignment(0, Alignment.RIGHT); // dim ID
        dimensionDump.setColumnAlignment(1, Alignment.RIGHT); // type ID
        dimensionDump.setColumnAlignment(3, Alignment.RIGHT); // shouldLoadSpawn
        dimensionDump.setColumnAlignment(5, Alignment.RIGHT); // loaded chunks
        dimensionDump.setColumnAlignment(6, Alignment.RIGHT); // loaded entities
        dimensionDump.setUseColumnSeparator(true);

        return dimensionDump.getLines();
    }
}
