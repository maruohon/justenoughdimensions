package fi.dy.masa.justenoughdimensions.util;

import java.io.File;
import com.google.common.io.Files;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;

public class ConfigFileUtils
{
    public static void createDirIfNotExists(File dir)
    {
        if (dir.exists() == false)
        {
            try
            {
                dir.mkdirs();
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to create the directory '{}'", dir.getAbsolutePath(), e);
            }
        }
    }

    public static void tryCopyOrMoveConfigIfMissingOrOlder(File fileToReplace, File replacementFile, FileAction action, ConfigComparator configComparator)
    {
        if (replacementFile.exists() && replacementFile.isFile() && replacementFile.canRead() &&
            (fileToReplace.exists() == false || configComparator.shouldReplace(fileToReplace, replacementFile)))
        {
            try
            {
                if (action == FileAction.COPY)
                {
                    JustEnoughDimensions.logger.info("Copying the file '{}' to the new location '{}'",
                            replacementFile.getAbsolutePath(), fileToReplace.getAbsolutePath());

                    Files.copy(replacementFile, fileToReplace);
                }
                else if (action == FileAction.MOVE)
                {
                    JustEnoughDimensions.logger.info("Moving the file '{}' to the new location '{}'",
                            replacementFile.getAbsolutePath(), fileToReplace.getAbsolutePath());

                    Files.move(replacementFile, fileToReplace);
                }
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to {} the file '{}' to the new location '{}'",
                        action.name().toLowerCase(), replacementFile.getAbsolutePath(), fileToReplace.getAbsolutePath(), e);
            }
        }
    }

    public static abstract class ConfigComparator
    {
        public abstract boolean shouldReplace(File fileToReplace, File replacementFile);
    }

    public enum FileAction
    {
        COPY,
        MOVE;
    }
}
