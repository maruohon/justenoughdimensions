package fi.dy.masa.justenoughdimensions.client.render;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SkyRenderer extends net.minecraftforge.client.IRenderHandler
{
    private static final ResourceLocation END_SKY_TEXTURES      = new ResourceLocation("textures/environment/end_sky.png");
    private static final ResourceLocation MOON_PHASES_TEXTURES  = new ResourceLocation("textures/environment/moon_phases.png");
    private static final ResourceLocation SUN_TEXTURES          = new ResourceLocation("textures/environment/sun.png");

    private int skyRenderType;
    private boolean disableSun;
    private boolean disableMoon;
    private boolean disableStars;
    private boolean vboEnabled;
    private int starGLCallList = -1;
    private int glSkyList = -1;
    private int glSkyList2 = -1;
    private final VertexFormat vertexBufferFormat;
    private net.minecraft.client.renderer.vertex.VertexBuffer starVBO;
    private net.minecraft.client.renderer.vertex.VertexBuffer skyVBO;
    private net.minecraft.client.renderer.vertex.VertexBuffer sky2VBO;

    public SkyRenderer(int skyRenderType, int disableFlags)
    {
        this.vboEnabled = OpenGlHelper.useVbo();
        this.vertexBufferFormat = new VertexFormat();
        this.vertexBufferFormat.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        this.skyRenderType = skyRenderType;
        this.disableSun   = (disableFlags & 0x01) != 0;
        this.disableMoon  = (disableFlags & 0x02) != 0;
        this.disableStars = (disableFlags & 0x04) != 0;
        this.generateStars();
        this.generateSky();
        this.generateSky2();
    }

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc)
    {
        if (this.skyRenderType == 2)
        {
            this.renderSkyEnd(mc);
        }
        else
        {
            this.renderSkySurface(world, mc, partialTicks);
        }
    }

    protected void renderSkyEnd(Minecraft mc)
    {
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.depthMask(false);
        mc.getTextureManager().bindTexture(END_SKY_TEXTURES);
        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer vertexbuffer = tessellator.getBuffer();

        for (int i = 0; i < 6; ++i)
        {
            GlStateManager.pushMatrix();

            if (i == 1)
            {
                GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            }

            if (i == 2)
            {
                GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            }

            if (i == 3)
            {
                GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
            }

            if (i == 4)
            {
                GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            }

            if (i == 5)
            {
                GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
            }

            vertexbuffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            vertexbuffer.pos(-100.0D, -100.0D, -100.0D).tex(0.0D, 0.0D).color(40, 40, 40, 255).endVertex();
            vertexbuffer.pos(-100.0D, -100.0D, 100.0D).tex(0.0D, 16.0D).color(40, 40, 40, 255).endVertex();
            vertexbuffer.pos(100.0D, -100.0D, 100.0D).tex(16.0D, 16.0D).color(40, 40, 40, 255).endVertex();
            vertexbuffer.pos(100.0D, -100.0D, -100.0D).tex(16.0D, 0.0D).color(40, 40, 40, 255).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
    }

    protected void renderSkySurface(WorldClient world, Minecraft mc, float partialTicks)
    {
        GlStateManager.disableTexture2D();
        Vec3d skyColor = world.getSkyColor(mc.getRenderViewEntity(), partialTicks);
        float r = (float)skyColor.xCoord;
        float g = (float)skyColor.yCoord;
        float b = (float)skyColor.zCoord;
        int pass = 2;

        if (pass != 2)
        {
            float f3 = (r * 30.0F + g * 59.0F + b * 11.0F) / 100.0F;
            float f4 = (r * 30.0F + g * 70.0F) / 100.0F;
            float f5 = (r * 30.0F + b * 70.0F) / 100.0F;
            r = f3;
            g = f4;
            b = f5;
        }

        GlStateManager.color(r, g, b);
        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer vertexbuffer = tessellator.getBuffer();
        GlStateManager.depthMask(false);
        GlStateManager.enableFog();
        GlStateManager.color(r, g, b);

        if (this.vboEnabled)
        {
            this.skyVBO.bindBuffer();
            GlStateManager.glEnableClientState(32884);
            GlStateManager.glVertexPointer(3, 5126, 12, 0);
            this.skyVBO.drawArrays(7);
            this.skyVBO.unbindBuffer();
            GlStateManager.glDisableClientState(32884);
        }
        else
        {
            GlStateManager.callList(this.glSkyList);
        }

        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderHelper.disableStandardItemLighting();
        float[] afloat = world.provider.calcSunriseSunsetColors(world.getCelestialAngle(partialTicks), partialTicks);

        if (afloat != null)
        {
            GlStateManager.disableTexture2D();
            GlStateManager.shadeModel(7425);
            GlStateManager.pushMatrix();
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(MathHelper.sin(world.getCelestialAngleRadians(partialTicks)) < 0.0F ? 180.0F : 0.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
            float rsr = afloat[0];
            float gsr = afloat[1];
            float bsr = afloat[2];

            if (pass != 2)
            {
                float f9 = (rsr * 30.0F + gsr * 59.0F + bsr * 11.0F) / 100.0F;
                float f10 = (rsr * 30.0F + gsr * 70.0F) / 100.0F;
                float f11 = (rsr * 30.0F + bsr * 70.0F) / 100.0F;
                rsr = f9;
                gsr = f10;
                bsr = f11;
            }

            vertexbuffer.begin(6, DefaultVertexFormats.POSITION_COLOR);
            vertexbuffer.pos(0.0D, 100.0D, 0.0D).color(rsr, gsr, bsr, afloat[3]).endVertex();

            for (int l = 0; l <= 16; ++l)
            {
                float f21 = (float)l * ((float)Math.PI * 2F) / 16.0F;
                float f12 = MathHelper.sin(f21);
                float f13 = MathHelper.cos(f21);
                vertexbuffer.pos((double)(f12 * 120.0F), (double)(f13 * 120.0F), (double)(-f13 * 40.0F * afloat[3])).color(afloat[0], afloat[1], afloat[2], 0.0F).endVertex();
            }

            tessellator.draw();
            GlStateManager.popMatrix();
            GlStateManager.shadeModel(7424);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.pushMatrix();
        float f16 = 1.0F - world.getRainStrength(partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, f16);
        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(world.getCelestialAngle(partialTicks) * 360.0F, 1.0F, 0.0F, 0.0F);

        double d17 = 30.0D;

        if (this.disableSun == false)
        {
            mc.getTextureManager().bindTexture(SUN_TEXTURES);
            vertexbuffer.begin(7, DefaultVertexFormats.POSITION_TEX);
            vertexbuffer.pos(-d17, 100.0D, -d17).tex(0.0D, 0.0D).endVertex();
            vertexbuffer.pos( d17, 100.0D, -d17).tex(1.0D, 0.0D).endVertex();
            vertexbuffer.pos( d17, 100.0D,  d17).tex(1.0D, 1.0D).endVertex();
            vertexbuffer.pos(-d17, 100.0D,  d17).tex(0.0D, 1.0D).endVertex();
            tessellator.draw();
        }

        if (this.disableMoon == false)
        {
            d17 = 20.0D;
            mc.getTextureManager().bindTexture(MOON_PHASES_TEXTURES);
            int i = world.getMoonPhase();
            int k = i % 4;
            int j = i / 4 % 2;
            double f22 = (double)(k + 0) / 4.0D;
            double f23 = (double)(j + 0) / 2.0D;
            double f24 = (double)(k + 1) / 4.0D;
            double f14 = (double)(j + 1) / 2.0D;
            vertexbuffer.begin(7, DefaultVertexFormats.POSITION_TEX);
            vertexbuffer.pos(-d17, -100.0D,  d17).tex(f24, f14).endVertex();
            vertexbuffer.pos( d17, -100.0D,  d17).tex(f22, f14).endVertex();
            vertexbuffer.pos( d17, -100.0D, -d17).tex(f22, f23).endVertex();
            vertexbuffer.pos(-d17, -100.0D, -d17).tex(f24, f23).endVertex();
            tessellator.draw();
        }

        if (this.disableStars == false)
        {
            GlStateManager.disableTexture2D();
            float brightness = world.getStarBrightness(partialTicks) * f16;

            if (brightness > 0.0F)
            {
                GlStateManager.color(brightness, brightness, brightness, brightness);

                if (this.vboEnabled)
                {
                    this.starVBO.bindBuffer();
                    GlStateManager.glEnableClientState(32884);
                    GlStateManager.glVertexPointer(3, 5126, 12, 0);
                    this.starVBO.drawArrays(7);
                    this.starVBO.unbindBuffer();
                    GlStateManager.glDisableClientState(32884);
                }
                else
                {
                    GlStateManager.callList(this.starGLCallList);
                }
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableFog();
        GlStateManager.popMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.0F, 0.0F, 0.0F);
        double d0 = mc.player.getPositionEyes(partialTicks).yCoord - world.getHorizon();

        if (d0 < 0.0D)
        {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, 12.0F, 0.0F);

            if (this.vboEnabled)
            {
                this.sky2VBO.bindBuffer();
                GlStateManager.glEnableClientState(32884);
                GlStateManager.glVertexPointer(3, 5126, 12, 0);
                this.sky2VBO.drawArrays(7);
                this.sky2VBO.unbindBuffer();
                GlStateManager.glDisableClientState(32884);
            }
            else
            {
                GlStateManager.callList(this.glSkyList2);
            }

            GlStateManager.popMatrix();
            double d19 = -(d0 + 65.0D);
            vertexbuffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            vertexbuffer.pos(-1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos(-1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            vertexbuffer.pos( 1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            tessellator.draw();
        }

        if (world.provider.isSkyColored())
        {
            GlStateManager.color(r * 0.2F + 0.04F, g * 0.2F + 0.04F, b * 0.6F + 0.1F);
        }
        else
        {
            GlStateManager.color(r, g, b);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, -((float)(d0 - 16.0D)), 0.0F);
        GlStateManager.callList(this.glSkyList2);
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
    }

    private void generateSky2()
    {
        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer vertexbuffer = tessellator.getBuffer();

        if (this.sky2VBO != null)
        {
            this.sky2VBO.deleteGlBuffers();
        }

        if (this.glSkyList2 >= 0)
        {
            GLAllocation.deleteDisplayLists(this.glSkyList2);
            this.glSkyList2 = -1;
        }

        if (this.vboEnabled)
        {
            this.sky2VBO = new net.minecraft.client.renderer.vertex.VertexBuffer(this.vertexBufferFormat);
            this.renderSky(vertexbuffer, -16.0F, true);
            vertexbuffer.finishDrawing();
            vertexbuffer.reset();
            this.sky2VBO.bufferData(vertexbuffer.getByteBuffer());
        }
        else
        {
            this.glSkyList2 = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(this.glSkyList2, 4864);
            this.renderSky(vertexbuffer, -16.0F, true);
            tessellator.draw();
            GlStateManager.glEndList();
        }
    }

    private void generateSky()
    {
        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer vertexbuffer = tessellator.getBuffer();

        if (this.skyVBO != null)
        {
            this.skyVBO.deleteGlBuffers();
        }

        if (this.glSkyList >= 0)
        {
            GLAllocation.deleteDisplayLists(this.glSkyList);
            this.glSkyList = -1;
        }

        if (this.vboEnabled)
        {
            this.skyVBO = new net.minecraft.client.renderer.vertex.VertexBuffer(this.vertexBufferFormat);
            this.renderSky(vertexbuffer, 16.0F, false);
            vertexbuffer.finishDrawing();
            vertexbuffer.reset();
            this.skyVBO.bufferData(vertexbuffer.getByteBuffer());
        }
        else
        {
            this.glSkyList = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(this.glSkyList, 4864);
            this.renderSky(vertexbuffer, 16.0F, false);
            tessellator.draw();
            GlStateManager.glEndList();
        }
    }

    private void renderSky(VertexBuffer worldRendererIn, float posY, boolean reverseX)
    {
        worldRendererIn.begin(7, DefaultVertexFormats.POSITION);

        for (int k = -384; k <= 384; k += 64)
        {
            for (int l = -384; l <= 384; l += 64)
            {
                float f = (float)k;
                float f1 = (float)(k + 64);

                if (reverseX)
                {
                    f1 = (float)k;
                    f = (float)(k + 64);
                }

                worldRendererIn.pos((double)f, (double)posY, (double)l).endVertex();
                worldRendererIn.pos((double)f1, (double)posY, (double)l).endVertex();
                worldRendererIn.pos((double)f1, (double)posY, (double)(l + 64)).endVertex();
                worldRendererIn.pos((double)f, (double)posY, (double)(l + 64)).endVertex();
            }
        }
    }

    private void generateStars()
    {
        Tessellator tessellator = Tessellator.getInstance();
        VertexBuffer vertexbuffer = tessellator.getBuffer();

        if (this.starVBO != null)
        {
            this.starVBO.deleteGlBuffers();
        }

        if (this.starGLCallList >= 0)
        {
            GLAllocation.deleteDisplayLists(this.starGLCallList);
            this.starGLCallList = -1;
        }

        if (this.vboEnabled)
        {
            this.starVBO = new net.minecraft.client.renderer.vertex.VertexBuffer(this.vertexBufferFormat);
            this.renderStars(vertexbuffer);
            vertexbuffer.finishDrawing();
            vertexbuffer.reset();
            this.starVBO.bufferData(vertexbuffer.getByteBuffer());
        }
        else
        {
            this.starGLCallList = GLAllocation.generateDisplayLists(1);
            GlStateManager.pushMatrix();
            GlStateManager.glNewList(this.starGLCallList, 4864);
            this.renderStars(vertexbuffer);
            tessellator.draw();
            GlStateManager.glEndList();
            GlStateManager.popMatrix();
        }
    }

    private void renderStars(VertexBuffer worldRendererIn)
    {
        Random random = new Random(10842L);
        worldRendererIn.begin(7, DefaultVertexFormats.POSITION);

        for (int i = 0; i < 1500; ++i)
        {
            double d0 = (double)(random.nextFloat() * 2.0F - 1.0F);
            double d1 = (double)(random.nextFloat() * 2.0F - 1.0F);
            double d2 = (double)(random.nextFloat() * 2.0F - 1.0F);
            double d3 = (double)(0.15F + random.nextFloat() * 0.1F);
            double d4 = d0 * d0 + d1 * d1 + d2 * d2;

            if (d4 < 1.0D && d4 > 0.01D)
            {
                d4 = 1.0D / Math.sqrt(d4);
                d0 = d0 * d4;
                d1 = d1 * d4;
                d2 = d2 * d4;
                double d5 = d0 * 100.0D;
                double d6 = d1 * 100.0D;
                double d7 = d2 * 100.0D;
                double d8 = Math.atan2(d0, d2);
                double d9 = Math.sin(d8);
                double d10 = Math.cos(d8);
                double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                double d12 = Math.sin(d11);
                double d13 = Math.cos(d11);
                double d14 = random.nextDouble() * Math.PI * 2.0D;
                double d15 = Math.sin(d14);
                double d16 = Math.cos(d14);

                for (int j = 0; j < 4; ++j)
                {
                    double d18 = (double)((j & 2) - 1) * d3;
                    double d19 = (double)((j + 1 & 2) - 1) * d3;
                    double d21 = d18 * d16 - d19 * d15;
                    double d22 = d19 * d16 + d18 * d15;
                    double d23 = d21 * d12 + 0.0D * d13;
                    double d24 = 0.0D * d12 - d21 * d13;
                    double d25 = d24 * d9 - d22 * d10;
                    double d26 = d22 * d9 + d24 * d10;
                    worldRendererIn.pos(d5 + d25, d6 + d23, d7 + d26).endVertex();
                }
            }
        }
    }
}
