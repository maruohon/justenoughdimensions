package fi.dy.masa.justenoughdimensions.world;

import com.google.gson.JsonObject;

public interface IWorldProviderJED
{
    /**
     *  Set JED-specific WorldProvider properties on the client side from a synced JSON object
     * @param tag
     */
    public void setJEDPropertiesFromJson(JsonObject obj);

    /**
     * Returns true if the WorldInfo values have already been set for this WorldProvider
     * @return
     */
    public boolean getWorldInfoHasBeenSet();
}
