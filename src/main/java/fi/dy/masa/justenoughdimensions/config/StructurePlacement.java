package fi.dy.masa.justenoughdimensions.config;

import java.io.File;
import javax.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.justenoughdimensions.util.JEDJsonUtils;

public class StructurePlacement
{
    private static final String STRUCTURE_DIR = "structures";

    private final File file;
    private final boolean centered;
    private final BlockPos offset;
    private final Rotation rotation;
    private final Mirror mirror;
    private final int loadRangeAround;

    private StructurePlacement(File file, Rotation rotation, Mirror mirror, boolean centered, BlockPos offset, int loadRangeAround)
    {
        this.file = file;
        this.rotation = rotation;
        this.mirror = mirror;
        this.centered = centered;
        this.offset = offset;
        this.loadRangeAround = loadRangeAround;
    }

    public static File getStructureDirectory()
    {
        return new File(DimensionConfig.instance().getGlobalJEDConfigDir(), STRUCTURE_DIR);
    }

    public File getFile()
    {
        return this.file;
    }

    public Rotation getRotation()
    {
        return this.rotation;
    }

    public Mirror getMirror()
    {
        return this.mirror;
    }

    public boolean isCentered()
    {
        return this.centered;
    }

    public BlockPos getOffset()
    {
        return this.offset;
    }

    public int getLoadRangeAround()
    {
        return this.loadRangeAround;
    }

    private static Rotation getRotationFromName(String name)
    {
        if (name.equals("cw_90"))
        {
            return Rotation.CLOCKWISE_90;
        }
        else if (name.equals("cw_180"))
        {
            return Rotation.CLOCKWISE_180;
        }
        else if (name.equals("ccw_90"))
        {
            return Rotation.COUNTERCLOCKWISE_90;
        }

        return Rotation.NONE;
    }

    private static Mirror getMirrorFromName(String name)
    {
        if (name.equals("front_back"))
        {
            return Mirror.FRONT_BACK;
        }
        else if (name.equals("left_right"))
        {
            return Mirror.LEFT_RIGHT;
        }

        return Mirror.NONE;
    }

    @Nullable
    public static StructurePlacement fromJson(JsonObject obj)
    {
        if (JEDJsonUtils.hasString(obj, "name"))
        {
            String name = obj.get("name").getAsString();
            File dir = getStructureDirectory();
            File file = new File(dir, name);
            boolean centered = JEDJsonUtils.getBooleanOrDefault(obj, "centered", false);
            Rotation rotation = Rotation.NONE;
            Mirror mirror = Mirror.NONE;
            BlockPos offset = BlockPos.ORIGIN;
            int loadAround = MathHelper.clamp(JEDJsonUtils.getIntegerOrDefault(obj, "load_around", 16), 0, 128);

            if (JEDJsonUtils.hasString(obj, "rotation"))
            {
                rotation = getRotationFromName(obj.get("rotation").getAsString());
            }

            if (JEDJsonUtils.hasString(obj, "mirror"))
            {
                mirror = getMirrorFromName(obj.get("mirror").getAsString());
            }

            if (JEDJsonUtils.hasArray(obj, "offset"))
            {
                JsonArray arr = obj.get("offset").getAsJsonArray();

                if (arr.size() == 3)
                {
                    offset = new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
                }
            }

            return new StructurePlacement(file, rotation, mirror, centered, offset, loadAround);
        }

        return null;
    }

    public enum StructureType
    {
        STRUCTURE   (".nbt"),
        SCHEMATIC   (".schematic"),
        INVALID     ("");

        private final String extension;

        private StructureType(String extension)
        {
            this.extension = extension;
        }

        public String getExtension()
        {
            return this.extension;
        }

        public static StructureType fromFileName(String fileName)
        {
            if (fileName.endsWith(".nbt"))
            {
                return STRUCTURE;
            }
            else if (fileName.endsWith(".schematic"))
            {
                return SCHEMATIC;
            }

            return INVALID;
        }
    }
}
