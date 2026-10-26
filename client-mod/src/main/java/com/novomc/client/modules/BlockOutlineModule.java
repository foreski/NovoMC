package com.novomc.client.modules;

import com.novomc.client.module.Module;
import com.novomc.client.util.Draw3D;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Replaces the black block selection box with a colored outline and fill. */
public class BlockOutlineModule extends Module {
    public BlockOutlineModule() {
        super("block_outline");
    }

    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent event) {
        MovingObjectPosition target = event.target;
        if (!isEnabled() || event.subID != 0 || target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        World world = mc.theWorld;
        BlockPos pos = target.getBlockPos();
        Block block = world.getBlockState(pos).getBlock();
        if (block.getMaterial() == Material.air || !world.getWorldBorder().contains(pos)) {
            return;
        }
        event.setCanceled(true);

        block.setBlockBoundsBasedOnState(world, pos);
        double[] cam = Draw3D.camera(event.player, event.partialTicks);
        AxisAlignedBB box = block.getSelectedBoundingBox(world, pos).expand(0.002, 0.002, 0.002).offset(-cam[0], -cam[1], -cam[2]);
        int rgb = color("color") & 0xFFFFFF;

        Draw3D.begin(number("width"));
        if (bool("fill")) {
            Draw3D.color(rgb, number("fillOpacity") / 100f);
            Draw3D.filledBox(box);
        }
        Draw3D.color(rgb, 1f);
        RenderGlobal.drawSelectionBoundingBox(box);
        Draw3D.end();
    }
}
