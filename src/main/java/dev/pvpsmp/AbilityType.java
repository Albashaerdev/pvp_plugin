package dev.pvpsmp;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

public enum AbilityType {
    AXE("Axe"), SWORD("Sword"), MACE("Mace"), SPEAR("Spear");

    public final String display;
    AbilityType(String display) { this.display = display; }

    public boolean matches(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return switch (this) {
            case AXE -> Tag.ITEMS_AXES.isTagged(m);
            case SWORD -> Tag.ITEMS_SWORDS.isTagged(m);
            case MACE -> m == Material.MACE;
            case SPEAR -> m.name().endsWith("_SPEAR"); // version-safe (spears: 1.21.11+)
        };
    }

    public static AbilityType parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException ex) { return null; }
    }
}
