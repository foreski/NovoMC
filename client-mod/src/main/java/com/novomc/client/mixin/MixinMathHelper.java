package com.novomc.client.mixin;

import com.novomc.client.render.FastMath;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** Fast Math: a small sine table when enabled. */
@Mixin(MathHelper.class)
public abstract class MixinMathHelper {
    @Shadow @Final private static float[] SIN_TABLE;

    /**
     * @author NovoMC
     * @reason Fast Math switches to a cache-friendly table.
     */
    @Overwrite
    public static float sin(float value) {
        return FastMath.enabled ? FastMath.sin(value) : SIN_TABLE[(int) (value * 10430.378F) & 65535];
    }

    /**
     * @author NovoMC
     * @reason Fast Math switches to a cache-friendly table.
     */
    @Overwrite
    public static float cos(float value) {
        return FastMath.enabled ? FastMath.cos(value) : SIN_TABLE[(int) (value * 10430.378F + 16384.0F) & 65535];
    }
}
