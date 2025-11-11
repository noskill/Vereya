package io.singularitynet.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/*
 Short description
 -----------------
 TextureHelper coordinates colour-map (segmentation) rendering:
 - State: tracks if colour-map mode is active, and the entity currently being
   rendered. Holds colour maps for entities (mob types) and miscellaneous
   textures (eg sun, moon) configured by the mission.
 - Mixins:
   * EntityRenderDispatcherMixin sets/clears the current entity around each
     entity render call.
   * TextureManagerMixin hooks after texture binding and calls
     onTextureBound(id), which activates the annotate shader and sets uniforms
     to output a flat colour.
 - Shader: annotate.vsh/annotate.fsh is loaded once. Uniforms entityColourR/G/B
   are set to:
     * a solid RGB for entities (from map or deterministic fallback),
     * a solid RGB for known misc textures,
     * or -1 to signal the block atlas path (shader uses UVs to derive colour).
 - Extensibility: use setMobColours and setMiscTextureColours with keys:
     * mob/entity: by entity type string from mission config,
     * misc textures: by Identifier path, e.g. "textures/environment/sun.png".
 The frame readout happens in ColourMapProducerImplementation; this class ensures
 the scene is rendered in segmentation colours before the pixels are read.
*/

public class TextureHelper {
    // Indicates whether a colour-map render pass is active this frame.
    public static volatile boolean colourmapFrame = false;

    private static volatile boolean isProducingColourMap = false;

    // Optional mapping from mob/entity identifiers to colours.
    private static Map<String, Integer> idealMobColours = null;
    // Optional mapping from texture identifiers (paths) to colours for misc elements (eg sun/moon).
    private static Map<String, Integer> miscTexturesToColours = null;

    // Placeholder sky renderer reference (no-op in this port).
    private static Object blankSkyRenderer = null;

    // Current entity being rendered (set via mixin).
    private static Entity currentEntity = null;

    // Shader handling
    private static int shaderProgram = -1;
    private static boolean initialised = false;
    private static int uniformR = -1;
    private static int uniformG = -1;
    private static int uniformB = -1;

    // Off-screen framebuffer used for the segmentation pass.
    private static SimpleFramebuffer segmentationFbo = null;

    public static void setIsProducingColourMap(boolean usemap) {
        isProducingColourMap = usemap;
    }

    public static boolean isProducingColourMap() {
        return isProducingColourMap;
    }

    /**
     * Set preferred mob colours, keyed by entity type string.
     */
    public static void setMobColours(Map<String, Integer> mobColours) {
        if (mobColours == null || mobColours.isEmpty()) {
            idealMobColours = null;
        } else {
            idealMobColours = new HashMap<>(mobColours);
        }
    }

    /**
     * Set colours for miscellaneous textures, keyed by texture path string.
     */
    public static void setMiscTextureColours(Map<String, Integer> miscColours) {
        if (miscColours == null || miscColours.isEmpty()) {
            miscTexturesToColours = null;
        } else {
            miscTexturesToColours = new HashMap<>(miscColours);
        }
    }

    public static void setCurrentEntity(Entity entity) {
        currentEntity = entity;
    }

    /**
     * Returns an ARGB colour for a given entity based on configured mappings.
     * If no explicit mapping exists, returns a deterministic hash-based colour.
     */
    public static int getColourForEntity(Entity entity) {
        if (entity == null) return 0x000000;
        String key = entity.getType().toString();
        if (idealMobColours != null) {
            Integer col = idealMobColours.get(key);
            if (col != null) return (0xFF000000 | (col & 0x00FFFFFF));
        }
        // Fallback: deterministic per-entity-type colour.
        int hash = key.hashCode();
        int rgb = (hash & 0x00FFFFFF);
        return 0xFF000000 | rgb;
    }

    /**
     * Returns an ARGB colour for a given texture identifier if one was configured.
     * Returns -1 if no mapping exists.
     */
    public static int getColourForTexture(Identifier id) {
        if (id == null || miscTexturesToColours == null) return -1;
        Integer col = miscTexturesToColours.get(id.getPath());
        if (col == null) return -1;
        return 0xFF000000 | (col & 0x00FFFFFF);
    }

    /**
     * Placeholder to mirror Malmo API. In this port it stores the value but does not affect rendering.
     */
    public static void setSkyRenderer(Object skyRenderer) {
        blankSkyRenderer = skyRenderer;
    }

    public static Object getSkyRenderer() {
        return blankSkyRenderer;
    }

    // Minimal placeholder that mirrors Malmo's BlankSkyRenderer concept.
    // In Fabric 1.21 the sky is rendered inside WorldRenderer; we keep this to
    // preserve API shape used by ColourMapProducerImplementation.
    public static class BlankSkyRenderer {
        public final int r;
        public final int g;
        public final int b;

        public BlankSkyRenderer(byte[] rgb) {
            // Expecting 3 bytes [R,G,B].
            int R = rgb.length > 0 ? (rgb[0] & 0xFF) : 0;
            int G = rgb.length > 1 ? (rgb[1] & 0xFF) : 0;
            int B = rgb.length > 2 ? (rgb[2] & 0xFF) : 0;
            this.r = R;
            this.g = G;
            this.b = B;
        }
    }

    // Called from mixin when a texture is bound. Decides the uniform colour and
    // ensures the shader is active while producing a colour map.
    public static void onTextureBound(Identifier id) {
        if (!isProducingColourMap || !colourmapFrame) {
            GL20.glUseProgram(0);
            return;
        }
        ensureInitialised();
        int col = 0;
        if (currentEntity != null) {
            col = getColourForEntity(currentEntity) & 0x00FFFFFF;
        } else {
            int misc = getColourForTexture(id);
            if (misc != -1) {
                col = misc & 0x00FFFFFF;
            } else if (SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE.equals(id)) {
                col = -1; // signal shader to use UV-based colouring
            } else {
                // default: black
                col = 0;
            }
        }

        GL20.glUseProgram(shaderProgram);
        if (uniformR != -1 && uniformG != -1 && uniformB != -1) {
            if (col == -1) {
                GL20.glUniform1i(uniformR, -1);
                GL20.glUniform1i(uniformG, -1);
                GL20.glUniform1i(uniformB, -1);
            } else {
                GL20.glUniform1i(uniformR, (col >> 16) & 0xFF);
                GL20.glUniform1i(uniformG, (col >> 8) & 0xFF);
                GL20.glUniform1i(uniformB, col & 0xFF);
            }
        }
    }

    private static void ensureInitialised() {
        if (initialised) return;
        shaderProgram = createProgram("annotate");
        if (shaderProgram <= 0) {
            initialised = true; // avoid retry loop; no shader
            return;
        }
        uniformR = GL20.glGetUniformLocation(shaderProgram, "entityColourR");
        uniformG = GL20.glGetUniformLocation(shaderProgram, "entityColourG");
        uniformB = GL20.glGetUniformLocation(shaderProgram, "entityColourB");
        initialised = true;
    }

    private static int loadShader(String filename, int shaderType) {
        try (InputStream stream = TextureHelper.class.getClassLoader().getResourceAsStream(filename)) {
            if (stream == null) return -1;
            String src;
            try (BufferedInputStream bis = new BufferedInputStream(stream)) {
                byte[] bytes = bis.readAllBytes();
                src = new String(bytes, StandardCharsets.UTF_8);
            }
            int shader = GL20.glCreateShader(shaderType);
            GL20.glShaderSource(shader, src);
            GL20.glCompileShader(shader);
            int status = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
            if (status == GL11.GL_FALSE) {
                String info = GL20.glGetShaderInfoLog(shader);
                System.out.println("Shader compile error (" + filename + "): " + info);
                GL20.glDeleteShader(shader);
                return -1;
            }
            return shader;
        } catch (Exception e) {
            System.out.println("Failed to load shader: " + filename + ": " + e.getMessage());
            return -1;
        }
    }

    private static int createProgram(String baseName) {
        int prog = GL20.glCreateProgram();
        int v = loadShader(baseName + ".vsh", GL20.GL_VERTEX_SHADER);
        int f = loadShader(baseName + ".fsh", GL20.GL_FRAGMENT_SHADER);
        if (v <= 0 || f <= 0) {
            if (v > 0) GL20.glDeleteShader(v);
            if (f > 0) GL20.glDeleteShader(f);
            GL20.glDeleteProgram(prog);
            return -1;
        }
        GL20.glAttachShader(prog, v);
        GL20.glAttachShader(prog, f);
        GL20.glLinkProgram(prog);
        int link = GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS);
        if (link == GL11.GL_FALSE) {
            String info = GL20.glGetProgramInfoLog(prog);
            System.out.println("Shader link error: " + info);
            GL20.glDeleteShader(v);
            GL20.glDeleteShader(f);
            GL20.glDeleteProgram(prog);
            return -1;
        }
        // shaders can be detached after linking
        GL20.glDetachShader(prog, v);
        GL20.glDetachShader(prog, f);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return prog;
    }

    // --- Segmentation FBO management ---
    public static synchronized void ensureSegmentationFramebuffer(int width, int height) {
        if (segmentationFbo == null || segmentationFbo.textureWidth != width || segmentationFbo.textureHeight != height) {
            if (segmentationFbo != null) {
                segmentationFbo.delete();
            }
            segmentationFbo = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        }
    }

    public static void beginSegmentationPass() {
        if (segmentationFbo != null) {
            segmentationFbo.beginWrite(true);
            segmentationFbo.setClearColor(0,0,0,0);
            segmentationFbo.clear(MinecraftClient.IS_SYSTEM_MAC);
        }
    }

    public static void endSegmentationPass() {
        if (segmentationFbo != null) {
            segmentationFbo.endWrite();
            // Rebind main framebuffer for subsequent normal rendering; GameRenderer will proceed.
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        }
    }

    public static Framebuffer getSegmentationFramebuffer() {
        return segmentationFbo;
    }
}
