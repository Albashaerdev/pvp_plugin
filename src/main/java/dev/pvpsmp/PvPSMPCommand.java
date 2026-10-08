package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class PvPSMPCommand implements CommandExecutor, TabCompleter {
    private final PvPSMPPlugin plugin;

    public PvPSMPCommand(PvPSMPPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "leaderboard" -> board(s);
            case "ability" -> ability(s);
            default -> admin(s, args);
        }
        return true;
    }

    private void board(CommandSender s) {
        s.sendMessage(Txt.c("--- Top Kills ---", NamedTextColor.GOLD));
        int i = 1;
        for (Map.Entry<UUID, DataStore.PData> e : plugin.data().top(10)) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            s.sendMessage(Txt.c("#" + i++ + " " + (name == null ? "Unknown" : name) + " - " + e.getValue().kills,
                    NamedTextColor.YELLOW));
        }
        if (i == 1) s.sendMessage(Txt.c("No kills yet.", NamedTextColor.GRAY));
    }

    private void ability(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return; }
        AbilityType a = plugin.abilities().get(p);
        DataStore.PData d = plugin.data().get(p.getUniqueId());
        p.sendMessage(Txt.c("Ability: " + (a == null ? "none (craft an Ability Book)" : a.display), NamedTextColor.GOLD));
        p.sendMessage(Txt.c("Hearts: " + d.hearts + "  Strength: " + d.strength + "  Kills: " + d.kills, NamedTextColor.YELLOW));
        long r = plugin.abilities().remaining(p, "book");
        if (r > 0) p.sendMessage(Txt.c("Book cooldown: " + (r / 60000) + "m " + (r / 1000 % 60) + "s", NamedTextColor.GRAY));
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("pvpsmp.admin")) { s.sendMessage(Txt.c("No permission.", NamedTextColor.RED)); return; }
        if (a.length == 0) { s.sendMessage(Txt.c("/pvpsmp <give|ability|reload>", NamedTextColor.GRAY)); return; }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage(Txt.c("Config reloaded (brewing recipes need a restart).", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (a.length < 3) { s.sendMessage(Txt.c("/pvpsmp give <player> <item> [amount]", NamedTextColor.RED)); return; }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) { s.sendMessage(Txt.c("Player not found.", NamedTextColor.RED)); return; }
                int amt = a.length > 3 ? parse(a[3]) : 1;
                ItemStack it = plugin.items().byName(a[2], amt);
                if (it == null) { s.sendMessage(Txt.c("Unknown item. " + Items.GIVE_NAMES, NamedTextColor.RED)); return; }
                t.getInventory().addItem(it).values().forEach(x -> t.getWorld().dropItemNaturally(t.getLocation(), x));
                s.sendMessage(Txt.c("Gave " + it.getAmount() + "x " + a[2] + " to " + t.getName(), NamedTextColor.GREEN));
            }
            case "ability" -> {
                if (a.length < 3) { s.sendMessage(Txt.c("/pvpsmp ability <player> <axe|sword|mace|spear|none>", NamedTextColor.RED)); return; }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) { s.sendMessage(Txt.c("Player not found.", NamedTextColor.RED)); return; }
                plugin.abilities().set(t, AbilityType.parse(a[2]));
                s.sendMessage(Txt.c("Set ability of " + t.getName() + " to " + a[2], NamedTextColor.GREEN));
            }
            default -> s.sendMessage(Txt.c("/pvpsmp <give|ability|reload>", NamedTextColor.GRAY));
        }
    }

    private int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; } }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!cmd.getName().equalsIgnoreCase("pvpsmp")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("give", "ability", "reload"));
        else if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) out.addAll(Items.GIVE_NAMES);
        else if (a.length == 3 && a[0].equalsIgnoreCase("ability")) {
            for (AbilityType t : AbilityType.values()) out.add(t.name().toLowerCase());
            out.add("none");
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
