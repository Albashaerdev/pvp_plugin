package dev.pvpsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Lifesteal, Strength transfer, leaderboard, death-drop rules and kill-farming protection. */
public final class DeathListener implements Listener {
    private final PvPSMPPlugin plugin;
    private final Map<String, Deque<Long>> pairKills = new HashMap<>();   // "killer>victim" -> timestamps
    private final Map<String, Integer> together = new HashMap<>();        // "a|b" -> seconds spent near each other

    public DeathListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::proximityTick, 100L, 100L);
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { plugin.stats().apply(e.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.stats().apply(p);
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) p.setHealth(hp.getValue());
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null && plugin.combat().isTagged(victim)) killer = plugin.combat().opponent(victim);
        if (victim.equals(killer)) killer = null;
        boolean pvp = killer != null || plugin.combat().isTagged(victim);

        if (!pvp) {
            // normal death: keep everything
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        } else {
            // death in PvP: drop everything (also covers keepInventory gamerule being on)
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            if (e.getDrops().isEmpty()) {
                for (ItemStack it : victim.getInventory().getContents()) if (it != null && !it.getType().isAir()) e.getDrops().add(it);
                victim.getInventory().clear();
            }
            if (e.getDroppedExp() == 0) e.setDroppedExp(Math.min(victim.getLevel() * 7, 100));
        }

        if (killer != null) handleKill(killer, victim, e);
        plugin.combat().clear(victim);
    }

    private void handleKill(Player killer, Player victim, PlayerDeathEvent e) {
        // ---- kill farming ----
        if (cfg().getBoolean("kill-farming.enabled", true)) {
            String key = killer.getUniqueId() + ">" + victim.getUniqueId();
            long window = cfg().getInt("kill-farming.window-minutes", 180) * 60_000L;
            long now = System.currentTimeMillis();
            Deque<Long> q = pairKills.computeIfAbsent(key, k -> new ArrayDeque<>());
            q.removeIf(t -> now - t > window);
            q.add(now);
            if (q.size() >= cfg().getInt("kill-farming.kills-threshold", 7)
                    && togetherEnough(killer.getUniqueId(), victim.getUniqueId())) {
                pairKills.remove(key);
                banForFarming(killer);
                return;   // no rewards for the farmed kill
            }
        }

        // ---- leaderboard ----
        plugin.data().get(killer.getUniqueId()).kills++;

        // ---- lifesteal ----
        plugin.stats().addHearts(killer, +1);
        plugin.stats().addHearts(victim, -1);

        // ---- strength drop ----
        DataStore.PData vd = plugin.data().get(victim.getUniqueId());
        int min = cfg().getInt("strength.min-level", 1);
        if (vd.strength > min) {
            plugin.stats().addStrength(victim, -1);
            e.getDrops().add(plugin.items().strengthVial());
            killer.sendMessage(Txt.c(victim.getName() + " dropped a Strength Vial!", NamedTextColor.RED));
        }
    }

    private void banForFarming(Player p) {
        int hours = cfg().getInt("kill-farming.ban-hours", 3);
        Date until = new Date(System.currentTimeMillis() + hours * 3_600_000L);
        ProfileBanList list = Bukkit.getBanList(BanList.Type.PROFILE);
        list.addBan(p.getPlayerProfile(), "Kill farming", until, "PvPSMP");
        p.kick(Component.text("Banned for Kill farming (" + hours + " hours)"));
        plugin.getLogger().info(p.getName() + " was banned for kill farming for " + hours + "h.");
    }

    // ---- "together a lot" detection ----
    private String pair(UUID a, UUID b) { return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a; }

    private boolean togetherEnough(UUID a, UUID b) {
        if (!cfg().getBoolean("kill-farming.require-proximity", true)) return true;
        return together.getOrDefault(pair(a, b), 0) >= cfg().getInt("kill-farming.proximity-seconds", 300);
    }

    private void proximityTick() {
        double d = cfg().getDouble("kill-farming.proximity-distance", 30);
        double d2 = d * d;
        List<Player> ps = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < ps.size(); i++) {
            for (int j = i + 1; j < ps.size(); j++) {
                Player a = ps.get(i), b = ps.get(j);
                if (a.getWorld() == b.getWorld() && a.getLocation().distanceSquared(b.getLocation()) <= d2) {
                    together.merge(pair(a.getUniqueId(), b.getUniqueId()), 5, Integer::sum);
                }
            }
        }
    }
}
