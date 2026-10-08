package dev.pvpsmp;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PvPSMPPlugin extends JavaPlugin {
    private DataStore data;
    private Items items;
    private Stats stats;
    private AbilityManager abilities;
    private CombatManager combat;
    private PotionManager potions;
    private AbilityListener abilityListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        items = new Items(this);
        stats = new Stats(this);
        combat = new CombatManager(this);
        potions = new PotionManager(this);
        abilities = new AbilityManager(this);
        abilityListener = new AbilityListener(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(abilityListener, this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(potions, this);
        pm.registerEvents(new BrewingListener(this), this);
        pm.registerEvents(new LootListener(this), this);
        pm.registerEvents(new ArmorListener(this), this);
        pm.registerEvents(new WorldGenListener(this), this);

        items.registerRecipes();

        PvPSMPCommand cmd = new PvPSMPCommand(this);
        for (String n : List.of("pvpsmp", "leaderboard", "ability")) {
            PluginCommand c = getCommand(n);
            if (c != null) { c.setExecutor(cmd); c.setTabCompleter(cmd); }
        }

        abilities.startHud();
        getServer().getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);
        for (Player p : Bukkit.getOnlinePlayers()) stats.apply(p);
    }

    @Override
    public void onDisable() {
        if (abilityListener != null) abilityListener.shutdown();
        if (data != null) data.save();
    }

    public DataStore data() { return data; }
    public Items items() { return items; }
    public Stats stats() { return stats; }
    public AbilityManager abilities() { return abilities; }
    public CombatManager combat() { return combat; }
    public PotionManager potions() { return potions; }
    public AbilityListener abilityListener() { return abilityListener; }
}
