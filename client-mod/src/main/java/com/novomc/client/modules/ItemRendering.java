package com.novomc.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Dropped-item rendering for 2D Items (flat, facing you) and Item Physics
 * (lying on the ground, tumbling while falling). Called from MixinRenderEntityItem.
 */
public final class ItemRendering {
    static volatile boolean flat;
    static volatile boolean physics;

    private ItemRendering() {}

    /** Draws the item and returns true, or returns false to let vanilla draw it. */
    public static boolean render(EntityItem entity, double x, double y, double z, float partialTicks) {
        if (!flat && !physics) {
            return false;
        }
        ItemStack stack = entity.getEntityItem();
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        RenderItem items = mc.getRenderItem();
        IBakedModel model = items.getItemModelMesher().getItemModel(stack);
        boolean block = model.isGui3d();

        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();

        int copies = 1;
        if (physics) {
            copies = stack.stackSize > 32 ? 3 : stack.stackSize > 1 ? 2 : 1;
            GlStateManager.translate((float) x, (float) y + (block ? 0.125F : 0.02F), (float) z);
            GlStateManager.rotate((entity.getEntityId() * 37) % 360, 0, 1, 0);
            if (!entity.onGround) {
                GlStateManager.rotate(((entity.getAge() + partialTicks) * 15F) % 360F, 1, 0, 0);
            }
            if (!block) {
                GlStateManager.rotate(90F, 1, 0, 0);
            }
        } else {
            float bob = MathHelper.sin((entity.getAge() + partialTicks) / 10.0F + entity.hoverStart) * 0.1F + 0.1F;
            GlStateManager.translate((float) x, (float) y + bob + 0.125F, (float) z);
            GlStateManager.rotate(180.0F - mc.getRenderManager().playerViewY, 0, 1, 0);
        }

        for (int i = 0; i < copies; i++) {
            GlStateManager.pushMatrix();
            if (i > 0) {
                GlStateManager.translate(i * 0.08F, i * 0.05F, block ? i * 0.06F : -i * 0.04F);
            }
            if (block) {
                GlStateManager.scale(0.5F, 0.5F, 0.5F);
            }
            items.renderItem(stack, ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GROUND));
            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        return true;
    }
}
