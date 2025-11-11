package io.singularitynet.MissionHandlers;

/*
 Short description
 -----------------
 ColourMapProducerImplementation enables segmentation rendering by:
 - Enabling colour-map mode in TextureHelper during prepare().
 - Supplying entity (mob) and miscellaneous texture colour maps from the
   mission parameters, plus defaults for sun/moon.
 - Optionally setting a solid-colour sky (BlankSkyRenderer placeholder).
 - When a frame is requested, the current framebuffer is read out as BGRA
   bytes and returned (the actual flat-colour rendering is applied by
   TextureHelper + mixins + annotate shader during the render pass).

 See TextureHelper for details of how textures/entities are mapped to colours
 and how the annotate shader is driven.
*/

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.singularitynet.MissionHandlerInterfaces.IVideoProducer;
import io.singularitynet.projectmalmo.ColourMapProducer;
import io.singularitynet.projectmalmo.EntityTypes;
import io.singularitynet.projectmalmo.MissionInit;
import io.singularitynet.projectmalmo.MobWithColour;
import io.singularitynet.utils.TextureHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL12.GL_BGRA;


public class ColourMapProducerImplementation extends HandlerBase implements IVideoProducer {
    private ColourMapProducer cmParams;
    private Framebuffer fbo;
    private Map<String, Integer> mobColours = new HashMap<String, Integer>();
    private Map<String, Integer> miscColours = new HashMap<String, Integer>();
    
    @Override
    public boolean parseParameters(Object params)
    {

        if (params == null || !(params instanceof ColourMapProducer))
            return false;
        this.cmParams = (ColourMapProducer)params;
        for (MobWithColour mob : this.cmParams.getColourSpec())
        {
            byte[] col = mob.getColour();
            int c = (col[2] & 0xff) + ((col[1] & 0xff) << 8) + ((col[0] & 0xff) << 16);
            for (EntityTypes ent : mob.getType())
            {
                String mobName = ent.value();
                this.mobColours.put(mobName, c);
            }
        }
        miscColours.put("textures/environment/sun.png", 0xffff00);
        miscColours.put("textures/environment/moon_phases.png", 0xffffff);
        return true;
    }
    

    @Override
    public VideoType getVideoType()
    {
        return VideoType.COLOUR_MAP;
    }

    @Override
    public int getWidth()
    {
        return this.cmParams.getWidth();
    }

    @Override
    public int getHeight()
    {
        return this.cmParams.getHeight();
    }

    public int getRequiredBufferSize()
    {
        // We transmit BGRA pixels (4 bytes per pixel) to match the sender buffer sizing.
        return this.getWidth() * this.getHeight() * 4;
    }

    @Override
    public int[] writeFrame(MissionInit missionInit, ByteBuffer buffer)
    {
        // All the complicated work is done inside TextureHelper - by this point, all we need do
        // is grab the contents of the Minecraft framebuffer.
        final int width = getWidth();
        final int height = getHeight();

        // Render the Minecraft frame into our own FBO, at the desired size:
        Framebuffer framebuffer = TextureHelper.getSegmentationFramebuffer();
        if (framebuffer == null) {
            framebuffer = MinecraftClient.getInstance().getFramebuffer();
        }

        // Read the current framebuffer into the supplied buffer as BGRA bytes.
        int i = framebuffer.textureWidth;
        int j = framebuffer.textureHeight;
        GlStateManager._readPixels(0, 0, i, j, GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);

        int[] sizes = new int[2];
        sizes[0] = i;
        sizes[1] = j;
        return sizes;

    }
    
    @Override
    public void prepare(MissionInit missionInit)
    {
        // this.fbo = new Framebuffer(this.getWidth(), this.getHeight(), true);
        TextureHelper.setIsProducingColourMap(true);
        TextureHelper.setMobColours(this.mobColours);
        TextureHelper.setMiscTextureColours(this.miscColours);
        TextureHelper.setSkyRenderer(new TextureHelper.BlankSkyRenderer(this.cmParams.getSkyColour()));
    }

    @Override
    public void cleanup()
    {
        TextureHelper.setIsProducingColourMap(false);
        if (this.fbo != null) {
            this.fbo.delete(); // Must do this or we leak resources.
            this.fbo = null;
        }
    }
    
}
