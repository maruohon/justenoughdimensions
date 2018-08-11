package fi.dy.masa.justenoughdimensions.world;

public interface IWorldProviderJED
{
    /**
     *  Set JED-specific WorldProvider properties on the client side from the previously synced properties data
     * @param tag
     */
    public void setJEDProperties(JEDWorldProperties properties);

    /**
     * Returns true if the WorldInfo values have already been set for this WorldProvider
     * @return
     */
    public boolean getWorldInfoHasBeenSet();
}
