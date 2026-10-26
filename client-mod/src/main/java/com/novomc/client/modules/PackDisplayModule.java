package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;

/** Lists the active resource packs, top priority first. */
public class PackDisplayModule extends TextHud {
    public PackDisplayModule() {
        super("pack_display");
    }

    @Override
    protected String[] lines(boolean preview) {
        List<String> names = new ArrayList<String>();
        for (ResourcePackRepository.Entry e : mc.getResourcePackRepository().getRepositoryEntries()) {
            names.add(e.getResourcePackName().replaceFirst("\\.zip$", ""));
        }
        if (names.isEmpty()) {
            names.add("Default pack");
        }
        return names.toArray(new String[0]);
    }
}
