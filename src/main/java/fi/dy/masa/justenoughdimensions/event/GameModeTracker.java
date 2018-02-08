package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.reference.Reference;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;

public class GameModeTracker
{
    private static GameModeTracker instance;
    private Map<UUID, GameType> gameModes = new HashMap<UUID, GameType>();
    private boolean dirty = false;

    public static GameModeTracker getInstance()
    {
        if (instance == null)
        {
            instance = new GameModeTracker();
        }

        return instance;
    }

    /**
     * The basic idea here is to store the "normal" gamemode of a player in a non-forced-gamemode
     * dimension, so that we know what to set the player's gamemode to when they leave a forced-gamemode-dimension.
     * Note that they may be changing between different forced-gamemode dimension before returning to
     * a non-forced-gamemode dimension.
     */
    public void playerChangedDimension(EntityPlayerMP player, int dimFrom, int dimTo)
    {
        boolean forcedFrom = this.dimensionHasForcedGamemode(dimFrom);
        boolean forcedTo = this.dimensionHasForcedGamemode(dimTo);

        if (forcedTo)
        {
            // The gamemode only needs to be stored when changing to a forced-gamemode dimension
            // from a non-forced-gamemode dimension. Ie. the gamemode won't be touched when switching
            // between non-forced-gamemode dimensions, and the stored one won't be changed if switching
            // between multiple forced ones.
            if (forcedFrom == false)
            {
                this.storeNonForcedGamemode(player);
            }

            // The player is in the destination world at this point, so we get the gamemode from there
            this.setPlayerGamemode(player, player.getEntityWorld().getWorldInfo().getGameType());
        }
        // When switching to a non-forced-gamemode dimension from a forced-gamemode dimension,
        // ie. we have a stored gamemode for the player.
        else if (this.gameModes.containsKey(player.getUniqueID()))
        {
            this.restoreStoredGamemode(player);
        }
    }

    private boolean dimensionHasForcedGamemode(int dimension)
    {
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(dimension);
        return props != null && props.getForceGameMode();
    }

    private void storeNonForcedGamemode(EntityPlayerMP player)
    {
        this.gameModes.put(player.getUniqueID(), player.interactionManager.getGameType());
        this.dirty = true;
    }

    private void restoreStoredGamemode(EntityPlayerMP player)
    {
        this.setPlayerGamemode(player, this.gameModes.get(player.getUniqueID()));
        this.gameModes.remove(player.getUniqueID());
        this.dirty = true;
    }

    private void setPlayerGamemode(EntityPlayerMP player, GameType type)
    {
        player.setGameType(type);
        player.sendMessage(new TextComponentTranslation("jed.info.gamemode.changed", type.toString()));
    }

    public void readFromDisk()
    {
        // Clear the data structures when reading the data for a world/save, so that data
        // from another world won't carry over to a world/save that doesn't have the file yet.
        this.gameModes.clear();

        try
        {
            File saveDir = DimensionManager.getCurrentSaveRootDirectory();

            if (saveDir == null)
            {
                return;
            }

            File file = new File(new File(saveDir, Reference.MOD_ID), "gamemodetracker.dat");

            if (file.exists() && file.isFile())
            {
                this.readFromNBT(CompressedStreamTools.readCompressed(new FileInputStream(file)));
            }
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to read GamemodeTracker data from file");
        }
    }

    public void writeToDisk()
    {
        if (this.dirty == false)
        {
            return;
        }

        try
        {
            File saveDir = DimensionManager.getCurrentSaveRootDirectory();

            if (saveDir == null)
            {
                return;
            }

            saveDir = new File(saveDir, Reference.MOD_ID);

            if (saveDir.exists() == false && saveDir.mkdirs() == false)
            {
                JustEnoughDimensions.logger.warn("Failed to create the save directory '{}'", saveDir.toString());
                return;
            }

            File fileTmp = new File(saveDir, "gamemodetracker.dat.tmp");
            File fileReal = new File(saveDir, "gamemodetracker.dat");
            CompressedStreamTools.writeCompressed(this.writeToNBT(new NBTTagCompound()), new FileOutputStream(fileTmp));

            if (fileReal.exists())
            {
                fileReal.delete();
            }

            fileTmp.renameTo(fileReal);
            this.dirty = false;
        }
        catch (Exception e)
        {
            JustEnoughDimensions.logger.warn("Failed to write GamemodeTracker data to file", e);
        }
    }

    private void readFromNBT(NBTTagCompound nbt)
    {
        if (nbt == null || nbt.hasKey("GamemodeTracker", Constants.NBT.TAG_LIST) == false)
        {
            return;
        }

        NBTTagList tagList = nbt.getTagList("GamemodeTracker", Constants.NBT.TAG_COMPOUND);
        int count = tagList.tagCount();

        for (int i = 0; i < count; ++i)
        {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);

            if (tag.hasKey("UUIDM", Constants.NBT.TAG_LONG) &&
                tag.hasKey("UUIDL", Constants.NBT.TAG_LONG) &&
                tag.hasKey("GameMode", Constants.NBT.TAG_BYTE))
            {
                GameType type = GameType.getByID(tag.getByte("GameMode"));

                if (type != GameType.NOT_SET)
                {
                    this.gameModes.put(new UUID(tag.getLong("UUIDM"), tag.getLong("UUIDL")), type);
                }
            }
        }
    }

    private NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        NBTTagList tagList = new NBTTagList();

        for (Map.Entry<UUID, GameType> entry : this.gameModes.entrySet())
        {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("UUIDM", entry.getKey().getMostSignificantBits());
            tag.setLong("UUIDL", entry.getKey().getLeastSignificantBits());
            tag.setByte("GameMode", (byte) entry.getValue().getID());

            tagList.appendTag(tag);
        }

        nbt.setTag("GamemodeTracker", tagList);

        return nbt;
    }
}
