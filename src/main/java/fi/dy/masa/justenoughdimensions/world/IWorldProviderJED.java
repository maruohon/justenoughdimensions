package fi.dy.masa.justenoughdimensions.world;

public interface IWorldProviderJED
{
    /**
     *  Set JED-specific WorldProvider properties on the client side from the previously synced properties data
     * @param tag
     */
    void setJEDProperties(JEDWorldProperties properties);

    /**
     * Returns true if the WorldInfo values have already been set for this WorldProvider
     * @return
     */
    boolean getWorldInfoHasBeenSet();

    /**
     * Returns true if the spawn point has already been set/moved, and the vanilla
     * spawn point search should be skipped.
     * @return
     */
    boolean getShouldSkipSpawnSearch();
}
