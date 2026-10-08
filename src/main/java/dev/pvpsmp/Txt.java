package dev.pvpsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class Txt {
    private Txt() {}
    public static Component c(String s, NamedTextColor col) {
        return Component.text(s, col).decoration(TextDecoration.ITALIC, false);
    }
    public static Component c(String s) { return c(s, NamedTextColor.GRAY); }
}
