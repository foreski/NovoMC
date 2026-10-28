package com.novomc.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.novomc.modern.Visuals;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Outline: colour and width of the selected block's outline, plus an optional fill. High contrast stays vanilla. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int novomc$outlineColor(int color, PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state,
                                     int original, float width, boolean translucent) {
        Visuals.BlockOutline o = Visuals.BlockOutline.active();
        return o != null && !state.highContrast() ? o.lineColor() : color;
    }

    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float novomc$outlineWidth(float width, PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state,
                                       int color, float original, boolean translucent) {
        Visuals.BlockOutline o = Visuals.BlockOutline.active();
        return o != null && !state.highContrast() ? o.width() : width;
    }

    @Inject(method = "submitHitOutline", at = @At("HEAD"))
    private void novomc$outlineFill(PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state, int color,
                                    float width, boolean translucent, CallbackInfo ci) {
        Visuals.BlockOutline o = Visuals.BlockOutline.active();
        int fill = o != null && !state.highContrast() ? o.fillColor() : 0;
        if (fill == 0) {
            return;
        }
        collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, v) -> {
            for (AABB b : state.shape().toAabbs()) {
                box(p.pose(), v, b.inflate(0.002), fill);
            }
        });
    }

    private static void box(Matrix4f m, VertexConsumer v, AABB b, int c) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(m, v, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(m, v, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(m, v, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(m, v, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(m, v, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(m, v, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(Matrix4f m, VertexConsumer v, int c, float... p) {
        for (int i = 0; i < 12; i += 3) {
            v.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(c);
        }
    }
}
