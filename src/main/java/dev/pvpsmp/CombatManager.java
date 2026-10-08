package dev.pvpsmp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatManager {
    private final PvPSMPPlugin plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, UUID> opponent = new HashMap<>();

    public CombatManager(PvPSMPPlugin plugin) { this.plugin = plugin; }

    public void tag(Player a, Player b) {
        if (a.equals(b)) return;
        if (a.getGameMode() == GameMode.SPECTATOR || b.getGameMode() == GameMode.SPECTATOR) return;
        long end = System.currentTimeMillis() + plugin.getConfig().getInt("combat.duration-seconds", 10) * 1000L;
        for (Player p : new Player[]{a, b}) {
            boolean was = isTagged(p);
            until.put(p.getUniqueId(), end);
            opponent.put(p.getUniqueId(), p == a ? b.getUniqueId() : a.getUniqueId());
            if (!was) p.sendMessage(Txt.c("You are in combat! Don't log out.", net.kyori.adventure.text.format.NamedTextColor.RED));
            if (p.isGliding()) p.setGliding(false);
        }
    }

    public boolean isTagged(Player p) {
        Long e = until.get(p.getUniqueId());
        return e != null && e > System.currentTimeMillis();
    }

    public long remaining(Player p) {
        Long e = until.get(p.getUniqueId());
        return e == null ? 0 : Math.max(0, e - System.currentTimeMillis());
    }

    public Player opponent(Player p) {
        UUID o = opponent.get(p.getUniqueId());
        return o == null ? null : Bukkit.getPlayer(o);
    }

    public void clear(Player p) {
        until.remove(p.getUniqueId());
        opponent.remove(p.getUniqueId());
    }
}
