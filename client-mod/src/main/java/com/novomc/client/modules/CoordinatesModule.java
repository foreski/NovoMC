package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class CoordinatesModule extends TextHud {
    private static final String[] FACING = {"South (+Z)", "West (-X)", "North (-Z)", "East (+X)"};

    public CoordinatesModule() {
        super("coordinates");
    }

    private String format(double v) {
        return String.format("%." + Math.max(0, Math.min(2, integer("decimals"))) + "f", v);
    }

    @Override
    protected String[] lines(boolean preview) {
        EntityPlayer p = mc.thePlayer;
        double x = p != null ? p.posX : 128.5;
        double y = p != null ? p.getEntityBoundingBox().minY : 64;
        double z = p != null ? p.posZ : -42.5;
        List<String> out = new ArrayList<String>();
        out.add("X: " + format(x));
        out.add("Y: " + format(y));
        out.add("Z: " + format(z));
        if (bool("showFacing")) {
            int f = p != null ? MathHelper.floor_double(p.rotationYaw * 4 / 360 + 0.5) & 3 : 2;
            out.add(FACING[f]);
        }
        if (bool("showBiome")) {
            String biome = p != null && mc.theWorld != null
                    ? mc.theWorld.getBiomeGenForCoords(new BlockPos(p)).biomeName
                    : "Plains";
            out.add(biome);
        }
        return out.toArray(new String[0]);
    }
}
