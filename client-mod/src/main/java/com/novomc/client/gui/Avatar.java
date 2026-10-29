package com.novomc.client.gui;

import java.util.UUID;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.novomc.client.modules.NovoCapesModule;
import com.novomc.client.render.NovoCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The little player figure the title screen and the avatar editor both show:
 * the vanilla player model, skin and all, in a front view with a cape, wings
 * and hat, drawn back to front — wings, cape, body, hat — with depth testing
 * off, so the layers stack the way they do in game without depth-buffer tricks.
 *
 * Nothing is cached between calls: the caller passes the ids it wants worn, so
 * a preview only ever shows one item.
 */
final class Avatar {
    /** How far the figure reaches either way from the shoulder line, in model units. */
    static final int ABOVE_NECK = 15;
    static final int BELOW_NECK = 24;
    static final int FIGURE_H = ABOVE_NECK + BELOW_NECK;
    /** Half the span of a pair of wings at scale 1, from `WingModel.SCALE` (12 per OBJ unit). */
    static final float WING_HALF = 1.47F * 12.0F;

    private static final ModelPlayer MODEL = new ModelPlayer(0.0F, false);
    private static volatile ResourceLocation skin;
    private static boolean askedSkin;

    private Avatar() {}

    /**
     * Draws the figure with `neckY` at the shoulder line and `unit` pixels per
     * model unit (a player is 32 of those tall). `yaw` turns it about its spine
     * and `pitch` tips it toward or away from the viewer, both in degrees; a
     * null id leaves that slot bare.
     */
    static void draw(float centreX, float neckY, float unit, float yaw, float pitch,
            String cape, String wing, String hat) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translate(centreX, neckY, 0.0F);
        GlStateManager.scale(unit, unit, unit);
        // The figure turns about the shoulder line, so the costume stays centred
        // however far it is spun.
        GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);

        if (wing != null) {
            NovoCosmetics.renderWings(wing, 1.0F);
            reset();
        }
        if (cape != null) {
            cape(cape);
            reset();
        }

        mc.getTextureManager().bindTexture(skin());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        MODEL.bipedHead.render(1.0F);
        MODEL.bipedHeadwear.render(1.0F);
        MODEL.bipedBody.render(1.0F);
        MODEL.bipedRightArm.render(1.0F);
        MODEL.bipedLeftArm.render(1.0F);
        MODEL.bipedRightLeg.render(1.0F);
        MODEL.bipedLeftLeg.render(1.0F);

        if (hat != null) {
            NovoCosmetics.renderHat(hat, 1.0F);
            reset();
        }

        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The cape texture's outer face (10x16 at 1,1 of the 64x32 sheet), hung
     * behind the figure. It is drawn wider than a worn cape — flared from 6 to
     * 8.5 units — because from the front a worn cape hides completely behind
     * the body: flared, the player can see which one they are wearing.
     */
    static void cape(String id) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(NovoCapesModule.capeTexture(id));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        float u0 = 1.0F / 64.0F;
        float u1 = 11.0F / 64.0F;
        float v0 = 1.0F / 32.0F;
        float v1 = 17.0F / 32.0F;
        float z = 2.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(-6.0F, 0.0F, z);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(6.0F, 0.0F, z);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(8.5F, 16.0F, z + 0.4F);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(-8.5F, 16.0F, z + 0.4F);
        GL11.glEnd();
    }

    /** Puts back the 2D state the cosmetic renderers leave changed. */
    private static void reset() {
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The player's skin, else the grey default while it loads (or offline). */
    private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (skin == null) {
            if (!askedSkin) {
                askedSkin = true;
                try {
                    mc.getSkinManager().loadProfileTextures(mc.getSession().getProfile(), new SkinManager.SkinAvailableCallback() {
                        @Override
                        public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture texture) {
                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                skin = location;
                            }
                        }
                    }, false);
                } catch (Exception ignored) {
                    // Offline: the default skin below is fine.
                }
            }
            if (skin == null) {
                UUID id = mc.getSession().getProfile().getId();
                skin = id == null ? DefaultPlayerSkin.getDefaultSkinLegacy() : DefaultPlayerSkin.getDefaultSkin(id);
            }
        }
        return skin;
    }
}
