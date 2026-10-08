package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/** Factory + identification + recipes for every custom item. */
public final class Items {
    public static final String ABILITY_BOOK = "ability_book";
    public static final String OP_POTION = "op_potion";
    public static final String SHIELD_BREAKER = "shield_breaker";
    public static final String STRENGTH_VIAL = "strength_vial";
    public static final String NUGGET = "celestified_nugget";
    public static final String INGOT = "celestified_ingot";
    public static final String TEMPLATE = "celestial_upgrade_template";
    public static final String CELESTIAL_ARMOR = "celestial_armor";

    public static final List<String> GIVE_NAMES = List.of("ability_book", "strength_vial", "op_potion",
            "shield_breaker", "shield_breaker_2", "nugget", "ingot", "template");

    private final PvPSMPPlugin plugin;
    public final NamespacedKey ID, LEVEL, SECONDS;

    public Items(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        ID = new NamespacedKey(plugin, "item_id");
        LEVEL = new NamespacedKey(plugin, "sb_level");
        SECONDS = new NamespacedKey(plugin, "sb_seconds");
    }

    // ---------- identification ----------
    public String id(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }
    public boolean is(ItemStack s, String id) { return id.equals(id(s)); }

    private int intOf(ItemStack s, NamespacedKey k) {
        if (s == null || !s.hasItemMeta()) return 0;
        Integer v = s.getItemMeta().getPersistentDataContainer().get(k, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }
    public int level(ItemStack s) { return intOf(s, LEVEL); }
    public int seconds(ItemStack s) { return intOf(s, SECONDS); }

    // ---------- factories ----------
    private ItemStack make(Material mat, String id, String name, NamedTextColor color, boolean glint, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Txt.c(name, color));
        if (lore.length > 0) m.lore(Arrays.stream(lore).map(s -> Txt.c(s, NamedTextColor.GRAY)).toList());
        if (glint) m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack potion(Material mat, String id, String name, NamedTextColor c, int rgb, String... lore) {
        ItemStack it = make(mat, id, name, c, false, lore);
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        pm.setColor(Color.fromRGB(rgb));
        pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(pm);
        return it;
    }

    /** Points the item at a custom model from the PvPSMP resource pack (assets/pvpsmp/items/<name>.json). */
    private void model(ItemStack it, String name) {
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("pvpsmp", name));
        it.setItemMeta(m);
    }

    public ItemStack abilityBook(int amount) {
        ItemStack it = make(Material.BOOK, ABILITY_BOOK, "Ability Book", NamedTextColor.GOLD, true,
                "Right-click to roll a random weapon ability.", "20 minute cooldown between uses.");
        ItemMeta m = it.getItemMeta();
        m.setMaxStackSize(5);
        it.setItemMeta(m);
        it.setAmount(amount);
        return it;
    }

    public ItemStack opPotion() {
        return potion(Material.POTION, OP_POTION, "OP Potion", NamedTextColor.LIGHT_PURPLE, 0x5b2a86,
                "Does nothing... yet.");
    }

    public ItemStack shieldBreaker(int level, int seconds, boolean splash) {
        String n = (splash ? "Splash " : "") + "Shield Breaker Potion" + (level >= 2 ? " II" : " I");
        ItemStack it = potion(splash ? Material.SPLASH_POTION : Material.POTION, SHIELD_BREAKER, n,
                NamedTextColor.DARK_PURPLE, 0x9d4dff,
                "All your hits can disable shields.",
                "Counts as " + level + " axe hit" + (level > 1 ? "s" : "") + " against shields.",
                "Duration: " + (seconds / 60) + ":" + String.format("%02d", seconds % 60));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(LEVEL, PersistentDataType.INTEGER, level);
        m.getPersistentDataContainer().set(SECONDS, PersistentDataType.INTEGER, seconds);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack strengthVial() {
        return potion(Material.POTION, STRENGTH_VIAL, "Strength Vial", NamedTextColor.RED, 0xcc2222,
                "Drink to gain +1 Strength level.");
    }

    public ItemStack nugget(int amount) {
        ItemStack it = make(Material.IRON_NUGGET, NUGGET, "Celestified Nugget", NamedTextColor.AQUA, false,
                "9 of these make a Celestified Ingot.");
        model(it, "celestified_nugget");
        it.setAmount(amount);
        return it;
    }

    public ItemStack ingot() {
        ItemStack it = make(Material.COPPER_INGOT, INGOT, "Celestified Ingot", NamedTextColor.AQUA, false,
                "Used with a Celestial Upgrade Template", "to upgrade netherite armor.");
        model(it, "celestified_ingot");
        return it;
    }

    public ItemStack template() {
        return make(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, TEMPLATE, "Celestial Upgrade Template",
                NamedTextColor.AQUA, true, "Smithing: netherite armor + Celestified Ingot");
    }

    public ItemStack byName(String name, int amount) {
        ItemStack it = switch (name.toLowerCase()) {
            case "ability_book" -> abilityBook(1);
            case "strength_vial" -> strengthVial();
            case "op_potion" -> opPotion();
            case "shield_breaker" -> shieldBreaker(1, plugin.getConfig().getInt("potions.shield-breaker-seconds", 180), false);
            case "shield_breaker_2" -> shieldBreaker(2, plugin.getConfig().getInt("potions.shield-breaker-seconds", 180), false);
            case "nugget" -> nugget(1);
            case "ingot" -> ingot();
            case "template" -> template();
            default -> null;
        };
        if (it != null) it.setAmount(Math.max(1, Math.min(amount, it.getMaxStackSize())));
        return it;
    }

    // ---------- recipes ----------
    public void registerRecipes() {
        // Ability book: diamond blocks (corners), gold blocks (sides), nautilus shell (center)
        NamespacedKey bk = new NamespacedKey(plugin, "ability_book");
        Bukkit.removeRecipe(bk);
        ShapedRecipe book = new ShapedRecipe(bk, abilityBook(1));
        book.shape("DGD", "GNG", "DGD");
        book.setIngredient('D', Material.DIAMOND_BLOCK);
        book.setIngredient('G', Material.GOLD_BLOCK);
        book.setIngredient('N', Material.NAUTILUS_SHELL);
        Bukkit.addRecipe(book);

        // 9 nuggets -> ingot
        NamespacedKey ik = new NamespacedKey(plugin, "celestified_ingot");
        Bukkit.removeRecipe(ik);
        ShapelessRecipe ingot = new ShapelessRecipe(ik, ingot());
        for (int i = 0; i < 9; i++) ingot.addIngredient(new RecipeChoice.ExactChoice(nugget(1)));
        Bukkit.addRecipe(ingot);

        // Template duplication: like netherite template but ancient debris in the bottom middle
        NamespacedKey tk = new NamespacedKey(plugin, "celestial_template_dupe");
        Bukkit.removeRecipe(tk);
        ItemStack two = template();
        two.setAmount(2);
        ShapedRecipe dupe = new ShapedRecipe(tk, two);
        dupe.shape("DDD", "DTD", "DAD");
        dupe.setIngredient('D', Material.DIAMOND);
        dupe.setIngredient('T', new RecipeChoice.ExactChoice(template()));
        dupe.setIngredient('A', Material.ANCIENT_DEBRIS);
        Bukkit.addRecipe(dupe);
    }
}
