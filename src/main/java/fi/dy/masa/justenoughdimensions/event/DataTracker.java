package fi.dy.masa.justenoughdimensions.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.util.EntityUtils;
import fi.dy.masa.justenoughdimensions.util.PlayerInventoryStorage;
import fi.dy.masa.justenoughdimensions.util.world.WorldFileUtils;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;

public class DataTracker
{
    private static DataTracker instance;
    private Map<UUID, PlayerData> playerData = new HashMap<>();
    private boolean dirty = false;

    public static DataTracker getInstance()
    {
        if (instance == null)
        {
            instance = new DataTracker();
        }

        return instance;
    }

    private PlayerData getOrCreatePlayerData(UUID uuid)
    {
        PlayerData data = this.playerData.get(uuid);

        if (data == null)
        {
            data = new PlayerData();
            this.playerData.put(uuid, data);
        }

        return data;
    }

    public void playerLoginOrRespawn(EntityPlayer playerIn)
    {
        if (EntityUtils.isValidPlayerMP(playerIn))
        {
            PlayerData data = this.playerData.get(playerIn.getUniqueID());

            if (data != null)
            {
                int dimFrom = data.dimension;
                int dimTo = playerIn.getEntityWorld().provider.getDimension();

                if (dimFrom != dimTo)
                {
                    this.playerChangedDimension(playerIn, dimFrom, dimTo);
                }
            }

            this.storePlayerDimension(playerIn);
        }
    }

    public void playerDied(EntityPlayer player)
    {
        if (player.getEntityWorld().getWorldInfo().isHardcoreModeEnabled())
        {
            this.getOrCreatePlayerData(player.getUniqueID()).deadInHardcore = true;
            this.dirty = true;
        }
    }

    public void playerInitialSpawn(EntityPlayer player)
    {
        UUID uuid = player.getUniqueID();

        // If players first join into a ForceGameMode dimension, set a "normal game mode"
        // for them from the main configuration.
        if (EntityUtils.isValidPlayerMP(player) &&
            this.dimensionHasForcedGameMode(player.dimension) &&
            this.playerData.containsKey(uuid) == false)
        {
            PlayerData data = this.getOrCreatePlayerData(uuid);
            data.normalGameMode = Configs.normalGameMode;
            data.dimension = player.dimension;
            this.dirty = true;

            JustEnoughDimensions.logInfo("DataTracker: Set the \"normal game mode\" of player '{}' to '{}' after their initial join to a ForceGameMode dimension {}",
                    player.getName(), Configs.normalGameMode, player.dimension);
        }
    }

    /**
     * The basic idea here is to store the "normal" gamemode of a player in a non-forced-gamemode
     * dimension, so that we know what to set the player's gamemode to when they leave a forced-gamemode-dimension.
     * Note that they may be changing between different forced-gamemode dimension before returning to
     * a non-forced-gamemode dimension.
     */
    public void playerChangedDimension(EntityPlayer playerIn, int dimFrom, int dimTo)
    {
        if (EntityUtils.isValidPlayerMP(playerIn))
        {
            this.storePlayerDimension(playerIn);

            if (Configs.enableForcedGameModes)
            {
                PlayerData data = this.getOrCreatePlayerData(playerIn.getUniqueID());

                if (data.deadInHardcore)
                {
                    return;
                }

                EntityPlayerMP player = (EntityPlayerMP) playerIn;
                boolean forcedFrom = this.dimensionHasForcedGameMode(dimFrom);
                boolean forcedTo = this.dimensionHasForcedGameMode(dimTo);

                if (forcedTo)
                {
                    // The gamemode only needs to be stored when changing to a forced-gamemode dimension
                    // from a non-forced-gamemode dimension. Ie. the gamemode won't be touched when switching
                    // between non-forced-gamemode dimensions, and the stored one won't be changed if switching
                    // between multiple forced ones.
                    if (forcedFrom == false)
                    {
                        this.storeNonForcedGameMode(player);
                    }

                    // The player is in the destination world at this point, so we get the gamemode from there
                    this.setPlayerGameMode(player, player.getEntityWorld().getWorldInfo().getGameType());

                    JustEnoughDimensions.logInfo("DataTracker: Set gamemode '{}' for player '{}'",
                            player.interactionManager.getGameType(), player.getName());
                }
                // When switching to a non-forced-gamemode dimension from a forced-gamemode dimension,
                // ie. we have a stored gamemode for the player.
                else
                {
                    this.restoreStoredGameModeIfExists(player);
                }
            }

            if (Configs.enablePlayerInventoryGroups)
            {
                String groupFrom = this.getPlayerInventoryGroup(dimFrom);
                String groupTo = this.getPlayerInventoryGroup(dimTo);

                if (groupTo.equals(groupFrom) == false)
                {
                    JustEnoughDimensions.logInfo("DataTracker: Swapping the player inventories of player '{}' from group '{}' to group '{}'",
                            playerIn.getName(), groupFrom, groupTo);

                    PlayerInventoryStorage.INSTANCE.writePlayerInventoryDataForGroup(playerIn, groupFrom);
                    PlayerInventoryStorage.INSTANCE.readPlayerInventoryDataForGroup(playerIn, groupTo);
                    playerIn.inventoryContainer.detectAndSendChanges();
                }
            }
        }
    }

    private boolean dimensionHasForcedGameMode(int dimension)
    {
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(dimension);
        return props != null && props.getForceGameMode();
    }

    private String getPlayerInventoryGroup(int dimension)
    {
        JEDWorldProperties props = JEDWorldProperties.getPropertiesIfExists(dimension);
        return props != null ? props.getPlayerInventoryGroup(dimension) : PlayerInventoryStorage.DEFAULT_PLAYER_INVENTORY_GROUP;
    }

    private void storeNonForcedGameMode(EntityPlayerMP player)
    {
        this.getOrCreatePlayerData(player.getUniqueID()).normalGameMode = player.interactionManager.getGameType();
        this.dirty = true;

        JustEnoughDimensions.logInfo("DataTracker: Stored a non-forced gamemode '{}' for player '{}'",
                player.interactionManager.getGameType(), player.getName());
    }

    private void restoreStoredGameModeIfExists(EntityPlayerMP player)
    {
        PlayerData data = this.getOrCreatePlayerData(player.getUniqueID());

        if (data.normalGameMode != null)
        {
            this.setPlayerGameMode(player, data.normalGameMode);
            data.normalGameMode = null;
            this.dirty = true;

            JustEnoughDimensions.logInfo("DataTracker: Restored gamemode '{}' for player '{}'",
                    player.interactionManager.getGameType(), player.getName());
        }
    }

    private void setPlayerGameMode(EntityPlayerMP player, GameType type)
    {
        player.setGameType(type);
        player.sendMessage(new TextComponentTranslation("jed.info.gamemode.changed", type.toString()));
    }

    private void storePlayerDimension(EntityPlayer player)
    {
        int dimension = player.getEntityWorld().provider.getDimension();
        this.getOrCreatePlayerData(player.getUniqueID()).dimension = dimension;
        this.dirty = true;

        JustEnoughDimensions.logInfo("DataTracker: Stored dimension '{}' for player '{}'", dimension, player.getName());
    }

    @Nullable
    public Integer getPlayersDimension(EntityPlayer player)
    {
        return this.getOrCreatePlayerData(player.getUniqueID()).dimension;
    }

    public int getPlayerCountInDimension(int dimension)
    {
        int count = 0;

        for (PlayerData data : this.playerData.values())
        {
            if (data.dimension == dimension)
            {
                count++;
            }
        }

        return count;
    }

    public void readFromDisk(@Nullable File worldDir)
    {
        // Clear the data structures when reading the data for a world/save, so that data
        // from another world won't carry over to a world/save that doesn't have the file yet.
        this.playerData.clear();

        if (worldDir != null)
        {
            File jedDataDir = WorldFileUtils.getWorldJEDDataDirectory(worldDir);
            File file = new File(jedDataDir, "data_tracker.dat");

            if (file.exists() && file.isFile() && file.canRead())
            {
                try
                {
                    FileInputStream is = new FileInputStream(file);
                    this.readFromNBT(CompressedStreamTools.readCompressed(is));
                    is.close();
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.warn("Failed to read DataTracker data from file '{}'", file.getAbsolutePath());
                }
            }
        }
    }

    public void writeToDisk()
    {
        if (this.dirty)
        {
            try
            {
                File saveDir = DimensionManager.getCurrentSaveRootDirectory();

                if (saveDir == null)
                {
                    return;
                }

                File jedDataDir = WorldFileUtils.getWorldJEDDataDirectory(saveDir);

                if (jedDataDir.exists() == false && jedDataDir.mkdirs() == false)
                {
                    JustEnoughDimensions.logger.warn("Failed to create the save directory '{}'", jedDataDir.getAbsolutePath());
                    return;
                }

                final NBTTagCompound nbt = this.writeToNBT(new NBTTagCompound());

                ThreadedFileIOBase.getThreadedIOInstance().queueIO(() ->
                {
                    File fileTmp = new File(jedDataDir, "data_tracker.dat.tmp");
                    File fileReal = new File(jedDataDir, "data_tracker.dat");

                    try
                    {
                        FileOutputStream os = new FileOutputStream(fileTmp);
                        CompressedStreamTools.writeCompressed(nbt, os);
                        os.close();

                        if (fileReal.exists())
                        {
                            fileReal.delete();
                        }

                        fileTmp.renameTo(fileReal);
                    }
                    catch (Exception e)
                    {
                        JustEnoughDimensions.logger.warn("Failed to write DataTracker data to file", e);
                    }

                    return false;
                });

                this.dirty = false;
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to write DataTracker data to file", e);
            }
        }
    }

    private void readFromNBTOld(NBTTagCompound nbt)
    {
        if (nbt != null)
        {
            if (nbt.hasKey("PlayerGameModes", Constants.NBT.TAG_LIST))
            {
                NBTTagList tagList = nbt.getTagList("PlayerGameModes", Constants.NBT.TAG_COMPOUND);
                final int count = tagList.tagCount();

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
                            this.getOrCreatePlayerData(new UUID(tag.getLong("UUIDM"), tag.getLong("UUIDL"))).normalGameMode = type;
                        }
                    }
                }
            }

            if (nbt.hasKey("PlayerDimensions", Constants.NBT.TAG_LIST))
            {
                NBTTagList tagList = nbt.getTagList("PlayerDimensions", Constants.NBT.TAG_COMPOUND);
                final int count = tagList.tagCount();

                for (int i = 0; i < count; ++i)
                {
                    NBTTagCompound tag = tagList.getCompoundTagAt(i);

                    if (tag.hasKey("UUIDM", Constants.NBT.TAG_LONG) &&
                        tag.hasKey("UUIDL", Constants.NBT.TAG_LONG) &&
                        tag.hasKey("Dimension", Constants.NBT.TAG_INT))
                    {
                        this.getOrCreatePlayerData(new UUID(tag.getLong("UUIDM"), tag.getLong("UUIDL"))).dimension = tag.getInteger("Dimension");
                    }
                }
            }
        }
    }

    private void readFromNBT(NBTTagCompound nbt)
    {
        if (nbt != null)
        {
            if (nbt.hasKey("PlayerData", Constants.NBT.TAG_LIST))
            {
                NBTTagList tagList = nbt.getTagList("PlayerData", Constants.NBT.TAG_COMPOUND);
                final int count = tagList.tagCount();

                for (int i = 0; i < count; ++i)
                {
                    NBTTagCompound tag = tagList.getCompoundTagAt(i);

                    if (tag.hasKey("UUIDM", Constants.NBT.TAG_LONG) &&
                        tag.hasKey("UUIDL", Constants.NBT.TAG_LONG))
                    {
                        PlayerData data = new PlayerData();
                        data.dimension = tag.getInteger("Dimension");
                        data.deadInHardcore = tag.getBoolean("HardcoreDead");

                        if (tag.hasKey("GameMode", Constants.NBT.TAG_BYTE))
                        {
                            GameType type = GameType.getByID(tag.getByte("GameMode"));

                            if (type != GameType.NOT_SET)
                            {
                                data.normalGameMode = type;
                            }
                        }

                        this.playerData.put(new UUID(tag.getLong("UUIDM"), tag.getLong("UUIDL")), data);
                    }
                }
            }
            else
            {
                this.readFromNBTOld(nbt);
            }
        }
    }

    private NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        NBTTagList tagList = new NBTTagList();

        for (Map.Entry<UUID, PlayerData> entry : this.playerData.entrySet())
        {
            PlayerData data = entry.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("UUIDM", entry.getKey().getMostSignificantBits());
            tag.setLong("UUIDL", entry.getKey().getLeastSignificantBits());
            tag.setInteger("Dimension", data.dimension);
            tag.setBoolean("HardcoreDead", data.deadInHardcore);

            if (data.normalGameMode != null)
            {
                tag.setByte("GameMode", (byte) data.normalGameMode.getID());
            }

            tagList.appendTag(tag);
        }

        nbt.setTag("PlayerData", tagList);

        return nbt;
    }

    private static class PlayerData
    {
        public boolean deadInHardcore;
        public int dimension;
        @Nullable
        public GameType normalGameMode;
    }
}
