package dev.pvpsmp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/** Ability ownership, cooldowns and the action-bar HUD. */
public final class AbilityManager {
    private final PvPSMPPlugin plugin;
    private final Map<String, Long> cd = new HashMap<>();
    private final Random rnd = new Random();

    public AbilityManager(PvPSMPPlugin plugin) { this.plugin = plugin; }

    public AbilityType get(Player p) {
        return AbilityType.parse(plugin.data().get(p.getUniqueId()).ability);
    }

    public void set(Player p, AbilityType t) {
        plugin.data().get(p.getUniqueId()).ability = t == null ? null : t.name();
        plugin.abilityListener().reset(p);
    }

    public AbilityType roll(Player p) {
        List<AbilityType> pool = new ArrayList<>(List.of(AbilityType.values()));
        AbilityType cur = get(p);
        if (cur != null) pool.remove(cur);
        return pool.get(rnd.nextInt(pool.size()));
    }

    // ---- cooldowns ----
    private String k(Player p, String name) { return p.getUniqueId() + ":" + name; }
    public boolean ready(Player p, String name) { return remaining(p, name) <= 0; }
    public long remaining(Player p, String name) {
        Long end = cd.get(k(p, name));
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }
    public void start(Player p, String name, long millis) { cd.put(k(p, name), System.currentTimeMillis() + millis); }

    // ---- HUD ----
    public void startHud() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) hud(p);
        }, 10L, 5L);
    }

    private void cdPart(List<String> parts, Player p, String key, String label) {
        long r = remaining(p, key);
        parts.add(r > 0 ? "§e" + label + " §f" + (r / 1000 + 1) + "s" : "§a" + label + " ready");
    }

    private void hud(Player p) {
        List<String> parts = new ArrayList<>();
        AbilityType a = get(p);
        if (a != null) {
            parts.add("§6" + a.display);
            switch (a) {
                case AXE -> cdPart(parts, p, "axe_freeze", "Freeze");
                case SWORD -> {
                    cdPart(parts, p, "sword_reward", "Surge");
                    int s = plugin.abilityListener().streak(p);
                    if (s > 0) parts.add("§7Streak " + s);
                }
                case MACE -> cdPart(parts, p, "mace", "Smash");
                case SPEAR -> {
                    Double b = plugin.abilityListener().spearBonus(p);
                    parts.add(b == null ? "§cRush OFF" : "§aRush +" + (int) Math.round(b * 100) + "%");
                }
            }
        }
        if (!ready(p, "book")) cdPart(parts, p, "book", "Reroll");
        if (plugin.combat().isTagged(p)) parts.add("§cCombat " + (plugin.combat().remaining(p) / 1000 + 1) + "s");
        if (parts.isEmpty()) return;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(String.join(" §8| ", parts)));
    }
}
