package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProviderEnd;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import fi.dy.masa.justenoughdimensions.client.render.SkyRenderer;

public class WorldProviderEndJED extends WorldProviderEnd implements IWorldProviderJED
{
    private int skyRenderType = 0;
    protected Vec3d skyColor = null;
    protected Vec3d fogColor = null;

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
            if (tag.hasKey("SkyColor", Constants.NBT.TAG_STRING))    { this.skyColor = WorldInfoJED.hexStringToColor(tag.getString("SkyColor")); }
            if (tag.hasKey("FogColor", Constants.NBT.TAG_STRING))    { this.fogColor = WorldInfoJED.hexStringToColor(tag.getString("FogColor")); }
            if (tag.hasKey("SkyRenderType", Constants.NBT.TAG_BYTE)) { this.skyRenderType = tag.getByte("SkyRenderType"); }

            if (this.skyRenderType != 0)
            {
                this.setSkyRenderer(new SkyRenderer(this.skyRenderType));
            }
            else
            {
                this.setSkyRenderer(null);
            }
        }
    }

    @Override
    public void setJEDPropertiesFromWorldInfo(WorldInfoJED worldInfo)
    {
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
        float r = (float)((blendColour >> 16 & 255) / 255.0F * skyColor.xCoord);
        float g = (float)((blendColour >>  8 & 255) / 255.0F * skyColor.yCoord);
        float b = (float)((blendColour       & 255) / 255.0F * skyColor.zCoord);
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
        float r = (float) fogColor.xCoord;
        float g = (float) fogColor.yCoord;
        float b = (float) fogColor.zCoord;
        r = r * (f * 0.94F + 0.06F);
        g = g * (f * 0.94F + 0.06F);
        b = b * (f * 0.91F + 0.09F);
        return new Vec3d(r, g, b);
    }
}
