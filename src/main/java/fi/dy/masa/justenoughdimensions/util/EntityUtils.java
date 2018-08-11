package fi.dy.masa.justenoughdimensions.util;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;

public class EntityUtils
{
    @Nullable
    public static <T extends Entity> T findEntityByUUID(List<T> list, UUID uuid)
    {
        if (uuid == null)
        {
            return null;
        }

        for (T entity : list)
        {
            if (entity.getUniqueID().equals(uuid))
            {
                return entity;
            }
        }

        return null;
    }
}
