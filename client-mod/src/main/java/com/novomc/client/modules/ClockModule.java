package com.novomc.client.modules;

import com.novomc.client.module.TextHud;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends TextHud {
    public ClockModule() {
        super("clock");
    }

    @Override
    protected String[] lines(boolean preview) {
        String pattern = (bool("format24h") ? "HH:mm" : "h:mm") + (bool("showSeconds") ? ":ss" : "") + (bool("format24h") ? "" : " a");
        return new String[] {LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))};
    }
}
