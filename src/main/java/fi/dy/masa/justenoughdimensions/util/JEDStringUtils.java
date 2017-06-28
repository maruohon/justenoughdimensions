package fi.dy.masa.justenoughdimensions.util;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class JEDStringUtils
{
    public static String colorToHexString(Vec3d color)
    {
        int c = 0;

        c |= (int) ((MathHelper.clamp(color.z, 0, 1) * 0xFF));
        c |= (int) ((MathHelper.clamp(color.y, 0, 1) * 0xFF)) << 8;
        c |= (int) ((MathHelper.clamp(color.x, 0, 1) * 0xFF)) << 16;

        return String.format("%06X", c);
    }

    public static Vec3d intToColor(int i)
    {
        return new Vec3d((double) ((i >> 16) & 0xFF) / (double) 0xFF,
                         (double) ((i >>  8) & 0xFF) / (double) 0xFF,
                         (double) (        i & 0xFF) / (double) 0xFF);
    }

    public static Vec3d hexStringToColor(String colorStr)
    {
        return intToColor(hexStringToInt(colorStr));
    }

    public static int hexStringToInt(String colorStr)
    {
        int value = 0;

        try
        {
            if (colorStr.startsWith("0x"))
            {
                colorStr = colorStr.substring(2);
            }

            // Long so that the MSB being one (ie. would-be-negative-number) doesn't mess up things
            value = (int) Long.parseLong(colorStr, 16);
        }
        catch (NumberFormatException e) {}

        return value;
    }
}
