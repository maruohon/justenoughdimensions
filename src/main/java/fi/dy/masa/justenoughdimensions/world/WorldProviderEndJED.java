package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldProviderEnd;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.justenoughdimensions.client.render.SkyRenderer;
import fi.dy.masa.justenoughdimensions.util.JEDStringUtils;
import fi.dy.masa.justenoughdimensions.world.util.WorldInfoUtils;
import fi.dy.masa.justenoughdimensions.world.util.WorldUtils;

public class WorldProviderEndJED extends WorldProviderEnd implements IWorldProviderJED
{
    private int skyRenderType = 0;
    private int skyDisableFlags = 0;
    protected Vec3d skyColor = null;
    protected Vec3d fogColor = null;
    protected Boolean canRespawnHere = null;
    protected Integer respawnDimension = null;
    protected float[] customLightBrightnessTable;
    private boolean worldInfoSet;

    @Override
    public boolean getWorldInfoHasBeenSet()
    {
        return this.worldInfoSet;
    }

    @Override
    public void setDimension(int dim)
    {
        super.setDimension(dim);

        // This method gets called the first time from DimensionManager.createProviderFor(),
        // at which time the world hasn't been set yet. The second call comes from the WorldServer
        // constructor, and there the world has just been set.
        if (this.world != null && this.getWorldInfoHasBeenSet() == false)
        {
            WorldInfoUtils.loadAndSetCustomWorldInfo(this.world);
            JEDWorldProperties.applyJEDWorldPropertiesToWorldProvider(this.world);
            //WorldUtils.overrideWorldProviderSettings(this.world, this);
            this.worldInfoSet = true;
        }
    }

    @Override
    public DimensionType getDimensionType()
    {
        DimensionType type = null;

        try
        {
            type = DimensionManager.getProviderType(this.getDimension());
        }
        catch (IllegalArgumentException e)
        {
        }

        return type != null ? type : super.getDimensionType();
    }

    @Override
    public void setJEDPropertiesFromNBT(NBTTagCompound tag)
    {
        if (tag != null)
        {
            if (tag.hasKey("SkyColor", Constants.NBT.TAG_STRING))    { this.skyColor = JEDStringUtils.hexStringToColor(tag.getString("SkyColor")); }
            if (tag.hasKey("FogColor", Constants.NBT.TAG_STRING))    { this.fogColor = JEDStringUtils.hexStringToColor(tag.getString("FogColor")); }
            if (tag.hasKey("SkyRenderType",   Constants.NBT.TAG_BYTE)) { this.skyRenderType   = tag.getByte("SkyRenderType");   }
            if (tag.hasKey("SkyDisableFlags", Constants.NBT.TAG_BYTE)) { this.skyDisableFlags = tag.getByte("SkyDisableFlags"); }

            if (tag.hasKey("LightBrightness", Constants.NBT.TAG_LIST))
            {
                NBTTagList list = tag.getTagList("LightBrightness", Constants.NBT.TAG_FLOAT);

                if (list.tagCount() == 16)
                {
                    this.customLightBrightnessTable = new float[16];

                    for (int i = 0; i < 16; i++)
                    {
                        this.customLightBrightnessTable[i] = list.getFloatAt(i);
                    }
                }
            }
        }

        if (tag != null && tag.hasKey("SkyRenderer", Constants.NBT.TAG_STRING))
        {
            WorldUtils.createSkyRendererFromName(this, tag.getString("SkyRenderer"));
        }
        else if (this.skyRenderType != 0)
        {
            this.setSkyRenderer(new SkyRenderer(this.skyRenderType, this.skyDisableFlags));
        }
        else
        {
            this.setSkyRenderer(null);
        }
    }

    @Override
    public void setJEDPropertiesFromWorldProperties(JEDWorldProperties properties)
    {
        this.customLightBrightnessTable = properties.getCustomLightBrightnessTable();
        this.canRespawnHere = properties.canRespawnHere();
        this.respawnDimension = properties.getRespawnDimension();
    }

    @Override
    public boolean canRespawnHere()
    {
        return this.canRespawnHere != null ? this.canRespawnHere : false;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player)
    {
        if (this.respawnDimension != null)
        {
            return this.respawnDimension;
        }
        else
        {
            return this.canRespawnHere() ? this.getDimension() : 0;
        }
    }

    @Override
    public float[] getLightBrightnessTable()
    {
        if (this.customLightBrightnessTable != null)
        {
            return this.customLightBrightnessTable;
        }

        return super.getLightBrightnessTable();
    }

    @Override
    public void setAllowedSpawnTypes(boolean allowHostile, boolean allowPeaceful)
    {
        // This fixes the custom dimensions being unable to spawn hostile mobs if the overworld is set to Peaceful
        // See Minecraft#runTick(), the call to this.world.setAllowedSpawnTypes(),
        // and also MinecraftServer#setDifficultyForAllWorlds()
        super.setAllowedSpawnTypes(this.world.getWorldInfo().getDifficulty() != EnumDifficulty.PEACEFUL, allowPeaceful);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isSkyColored()
    {
        return this.skyColor != null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getSkyColor(Entity entity, float partialTicks)
    {
        Vec3d skyColor = this.skyColor;
        if (skyColor == null)
        {
            return super.getSkyColor(entity, partialTicks);
        }

        float f1 = MathHelper.cos(this.world.getCelestialAngle(partialTicks) * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        int x = MathHelper.floor(entity.posX);
        int y = MathHelper.floor(entity.posY);
        int z = MathHelper.floor(entity.posZ);
        BlockPos blockpos = new BlockPos(x, y, z);
        int blendColour = net.minecraftforge.client.ForgeHooksClient.getSkyBlendColour(this.world, blockpos);
        float r = (float)((blendColour >> 16 & 255) / 255.0F * skyColor.x);
        float g = (float)((blendColour >>  8 & 255) / 255.0F * skyColor.y);
        float b = (float)((blendColour       & 255) / 255.0F * skyColor.z);
        r = r * f1;
        g = g * f1;
        b = b * f1;

        return new Vec3d(r, g, b);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getFogColor(float celestialAngle, float partialTicks)
    {
        Vec3d fogColor = this.fogColor;
        if (fogColor == null)
        {
            return super.getFogColor(celestialAngle, partialTicks);
        }

        float f = MathHelper.cos(celestialAngle * ((float)Math.PI * 2F)) * 2.0F + 0.5F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        float r = (float) fogColor.x;
        float g = (float) fogColor.y;
        float b = (float) fogColor.z;
        r = r * (f * 0.94F + 0.06F);
        g = g * (f * 0.94F + 0.06F);
        b = b * (f * 0.91F + 0.09F);
        return new Vec3d(r, g, b);
    }
}
