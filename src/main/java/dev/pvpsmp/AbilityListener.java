package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/** Axe / Sword / Mace / Spear abilities, shield rule, and the ability book. */
public final class AbilityListener implements Listener {
    private record Frozen(LivingEntity entity, BukkitTask task) {}
    private record Hits(int count, long expiry) {}

    private final PvPSMPPlugin plugin;
    private final AbilityManager am;
    private final Random rnd = new Random();

    private final Set<UUID> reentry = new HashSet<>();
    private final Map<UUID, Integer> streak = new HashMap<>();
    private final Map<UUID, Long> lastCrit = new HashMap<>();
    private final Map<UUID, Double> spear = new HashMap<>();
    private final Map<UUID, Location> spearLast = new HashMap<>();
    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private final Map<UUID, Hits> shieldHits = new HashMap<>();
    private final Set<UUID> wasBlocking = new HashSet<>();

    private final NamespacedKey swordKey, spearKey, freezeKey;

    public AbilityListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.am = plugin.abilities();
        swordKey = new NamespacedKey(plugin, "sword_attack_speed");
        spearKey = new NamespacedKey(plugin, "spear_rush");
        freezeKey = new NamespacedKey(plugin, "freeze");
        Bukkit.getScheduler().runTaskTimer(plugin, this::spearTick, 4L, 4L);
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    // =====================================================================
    //  Main melee handler (axe / sword / mace)
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (reentry.contains(p.getUniqueId())) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        AbilityType a = am.get(p);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (a == null) return;
        if (a != AbilityType.SWORD && !a.matches(hand)) return;   // sword() does its own weapon/streak handling
        switch (a) {
            case AXE -> axe(p, target, e);
            case SWORD -> sword(p, e);
            case MACE -> mace(p, target, e);
            default -> { }
        }
    }

    // ---- AXE ----
    private void axe(Player p, LivingEntity target, EntityDamageByEntityEvent e) {
        if (!e.isCritical()) return;   // a vanilla crit already needs a (near) full attack cooldown
        heal(p, cfg().getDouble("abilities.axe.heal-hearts", 1) * 2.0);
        if (!target.isOnGround() && am.ready(p, "axe_freeze")) {
            freeze(target, cfg().getInt("abilities.axe.freeze-seconds", 3) * 20);
            am.start(p, "axe_freeze", cfg().getInt("abilities.axe.freeze-cooldown-seconds", 20) * 1000L);
        }
    }

    private void heal(Player p, double amount) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double m = max == null ? 20 : max.getValue();
        p.setHealth(Math.min(m, p.getHealth() + amount));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 3, .3, .2, .3, 0);
    }

    private void freeze(LivingEntity t, int ticks) {
        unfreeze(t.getUniqueId());
        Attr.set(t, Attribute.MOVEMENT_SPEED, freezeKey, -1, AttributeModifier.Operation.ADD_SCALAR);
        Attr.set(t, Attribute.GRAVITY, freezeKey, -1, AttributeModifier.Operation.ADD_SCALAR);
        Attr.set(t, Attribute.JUMP_STRENGTH, freezeKey, -1, AttributeModifier.Operation.ADD_SCALAR);
        if (t instanceof Mob m) m.setAware(false);
        Location anchor = t.getLocation();
        BukkitTask task = new BukkitRunnable() {
            int left = ticks;
            @Override public void run() {
                if (!t.isValid() || t.isDead() || left-- <= 0) { unfreeze(t.getUniqueId()); return; }
                t.setVelocity(new Vector(0, 0, 0));
                Location now = t.getLocation();
                if (now.getWorld() == anchor.getWorld() && now.distanceSquared(anchor) > 0.25) {
                    Location back = anchor.clone();
                    back.setYaw(now.getYaw());
                    back.setPitch(now.getPitch());
                    t.teleport(back);
                }
                if (left % 5 == 0) t.getWorld().spawnParticle(Particle.SNOWFLAKE, now.add(0, 1, 0), 6, .3, .5, .3, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        frozen.put(t.getUniqueId(), new Frozen(t, task));
        t.getWorld().playSound(t.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        if (t instanceof Player pl) pl.sendMessage(Txt.c("You've been frozen in mid-air!", NamedTextColor.AQUA));
    }

    private void unfreeze(UUID id) {
        Frozen f = frozen.remove(id);
        if (f == null) return;
        f.task().cancel();
        LivingEntity t = f.entity();
        Attr.clear(t, Attribute.MOVEMENT_SPEED, freezeKey);
        Attr.clear(t, Attribute.GRAVITY, freezeKey);
        Attr.clear(t, Attribute.JUMP_STRENGTH, freezeKey);
        if (t instanceof Mob m) m.setAware(true);
    }

    // ---- SWORD ----
    private void sword(Player p, EntityDamageByEntityEvent e) {
        UUID id = p.getUniqueId();
        if (!AbilityType.SWORD.matches(p.getInventory().getItemInMainHand())) return;
        long timeout = cfg().getInt("abilities.sword.streak-timeout-seconds", 6) * 1000L;
        if (System.currentTimeMillis() - lastCrit.getOrDefault(id, 0L) > timeout) streak.put(id, 0);

        if (!e.isCritical()) {          // streak broken by a non-crit hit
            streak.put(id, 0);
            clearSwordBoost(p);
            return;
        }
        int s = streak.merge(id, 1, Integer::sum);
        lastCrit.put(id, System.currentTimeMillis());
        swordBoost(p, s);

        if (s >= cfg().getInt("abilities.sword.crit-streak-reward", 10) && am.ready(p, "sword_reward")) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max == null ? 20 : max.getValue());
            p.setFoodLevel(20);
            p.setSaturation(20f);
            streak.put(id, 0);
            clearSwordBoost(p);
            am.start(p, "sword_reward", cfg().getInt("abilities.sword.reward-cooldown-seconds", 60) * 1000L);
            p.sendMessage(Txt.c("Crit streak! Fully restored.", NamedTextColor.GOLD));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    private void swordBoost(Player p, int s) {
        AttributeInstance as = p.getAttribute(Attribute.ATTACK_SPEED);
        if (as == null) return;
        Attr.remove(as, swordKey);
        double targetSpeed = 1.0 / cfg().getDouble("abilities.sword.min-cooldown-seconds", 0.5);
        double cap = Math.max(0, targetSpeed - as.getValue());
        double amt = Math.min(s * cfg().getDouble("abilities.sword.speed-step", 0.05), cap);
        if (amt > 0) as.addTransientModifier(new AttributeModifier(swordKey, amt,
                AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ANY));
    }

    private void clearSwordBoost(Player p) { Attr.clear(p, Attribute.ATTACK_SPEED, swordKey); }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!AbilityType.SWORD.matches(p.getInventory().getItemInMainHand())) {
                clearSwordBoost(p);
                streak.put(p.getUniqueId(), 0);
            }
        });
    }

    // ---- MACE ----
    private void mace(Player p, LivingEntity target, EntityDamageByEntityEvent e) {
        if (!am.ready(p, "mace")) {
            e.setCancelled(true);
            return;
        }
        boolean smash = p.getFallDistance() > cfg().getDouble("abilities.mace.smash-fall-distance", 1.5);
        if (!smash) return;

        e.setDamage(e.getDamage() * cfg().getDouble("abilities.mace.damage-multiplier", 2.0));
        int cdSec = cfg().getInt("abilities.mace.cooldown-seconds", 10);
        am.start(p, "mace", cdSec * 1000L);
        p.setCooldown(Material.MACE, cdSec * 20);

        double radius = cfg().getDouble("abilities.mace.radius", 5.0);
        double aoe = cfg().getDouble("abilities.mace.aoe-damage", 8.0);
        double kb = cfg().getDouble("abilities.mace.knockback", 1.2);
        Location center = target.getLocation();

        reentry.add(p.getUniqueId());
        try {
            for (LivingEntity n : center.getWorld().getNearbyLivingEntities(center, radius)) {
                if (n.equals(p) || n instanceof ArmorStand) continue;
                if (n instanceof Tameable t && p.equals(t.getOwner())) continue;
                if (n instanceof Player op && (op.getGameMode() == GameMode.CREATIVE || op.getGameMode() == GameMode.SPECTATOR)) continue;
                if (!n.equals(target)) n.damage(aoe, p);
                Vector v = n.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (v.lengthSquared() < 0.0001) v = new Vector(rnd.nextDouble() - .5, 0, rnd.nextDouble() - .5);
                v.normalize().multiply(kb).setY(0.45);
                n.setVelocity(v);
            }
        } finally {
            reentry.remove(p.getUniqueId());
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
    }

    // =====================================================================
    //  Shield: needs TWO axe hits (shield-breaker potions count as hits)
    // =====================================================================
    @EventHandler(priority = EventPriority.LOWEST)
    public void shieldPre(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player v && e.getDamager() instanceof Player && v.isBlocking()) {
            wasBlocking.add(v.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void shieldPost(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        boolean blocking = wasBlocking.remove(v.getUniqueId());
        if (!blocking || e.isCancelled() || !(e.getDamager() instanceof Player a)) return;
        if (e.getFinalDamage() > 0.0) return;   // not actually blocked

        boolean axe = Tag.ITEMS_AXES.isTagged(a.getInventory().getItemInMainHand().getType());
        int weight = Math.max(axe ? 1 : 0, plugin.potions().level(a));
        if (weight <= 0) return;

        int need = cfg().getInt("shield.hits-required", 2);
        long now = System.currentTimeMillis();
        Hits h = shieldHits.get(v.getUniqueId());
        int total = (h != null && h.expiry() > now ? h.count() : 0) + weight;

        if (total >= need) {
            shieldHits.remove(v.getUniqueId());
            if (!axe) disableShield(v);      // vanilla already disabled it for axes
        } else {
            shieldHits.put(v.getUniqueId(), new Hits(total, now + cfg().getInt("shield.hit-window-seconds", 4) * 1000L));
            if (axe) Bukkit.getScheduler().runTask(plugin, () -> v.setCooldown(Material.SHIELD, 0)); // undo vanilla disable
        }
    }

    private void disableShield(Player v) {
        v.setCooldown(Material.SHIELD, 100);
        v.clearActiveItem();
        v.getWorld().playSound(v.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
    }

    // =====================================================================
    //  Spear rush (toggle with right click) + Ability Book
    // =====================================================================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick() || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        if (hand == null) return;

        if (plugin.items().is(hand, Items.ABILITY_BOOK)) {
            e.setCancelled(true);
            useBook(p, hand);
            return;
        }
        if (am.get(p) == AbilityType.SPEAR && AbilityType.SPEAR.matches(hand)) toggleSpear(p);
    }

    private void useBook(Player p, ItemStack book) {
        if (!am.ready(p, "book")) {
            p.sendMessage(Txt.c("Ability book is on cooldown: " + fmt(am.remaining(p, "book")), NamedTextColor.RED));
            return;
        }
        AbilityType n = am.roll(p);
        am.set(p, n);
        book.setAmount(book.getAmount() - 1);
        am.start(p, "book", cfg().getInt("abilities.book-cooldown-minutes", 20) * 60_000L);
        p.sendMessage(Txt.c("You gained the " + n.display + " ability!", NamedTextColor.GOLD));
        p.showTitle(net.kyori.adventure.title.Title.title(Txt.c(n.display, NamedTextColor.GOLD), Txt.c("ability unlocked")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private String fmt(long ms) {
        long s = ms / 1000;
        return (s / 60) + "m " + (s % 60) + "s";
    }

    private void toggleSpear(Player p) {
        if (!am.ready(p, "spear_toggle")) return;
        am.start(p, "spear_toggle", cfg().getInt("abilities.spear.toggle-cooldown-seconds", 1) * 1000L);
        UUID id = p.getUniqueId();
        if (spear.containsKey(id)) {
            stopSpear(p);
            p.sendMessage(Txt.c("Spear rush OFF", NamedTextColor.RED));
        } else {
            double base = cfg().getDouble("abilities.spear.base-speed-bonus", 0.10);
            spear.put(id, base);
            Attr.set(p, Attribute.MOVEMENT_SPEED, spearKey, base, AttributeModifier.Operation.ADD_SCALAR);
            p.sendMessage(Txt.c("Spear rush ON", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 1.5f);
        }
    }

    private void stopSpear(Player p) {
        spear.remove(p.getUniqueId());
        spearLast.remove(p.getUniqueId());
        Attr.clear(p, Attribute.MOVEMENT_SPEED, spearKey);
    }

    private void spearTick() {
        double base = cfg().getDouble("abilities.spear.base-speed-bonus", 0.10);
        double max = cfg().getDouble("abilities.spear.max-speed-bonus", 0.40);
        double ramp = cfg().getDouble("abilities.spear.ramp-per-tick-step", 0.02);
        for (Iterator<Map.Entry<UUID, Double>> it = spear.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Double> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead()
                    || am.get(p) != AbilityType.SPEAR
                    || !AbilityType.SPEAR.matches(p.getInventory().getItemInMainHand())) {
                if (p != null) Attr.clear(p, Attribute.MOVEMENT_SPEED, spearKey);
                spearLast.remove(en.getKey());
                it.remove();
                continue;
            }
            Location now = p.getLocation();
            Location last = spearLast.get(en.getKey());
            boolean moving = last != null && last.getWorld() == now.getWorld()
                    && (Math.pow(now.getX() - last.getX(), 2) + Math.pow(now.getZ() - last.getZ(), 2)) > 0.01;
            double v = moving ? Math.min(max, en.getValue() + ramp) : Math.max(base, en.getValue() - ramp * 2);
            en.setValue(v);
            Attr.set(p, Attribute.MOVEMENT_SPEED, spearKey, v, AttributeModifier.Operation.ADD_SCALAR);
            spearLast.put(en.getKey(), now);
        }
    }

    // =====================================================================
    //  State housekeeping
    // =====================================================================
    public int streak(Player p) { return streak.getOrDefault(p.getUniqueId(), 0); }
    public Double spearBonus(Player p) { return spear.get(p.getUniqueId()); }

    /** Called when a player's ability changes. */
    public void reset(Player p) {
        stopSpear(p);
        clearSwordBoost(p);
        streak.put(p.getUniqueId(), 0);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(frozen.keySet())) unfreeze(id);
        for (UUID id : new ArrayList<>(spear.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stopSpear(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        unfreeze(e.getPlayer().getUniqueId());
        reset(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        unfreeze(e.getEntity().getUniqueId());
        reset(e.getEntity());
    }
}
