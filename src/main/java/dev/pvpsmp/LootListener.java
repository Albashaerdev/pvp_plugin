package dev.pvpsmp;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Celestified nuggets / upgrade templates in structure chests + more trapping materials. */
public final class LootListener implements Listener {
    private final PvPSMPPlugin plugin;

    public LootListener(PvPSMPPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onLoot(LootGenerateEvent e) {
        FileConfiguration c = plugin.getConfig();
        Items items = plugin.items();
        String key = e.getLootTable().getKey().getKey();    // e.g. "chests/ancient_city"
        List<ItemStack> loot = e.getLoot();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (String t : c.getStringList("loot.nugget-tables")) {
            if (key.contains(t)) {
                if (r.nextDouble() < c.getDouble("loot.nugget-chance", 0.028)) loot.add(items.nugget(1));
                break;
            }
        }
        if (key.contains("ancient_city") && r.nextDouble() < c.getDouble("loot.template-chance", 0.008)) {
            loot.add(items.template());
        }

        // Trapping materials are more common everywhere
        if (r.nextDouble() < c.getDouble("loot.trapping-chance", 0.25)) {
            List<String> names = c.getStringList("loot.trapping-items");
            if (!names.isEmpty()) {
                Material m = Material.matchMaterial(names.get(r.nextInt(names.size())));
                if (m != null && !m.isAir()) {
                    int amt = m.getMaxStackSize() == 1 ? 1 : 2 + r.nextInt(5);
                    loot.add(new ItemStack(m, Math.min(amt, m.getMaxStackSize())));
                }
            }
        }
    }
}
