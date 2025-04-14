package fi.dy.masa.justenoughdimensions.client.render;

import java.util.Random;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
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
    private SkySettings skySettings;
    private boolean vboEnabled;
    private int starGLCallList = -1;
    private int glSkyList = -1;
    private int glSkyList2 = -1;
    private final VertexFormat vertexBufferFormat;
    private net.minecraft.client.renderer.vertex.VertexBuffer starVBO;
    private net.minecraft.client.renderer.vertex.VertexBuffer skyVBO;
    private net.minecraft.client.renderer.vertex.VertexBuffer sky2VBO;

    public static class SkySettings
    {
        public boolean disableSun;
        public boolean disableMoon;
        public boolean disableStars;
        public float moonScale;
        public float sunScale;
        public Vec3d moonColor;
        public Vec3d sunColor;
    }

    public SkyRenderer(int skyRenderType, SkySettings settings)
    {
        this.vboEnabled = OpenGlHelper.useVbo();
        this.vertexBufferFormat = new VertexFormat();
        this.vertexBufferFormat.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        this.skyRenderType = skyRenderType;
        this.skySettings = settings;
        this.generateStars();
        this.generateSky();
        this.generateSky2();
    }

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc)
    {
        if(ShaderCheck.isShaderOn()) { //It's preferable not to draw the sky than fight Optifine 
            return;
        }
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
        BufferBuilder bufferBuilder = tessellator.getBuffer();

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

            bufferBuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            bufferBuilder.pos(-100.0D, -100.0D, -100.0D).tex(0.0D, 0.0D).color(40, 40, 40, 255).endVertex();
            bufferBuilder.pos(-100.0D, -100.0D, 100.0D).tex(0.0D, 16.0D).color(40, 40, 40, 255).endVertex();
            bufferBuilder.pos(100.0D, -100.0D, 100.0D).tex(16.0D, 16.0D).color(40, 40, 40, 255).endVertex();
            bufferBuilder.pos(100.0D, -100.0D, -100.0D).tex(16.0D, 0.0D).color(40, 40, 40, 255).endVertex();
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
        float r = (float)skyColor.x;
        float g = (float)skyColor.y;
        float b = (float)skyColor.z;

        GlStateManager.color(r, g, b);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
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

            bufferBuilder.begin(6, DefaultVertexFormats.POSITION_COLOR);
            bufferBuilder.pos(0.0D, 100.0D, 0.0D).color(rsr, gsr, bsr, afloat[3]).endVertex();

            for (int l = 0; l <= 16; ++l)
            {
                float f21 = (float)l * ((float)Math.PI * 2F) / 16.0F;
                float f12 = MathHelper.sin(f21);
                float f13 = MathHelper.cos(f21);
                bufferBuilder.pos((double)(f12 * 120.0F), (double)(f13 * 120.0F), (double)(-f13 * 40.0F * afloat[3])).color(afloat[0], afloat[1], afloat[2], 0.0F).endVertex();
            }

            tessellator.draw();
            GlStateManager.popMatrix();
            GlStateManager.shadeModel(7424);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.pushMatrix();
        float rainFade = 1.0F - world.getRainStrength(partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, rainFade);
        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(world.getCelestialAngle(partialTicks) * 360.0F, 1.0F, 0.0F, 0.0F);

        double radius = 0.0D;

        if (this.skySettings.disableSun == false)
        {
            float sunR = (float) this.skySettings.sunColor.x;
            float sunG = (float) this.skySettings.sunColor.y;
            float sunB = (float) this.skySettings.sunColor.z;
            radius = 30.0D * this.skySettings.sunScale;

            GlStateManager.color(sunR, sunG, sunB, rainFade);
            mc.getTextureManager().bindTexture(SUN_TEXTURES);

            bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            bufferBuilder.pos(-radius, 100.0D, -radius).tex(0.0D, 0.0D).endVertex();
            bufferBuilder.pos( radius, 100.0D, -radius).tex(1.0D, 0.0D).endVertex();
            bufferBuilder.pos( radius, 100.0D,  radius).tex(1.0D, 1.0D).endVertex();
            bufferBuilder.pos(-radius, 100.0D,  radius).tex(0.0D, 1.0D).endVertex();
            tessellator.draw();
        }

        if (this.skySettings.disableMoon == false)
        {
            float moonR = (float) this.skySettings.moonColor.x;
            float moonG = (float) this.skySettings.moonColor.y;
            float moonB = (float) this.skySettings.moonColor.z;
            radius = 20.0D * this.skySettings.moonScale;

            GlStateManager.color(moonR, moonG, moonB, rainFade);
            mc.getTextureManager().bindTexture(MOON_PHASES_TEXTURES);

            int i = world.getMoonPhase();
            int k = i % 4;
            int j = i / 4 % 2;
            double f22 = (double)(k + 0) / 4.0D;
            double f23 = (double)(j + 0) / 2.0D;
            double f24 = (double)(k + 1) / 4.0D;
            double f14 = (double)(j + 1) / 2.0D;

            bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            bufferBuilder.pos(-radius, -100.0D,  radius).tex(f24, f14).endVertex();
            bufferBuilder.pos( radius, -100.0D,  radius).tex(f22, f14).endVertex();
            bufferBuilder.pos( radius, -100.0D, -radius).tex(f22, f23).endVertex();
            bufferBuilder.pos(-radius, -100.0D, -radius).tex(f24, f23).endVertex();
            tessellator.draw();
        }

        if (this.skySettings.disableStars == false)
        {
            GlStateManager.disableTexture2D();
            float brightness = world.getStarBrightness(partialTicks) * rainFade;

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
        double d0 = mc.player.getPositionEyes(partialTicks).y - world.getHorizon();

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
            bufferBuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
            bufferBuilder.pos(-1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D,   d19, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D,   d19,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos(-1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D, -1.0D,  1.0D).color(0, 0, 0, 255).endVertex();
            bufferBuilder.pos( 1.0D, -1.0D, -1.0D).color(0, 0, 0, 255).endVertex();
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
        BufferBuilder bufferBuilder = tessellator.getBuffer();

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
            this.renderSky(bufferBuilder, -16.0F, true);
            bufferBuilder.finishDrawing();
            bufferBuilder.reset();
            this.sky2VBO.bufferData(bufferBuilder.getByteBuffer());
        }
        else
        {
            this.glSkyList2 = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(this.glSkyList2, 4864);
            this.renderSky(bufferBuilder, -16.0F, true);
            tessellator.draw();
            GlStateManager.glEndList();
        }
    }

    private void generateSky()
    {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();

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
            this.renderSky(bufferBuilder, 16.0F, false);
            bufferBuilder.finishDrawing();
            bufferBuilder.reset();
            this.skyVBO.bufferData(bufferBuilder.getByteBuffer());
        }
        else
        {
            this.glSkyList = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(this.glSkyList, 4864);
            this.renderSky(bufferBuilder, 16.0F, false);
            tessellator.draw();
            GlStateManager.glEndList();
        }
    }

    private void renderSky(BufferBuilder bufferBuilder, float posY, boolean reverseX)
    {
        bufferBuilder.begin(7, DefaultVertexFormats.POSITION);

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

                bufferBuilder.pos((double)f, (double)posY, (double)l).endVertex();
                bufferBuilder.pos((double)f1, (double)posY, (double)l).endVertex();
                bufferBuilder.pos((double)f1, (double)posY, (double)(l + 64)).endVertex();
                bufferBuilder.pos((double)f, (double)posY, (double)(l + 64)).endVertex();
            }
        }
    }

    private void generateStars()
    {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();

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
            this.renderStars(bufferBuilder);
            bufferBuilder.finishDrawing();
            bufferBuilder.reset();
            this.starVBO.bufferData(bufferBuilder.getByteBuffer());
        }
        else
        {
            this.starGLCallList = GLAllocation.generateDisplayLists(1);
            GlStateManager.pushMatrix();
            GlStateManager.glNewList(this.starGLCallList, 4864);
            this.renderStars(bufferBuilder);
            tessellator.draw();
            GlStateManager.glEndList();
            GlStateManager.popMatrix();
        }
    }

    private void renderStars(BufferBuilder bufferBuilder)
    {
        Random random = new Random(10842L);
        bufferBuilder.begin(7, DefaultVertexFormats.POSITION);

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
                    bufferBuilder.pos(d5 + d25, d6 + d23, d7 + d26).endVertex();
                }
            }
        }
    }
}
