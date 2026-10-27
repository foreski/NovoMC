package com.novomc.client.mixin.accessor;

import java.io.File;
import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A resource pack's zip or folder, for listing its CTM properties (Connected Textures). */
@Mixin(AbstractResourcePack.class)
public interface AbstractResourcePackAccessor {
    @Accessor("resourcePackFile")
    File novomc$getFile();
}
