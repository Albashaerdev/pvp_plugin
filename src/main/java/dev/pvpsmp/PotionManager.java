package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shield Breaker buffs, OP potion, Strength Vial. */
public final class PotionManager implements Listener {
    private record Buff(int level, long expiry) {}

    private final PvPSMPPlugin plugin;
    private final Map<UUID, Buff> buffs = new HashMap<>();

    public PotionManager(PvPSMPPlugin plugin) { this.plugin = plugin; }

    public int level(Player p) {
        Buff b = buffs.get(p.getUniqueId());
        if (b == null) return 0;
        if (b.expiry() < System.currentTimeMillis()) { buffs.remove(p.getUniqueId()); return 0; }
        return b.level();
    }

    private void give(Player p, int level, double seconds) {
        buffs.put(p.getUniqueId(), new Buff(level, System.currentTimeMillis() + (long) (seconds * 1000)));
        p.sendMessage(Txt.c("Shield Breaker " + (level >= 2 ? "II" : "I") + " active for "
                + (int) seconds + "s - your hits disable shields.", NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Items items = plugin.items();
        ItemStack it = e.getItem();
        String id = items.id(it);
        if (id == null) return;
        Player p = e.getPlayer();
        switch (id) {
            case Items.SHIELD_BREAKER -> give(p, items.level(it), items.seconds(it));
            case Items.STRENGTH_VIAL -> {
                if (plugin.stats().addStrength(p, 1)) {
                    p.sendMessage(Txt.c("Your Strength increased to level "
                            + plugin.data().get(p.getUniqueId()).strength + "!", NamedTextColor.RED));
                } else {
                    e.setCancelled(true);
                    p.sendMessage(Txt.c("You're already at max Strength.", NamedTextColor.RED));
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onSplash(PotionSplashEvent e) {
        Items items = plugin.items();
        ItemStack it = e.getEntity().getItem();
        if (!items.is(it, Items.SHIELD_BREAKER)) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player p) give(p, items.level(it), items.seconds(it) * e.getIntensity(le));
        }
    }
}
