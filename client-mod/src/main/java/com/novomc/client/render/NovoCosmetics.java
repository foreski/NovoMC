package com.novomc.client.render;

import java.util.UUID;

import com.novomc.client.gui.Cosmetics;
import com.novomc.client.modules.NovoCapesModule;
import com.novomc.client.net.NovoPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Draws the Novo wings and hats worn by a player, once per rendered player.
 *
 * Called from {@code RendererLivingEntity.renderLayers}, just after the player
 * model and before the layers (armor, cape). The model's own pose is reused
 * from the frame that was just rendered, so wings follow the torso and hats
 * follow the head through sneaking, riding and head turns without copying any
 * of the vanilla pose maths.
 *
 * Both are untextured flat-shaded geometry, so they need no atlas: wings are
 * the OBJ meshes in {@code assets/novomc/models/wings} baked by
 * {@link WingModel}, hats the ones in {@code models/hats} baked by
 * {@link HatMesh}.
 *
 * 1.8.9 only: the modern client has no model renderer to hook, so it shows
 * capes only even when a player wears wings or a hat.
 */
public final class NovoCosmetics {
    private static final long START = System.nanoTime();
    private static final float RAD_TO_DEG = 57.29578F;
    /** The head box is 8 model units tall; its pivot is the neck (y = 0). */
    private static final float HEAD_TOP = -8.0F;

    private NovoCosmetics() {}

    /**
     * @param player the player being drawn
     * @param model  the living renderer's model, posed for this frame
     * @param scale  the renderer's model scale (1/16)
     */
    public static void render(EntityPlayer player, ModelBase model, float scale) {
        if (!NovoCapesModule.isOn() || !(model instanceof ModelBiped) || player.isInvisible()) {
            return;
        }
        UUID id = player.getUniqueID();
        boolean self = player == Minecraft.getMinecraft().thePlayer;
        if (id == null || (!self && !NovoCapesModule.showsOthers())) {
            return;
        }
        ModelBiped biped = (ModelBiped) model;

        String wing = wingOf(player);
        if (wing != null) {
            GlStateManager.pushMatrix();
            transform(biped.bipedBody, scale);
            renderWings(wing, scale);
            GlStateManager.popMatrix();
        }

        String hat = hatOf(player);
        if (hat != null) {
            GlStateManager.pushMatrix();
            transform(biped.bipedHead, scale);
            renderHat(hat, scale);
            GlStateManager.popMatrix();
        }
    }

    /** The wings this player is drawn with, or null. */
    public static String wingOf(EntityPlayer player) {
        return worn(player, Cosmetics.WING);
    }

    /** The hat this player is drawn with, or null. */
    public static String hatOf(EntityPlayer player) {
        return worn(player, Cosmetics.HAT);
    }

    /**
     * The id a player wears in a slot, or null.
     *
     * Our own look comes from `cosmetics.json`, which the launcher writes and
     * the wardrobe edits: it stays on us when we don't share and when the relay
     * can't be reached. Presence only lists players who share, so it is the
     * source for everyone else.
     */
    private static String worn(EntityPlayer player, String kind) {
        UUID id = player.getUniqueID();
        if (player == Minecraft.getMinecraft().thePlayer) {
            Cosmetics local = Cosmetics.current();
            if (local != null) {
                return local.worn(kind);
            }
        }
        return Cosmetics.WING.equals(kind) ? NovoPresence.INSTANCE.wing(id) : NovoPresence.INSTANCE.hat(id);
    }

    /**
     * Draws a pair of wings at the origin: the caller supplies the matrix, so
     * this works both on a player (body transform applied) and on the model the
     * cosmetics screen previews.
     */
    public static void renderWings(String id, float scale) {
        WingModel wings = WingModel.get(id);
        if (wings != null) {
            wings.render(seconds(), scale);
        }
    }

    /** Draws a hat on the head pivot at the origin (y = 0), like a player's head. */
    public static void renderHat(String id, float scale) {
        HatMesh hat = HatMesh.get(id);
        if (hat == null) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, HEAD_TOP * scale, 0.0F);
        float fit = hat.fit() * scale;
        // The meshes are Y-up; the player model is Y-down.
        GlStateManager.scale(fit, -fit, fit);
        hat.render();
        GlStateManager.popMatrix();
    }

    /** Runs the same transform a {@link ModelRenderer} would for this part. */
    private static void transform(ModelRenderer part, float scale) {
        GlStateManager.translate(part.rotationPointX * scale, part.rotationPointY * scale, part.rotationPointZ * scale);
        if (part.rotateAngleZ != 0.0F) {
            GlStateManager.rotate(part.rotateAngleZ * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
        }
        if (part.rotateAngleY != 0.0F) {
            GlStateManager.rotate(part.rotateAngleY * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
        }
        if (part.rotateAngleX != 0.0F) {
            GlStateManager.rotate(part.rotateAngleX * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);
        }
    }

    /** Wall-clock seconds, so the wing flap is the same at any frame rate. */
    private static float seconds() {
        return (System.nanoTime() - START) / 1.0E9F;
    }

    /**
     * Flat untextured drawing for cosmetics. Culling stays off afterwards:
     * the player renderer turns it back on only once the layers are done.
     */
    static void beginFlat() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Puts back what the armor and cape layers expect to find. */
    static void endFlat() {
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
