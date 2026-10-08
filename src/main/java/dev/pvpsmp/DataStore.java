package dev.pvpsmp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Flat-file player database (players.yml): ability, strength level, kills, hearts. */
public final class DataStore {
    public static final class PData {
        public String ability;
        public int strength;
        public int kills;
        public int hearts;
    }

    private final PvPSMPPlugin plugin;
    private final File file;
    private final Map<UUID, PData> map = new ConcurrentHashMap<>();

    public DataStore(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                PData d = new PData();
                String a = y.getString(k + ".ability", "none");
                d.ability = "none".equals(a) ? null : a;
                d.strength = y.getInt(k + ".strength", 2);
                d.kills = y.getInt(k + ".kills", 0);
                d.hearts = y.getInt(k + ".hearts", 10);
                map.put(id, d);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public PData get(UUID id) {
        return map.computeIfAbsent(id, u -> {
            PData d = new PData();
            d.strength = plugin.getConfig().getInt("strength.start-level", 2);
            d.hearts = plugin.getConfig().getInt("lifesteal.start-hearts", 10);
            return d;
        });
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PData> e : map.entrySet()) {
            String k = e.getKey().toString();
            y.set(k + ".ability", e.getValue().ability == null ? "none" : e.getValue().ability);
            y.set(k + ".strength", e.getValue().strength);
            y.set(k + ".kills", e.getValue().kills);
            y.set(k + ".hearts", e.getValue().hearts);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save players.yml: " + ex.getMessage());
        }
    }

    public List<Map.Entry<UUID, PData>> top(int n) {
        List<Map.Entry<UUID, PData>> l = new ArrayList<>(map.entrySet());
        l.removeIf(e -> e.getValue().kills <= 0);
        l.sort((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills));
        return l.subList(0, Math.min(n, l.size()));
    }
}
