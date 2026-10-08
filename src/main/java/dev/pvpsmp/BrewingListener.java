package dev.pvpsmp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Custom brewing:
 *   ominous bottle(s) + diamond axe (+ blaze powder)          -> OP Potion
 *   OP Potion(s)      + golden apple (+ BREEZE ROD as fuel)   -> Shield Breaker I (3:00)
 *   Shield Breaker    + redstone  -> 6:00   | glowstone -> II | gunpowder -> splash
 * Vanilla brewing stands refuse these items, so placement is handled manually, and the brew
 * itself runs on a plugin-side timer. One fuel item is consumed per brew.
 */
public final class BrewingListener implements Listener {
    private record Recipe(Predicate<ItemStack> bottle, Material ingredient, Material fuel, UnaryOperator<ItemStack> result) {}

    private final PvPSMPPlugin plugin;
    private final Items items;
    private final List<Recipe> recipes;
    private final Map<Location, BukkitTask> running = new HashMap<>();

    public BrewingListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.items = plugin.items();
        int base = plugin.getConfig().getInt("potions.shield-breaker-seconds", 180);
        int ext = plugin.getConfig().getInt("potions.shield-breaker-extended-seconds", 360);
        recipes = List.of(
                new Recipe(i -> i.getType() == Material.OMINOUS_BOTTLE, Material.DIAMOND_AXE, Material.BLAZE_POWDER,
                        i -> items.opPotion()),
                new Recipe(i -> items.is(i, Items.OP_POTION), Material.GOLDEN_APPLE, Material.BREEZE_ROD,
                        i -> items.shieldBreaker(1, base, false)),
                new Recipe(i -> items.is(i, Items.SHIELD_BREAKER) && i.getType() == Material.POTION && items.seconds(i) < ext,
                        Material.REDSTONE, Material.BLAZE_POWDER,
                        i -> items.shieldBreaker(items.level(i), ext, false)),
                new Recipe(i -> items.is(i, Items.SHIELD_BREAKER) && items.level(i) < 2,
                        Material.GLOWSTONE_DUST, Material.BLAZE_POWDER,
                        i -> items.shieldBreaker(2, items.seconds(i), i.getType() == Material.SPLASH_POTION)),
                new Recipe(i -> items.is(i, Items.SHIELD_BREAKER) && i.getType() == Material.POTION,
                        Material.GUNPOWDER, Material.BLAZE_POWDER,
                        i -> items.shieldBreaker(items.level(i), items.seconds(i), true))
        );
    }

    // ---------- manual placement of items vanilla would refuse ----------
    private boolean bypass(int slot, Material m) {
        if (slot <= 2) return m == Material.OMINOUS_BOTTLE;
        if (slot == 3) return m == Material.DIAMOND_AXE || m == Material.GOLDEN_APPLE;
        if (slot == 4) return m == Material.BREEZE_ROD;
        return false;
    }

    private int shiftTarget(BrewerInventory bi, ItemStack cur) {
        Material m = cur.getType();
        if (m == Material.OMINOUS_BOTTLE) {
            for (int i = 0; i < 3; i++) if (isEmpty(bi.getItem(i))) return i;
            return -1;
        }
        if (m == Material.DIAMOND_AXE || m == Material.GOLDEN_APPLE) return 3;
        if (m == Material.BREEZE_ROD) return 4;
        return -1;
    }

    private boolean isEmpty(ItemStack s) { return s == null || s.getType().isAir(); }

    /** @return how many items were moved from src into the slot */
    private int put(BrewerInventory bi, int slot, ItemStack src, int max) {
        ItemStack cur = bi.getItem(slot);
        int limit = slot <= 2 ? 1 : src.getMaxStackSize();
        if (isEmpty(cur)) {
            int n = Math.min(Math.min(src.getAmount(), max), limit);
            ItemStack c = src.clone();
            c.setAmount(n);
            bi.setItem(slot, c);
            return n;
        }
        if (slot >= 3 && cur.isSimilar(src)) {
            int n = Math.max(0, Math.min(Math.min(src.getAmount(), max), limit - cur.getAmount()));
            if (n > 0) { cur.setAmount(cur.getAmount() + n); bi.setItem(slot, cur); }
            return n;
        }
        return 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory() instanceof BrewerInventory bi)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = e.getCursor();

        if (e.getRawSlot() < 5 && !isEmpty(cursor) && bypass(e.getRawSlot(), cursor.getType())) {
            // placing a custom-allowed item from the cursor into a brewing slot
            e.setCancelled(true);
            int slot = e.getRawSlot();
            int max = e.isRightClick() ? 1 : cursor.getAmount();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack cur = p.getItemOnCursor();
                if (isEmpty(cur)) return;
                int moved = put(bi, slot, cur, max);
                if (moved > 0) {
                    ItemStack c = cur.clone();
                    c.setAmount(cur.getAmount() - moved);
                    p.setItemOnCursor(c.getAmount() > 0 ? c : null);
                }
                p.updateInventory();
                check(bi.getLocation());
            });
            return;
        }

        if (e.isShiftClick() && e.getRawSlot() >= 5 && e.getCurrentItem() != null) {
            ItemStack cur = e.getCurrentItem();
            int target = shiftTarget(bi, cur);
            if (target >= 0) {
                e.setCancelled(true);
                Inventory from = e.getClickedInventory();
                int fromSlot = e.getSlot();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (from == null) return;
                    ItemStack src = from.getItem(fromSlot);
                    if (isEmpty(src)) return;
                    int moved = put(bi, target, src, src.getAmount());
                    if (moved > 0) {
                        src.setAmount(src.getAmount() - moved);
                        from.setItem(fromSlot, src.getAmount() > 0 ? src : null);
                    }
                    p.updateInventory();
                    check(bi.getLocation());
                });
                return;
            }
        }
        scheduleCheck(bi);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory() instanceof BrewerInventory bi) scheduleCheck(bi);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (e.getDestination() instanceof BrewerInventory bi) scheduleCheck(bi);
    }

    /** Stop vanilla from "brewing" our custom potions (e.g. gunpowder container mix). */
    @EventHandler(ignoreCancelled = true)
    public void onVanillaBrew(BrewEvent e) {
        BrewerInventory inv = e.getContents();
        for (int i = 0; i < 3; i++) {
            if (items.id(inv.getItem(i)) != null) { e.setCancelled(true); return; }
        }
    }

    // ---------- custom brewing engine ----------
    private void scheduleCheck(BrewerInventory bi) {
        Location loc = bi.getLocation();
        if (loc == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> check(loc));
    }

    private Recipe match(BrewerInventory inv) {
        ItemStack ing = inv.getIngredient(), fuel = inv.getFuel();
        if (isEmpty(ing) || isEmpty(fuel)) return null;
        for (Recipe r : recipes) {
            if (ing.getType() != r.ingredient() || fuel.getType() != r.fuel()) continue;
            boolean any = false, ok = true;
            for (int i = 0; i < 3; i++) {
                ItemStack s = inv.getItem(i);
                if (isEmpty(s)) continue;
                if (!r.bottle().test(s)) { ok = false; break; }
                any = true;
            }
            if (ok && any) return r;
        }
        return null;
    }

    private void check(Location raw) {
        Location key = raw.toBlockLocation();
        Block b = key.getBlock();
        BukkitTask cur = running.get(key);
        if (!(b.getState() instanceof BrewingStand st)) {
            if (cur != null) { cur.cancel(); running.remove(key); }
            return;
        }
        Recipe r = match(st.getInventory());
        if (r == null) {
            if (cur != null) { cur.cancel(); running.remove(key); }
            return;
        }
        if (cur != null) return;

        int ticks = plugin.getConfig().getInt("potions.brew-ticks", 400);
        BukkitTask task = new BukkitRunnable() {
            int left = ticks;
            @Override public void run() {
                if (!(key.getBlock().getState() instanceof BrewingStand s2)) { stop(); return; }
                Recipe m = match(s2.getInventory());
                if (m != r) { stop(); plugin.getServer().getScheduler().runTask(plugin, () -> check(key)); return; }
                if (left % 10 == 0) key.getWorld().spawnParticle(Particle.EFFECT, key.clone().add(.5, 1.0, .5), 4, .2, .1, .2, 0);
                if (--left <= 0) {
                    finish(s2, r);
                    stop();
                    plugin.getServer().getScheduler().runTask(plugin, () -> check(key));
                }
            }
            private void stop() { running.remove(key); cancel(); }
        }.runTaskTimer(plugin, 0L, 1L);
        running.put(key, task);
    }

    private void finish(BrewingStand st, Recipe r) {
        BrewerInventory inv = st.getInventory();
        for (int i = 0; i < 3; i++) {
            ItemStack s = inv.getItem(i);
            if (!isEmpty(s)) inv.setItem(i, r.result().apply(s));
        }
        ItemStack ing = inv.getIngredient();
        ing.setAmount(ing.getAmount() - 1);
        inv.setIngredient(ing.getAmount() > 0 ? ing : null);
        ItemStack fuel = inv.getFuel();
        fuel.setAmount(fuel.getAmount() - 1);
        inv.setFuel(fuel.getAmount() > 0 ? fuel : null);
        Location l = st.getLocation();
        l.getWorld().playSound(l, Sound.BLOCK_BREWING_STAND_BREW, 1f, 1f);
    }
}
