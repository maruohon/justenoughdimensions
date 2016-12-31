package fi.dy.masa.justenoughdimensions.world;

public class WorldProviderSurfaceJED extends WorldProviderJED
{
    @Override
    public boolean shouldMapSpin(String entity, double x, double y, double z)
    {
        return false;
    }
}
