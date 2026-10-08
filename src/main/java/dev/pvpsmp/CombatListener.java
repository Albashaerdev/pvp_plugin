package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

public final class CombatListener implements Listener {
    private final PvPSMPPlugin plugin;
    private final CombatManager combat;

    public CombatListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.combat = plugin.combat();
    }

    private Player shooterOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = shooterOf(e.getDamager());
        if (attacker != null) combat.tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent e) {
        if (!(e.getHitEntity() instanceof Player victim)) return;
        if (e.getEntity().getShooter() instanceof Player shooter) combat.tag(shooter, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player victim) combat.tag(shooter, victim);
        }
    }

    /** Can't leave the game during combat: logging out = death (items drop, killer credited). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (combat.isTagged(p) && !p.isDead()) {
            plugin.getServer().broadcast(Txt.c(p.getName() + " logged out during combat!", NamedTextColor.RED));
            p.setHealth(0.0);
        }
        combat.clear(p);
    }

    /** Only /msg and /w while in combat. */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!combat.isTagged(p)) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.startsWith("minecraft:")) label = label.substring("minecraft:".length());
        List<String> allowed = plugin.getConfig().getStringList("combat.allowed-commands");
        if (!allowed.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(Txt.c("You can only use /msg and /w during combat.", NamedTextColor.RED));
        }
    }

    /** Elytra doesn't work in combat. */
    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && e.isGliding() && combat.isTagged(p)) {
            e.setCancelled(true);
            p.sendMessage(Txt.c("Elytra is disabled during combat.", NamedTextColor.RED));
        }
    }

    /** Blocked items (stasis / pearls) during combat. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick() || e.getItem() == null) return;
        Player p = e.getPlayer();
        if (!combat.isTagged(p)) return;
        Material m = e.getItem().getType();
        for (String s : plugin.getConfig().getStringList("combat.blocked-items")) {
            Material b = Material.matchMaterial(s);
            if (b == m) {
                e.setCancelled(true);
                p.sendMessage(Txt.c("You can't use that during combat.", NamedTextColor.RED));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && plugin.getConfig().getBoolean("combat.block-pearl-teleport", true)
                && combat.isTagged(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Txt.c("Pearls/stasis are disabled during combat.", NamedTextColor.RED));
        }
    }
}
