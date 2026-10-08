package dev.pvpsmp;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;

/** Small helpers for keyed attribute modifiers. */
public final class Attr {
    private Attr() {}

    public static void remove(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(key)) inst.removeModifier(m);
        }
    }

    public static void clear(LivingEntity e, Attribute a, NamespacedKey key) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) remove(i, key);
    }

    public static void set(LivingEntity e, Attribute a, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance i = e.getAttribute(a);
        if (i == null) return;
        remove(i, key);
        if (amount != 0) i.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }
}
