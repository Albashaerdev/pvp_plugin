package dev.pvpsmp;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

/** Applies lifesteal hearts and the custom Strength level to a player. */
public final class Stats {
    private final PvPSMPPlugin plugin;
    private final NamespacedKey strengthKey;

    public Stats(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.strengthKey = new NamespacedKey(plugin, "custom_strength");
    }

    public void apply(Player p) {
        DataStore.PData d = plugin.data().get(p.getUniqueId());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(d.hearts * 2.0);
            if (p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
        }
        double per = plugin.getConfig().getDouble("strength.damage-per-level", 1.5);
        Attr.set(p, Attribute.ATTACK_DAMAGE, strengthKey, d.strength * per, AttributeModifier.Operation.ADD_NUMBER);
    }

    /** @return false if already at max. */
    public boolean addStrength(Player p, int delta) {
        DataStore.PData d = plugin.data().get(p.getUniqueId());
        int max = plugin.getConfig().getInt("strength.max-level", 5);
        int min = plugin.getConfig().getInt("strength.min-level", 1);
        int n = Math.max(min, Math.min(max, d.strength + delta));
        if (n == d.strength) return false;
        d.strength = n;
        apply(p);
        return true;
    }

    public void addHearts(Player p, int delta) {
        DataStore.PData d = plugin.data().get(p.getUniqueId());
        int min = plugin.getConfig().getInt("lifesteal.min-hearts", 1);
        int max = plugin.getConfig().getInt("lifesteal.max-hearts", 20);
        d.hearts = Math.max(min, Math.min(max, d.hearts + delta));
        apply(p);
        if (delta > 0) {
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) p.setHealth(Math.min(hp.getValue(), p.getHealth() + delta * 2.0));
        }
    }
}
