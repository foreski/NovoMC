package com.novomc.client.modules;

import com.novomc.client.gui.GuiHudEditor;
import com.novomc.client.mixin.accessor.EntityRendererAccessor;
import com.novomc.client.mixin.accessor.ShaderGroupAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Minecraft runs one post-processing shader at a time, so Menu Blur and
 * Motion Blur share this: menu blur while a menu is open, else motion blur.
 * It only ever stops shaders it started itself.
 *
 * Each shader is compiled once and kept, so opening and closing menus swaps
 * them instantly instead of recompiling (which caused a hitch every time).
 * Motion blur's trail is set every frame from the frame time, so it looks the
 * same at 60 FPS and at 300.
 */
public final class ShaderController {
    private static final ResourceLocation BLUR = new ResourceLocation("minecraft", "shaders/post/blur.json");
    private final Minecraft mc = Minecraft.getMinecraft();
    /** Loaded shader groups by name, ready to swap in. */
    private final Map<String, ShaderGroup> cache = new HashMap<String, ShaderGroup>();
    /** The group we put in place, if it's still the active one. */
    private ShaderGroup ours;
    private int width;
    private int height;
    private long lastFrame;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            update();
        }
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 1 / 60f : Math.min(0.25f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        ShaderGroup group = mc.entityRenderer != null ? mc.entityRenderer.getShaderGroup() : null;
        if (group == null || group != ours || !group.getShaderGroupName().contains("motion_blur")) {
            return;
        }
        // The json value is the trail left after one 60 FPS frame; scale it to this frame's length.
        float persistence = (float) Math.pow(MotionBlurModule.persistence(), dt * 60f);
        for (Shader pass : ((ShaderGroupAccessor) group).novomc$getShaders()) {
            ShaderUniform u = pass.getShaderManager().getShaderUniform("Phosphor");
            if (u != null) {
                u.set(persistence, persistence, persistence);
            }
        }
    }

    private ResourceLocation wanted() {
        if (mc.theWorld == null) {
            return null;
        }
        boolean menu = mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat) && !(mc.currentScreen instanceof GuiHudEditor);
        if (menu && MenuBlurModule.on()) {
            return BLUR;
        }
        return MotionBlurModule.shader();
    }

    private void update() {
        if (!OpenGlHelper.shadersSupported || mc.entityRenderer == null) {
            return;
        }
        ResourceLocation want = wanted();
        ShaderGroup current = mc.entityRenderer.getShaderGroup();
        EntityRendererAccessor renderer = (EntityRendererAccessor) mc.entityRenderer;
        if (ours != null && current != ours) {
            // Vanilla replaced or deleted ours (resource reload, spectating a mob): never reuse it.
            cache.values().remove(ours);
            ours = null;
        }
        if (current != null && ours == null) {
            // Someone else's shader: leave it alone.
            return;
        }
        if (want == null) {
            if (current != null) {
                renderer.novomc$setShaderGroup(null);
                renderer.novomc$setUseShader(false);
            }
            ours = null;
            return;
        }
        if (current != null && want.toString().equals(current.getShaderGroupName())) {
            return;
        }
        ShaderGroup group = cache.get(want.toString());
        if (group == null) {
            renderer.novomc$loadShader(want);
            group = mc.entityRenderer.getShaderGroup();
            if (group == null) {
                return;
            }
            cache.put(want.toString(), group);
            width = mc.displayWidth;
            height = mc.displayHeight;
        } else {
            renderer.novomc$setShaderGroup(group);
            renderer.novomc$setUseShader(true);
        }
        // Vanilla only resizes the active group; catch up after a window resize.
        if (width != mc.displayWidth || height != mc.displayHeight || group != ours) {
            group.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
            width = mc.displayWidth;
            height = mc.displayHeight;
        }
        ours = group;
    }
}
