package dev.pvpsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.Keyed;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Celestified armor smithing + guard against custom items leaking into vanilla recipes. */
public final class ArmorListener implements Listener {
    private final PvPSMPPlugin plugin;
    private final Items items;

    public ArmorListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.items = plugin.items();
    }

    private boolean isNetheriteArmor(Material m) {
        String n = m.name();
        return n.startsWith("NETHERITE_") && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"));
    }

    @EventHandler
    public void onSmith(PrepareSmithingEvent e) {
        SmithingInventory inv = e.getInventory();
        ItemStack t = inv.getInputTemplate(), base = inv.getInputEquipment(), add = inv.getInputMineral();
        if (!items.is(t, Items.TEMPLATE)) return;
        if (base == null || !items.is(add, Items.INGOT)) { e.setResult(null); return; }
        if (!isNetheriteArmor(base.getType()) || items.is(base, Items.CELESTIAL_ARMOR)
                || base.getItemMeta().getPersistentDataContainer().has(items.ID, PersistentDataType.STRING)) {
            e.setResult(null);
            return;
        }
        e.setResult(celestify(base));
    }

    private ItemStack celestify(ItemStack base) {
        FileConfiguration c = plugin.getConfig();
        ItemStack out = base.clone();      // keeps name, enchantments, trim, durability, etc.
        ItemMeta m = out.getItemMeta();
        String n = base.getType().name();

        EquipmentSlotGroup g;
        double armor;
        if (n.endsWith("_HELMET")) { g = EquipmentSlotGroup.HEAD; armor = 3; }
        else if (n.endsWith("_CHESTPLATE")) { g = EquipmentSlotGroup.CHEST; armor = 8; }
        else if (n.endsWith("_LEGGINGS")) { g = EquipmentSlotGroup.LEGS; armor = 6; }
        else { g = EquipmentSlotGroup.FEET; armor = 3; }

        // Adding custom modifiers replaces the item's default ones, so re-add the netherite baseline + bonuses.
        m.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(plugin, "celestial_armor"),
                armor + c.getDouble("armor.bonus-armor", 1.0), AttributeModifier.Operation.ADD_NUMBER, g));
        m.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(plugin, "celestial_toughness"),
                3.0 + c.getDouble("armor.bonus-toughness", 1.0), AttributeModifier.Operation.ADD_NUMBER, g));
        m.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(new NamespacedKey(plugin, "celestial_kb"),
                c.getDouble("armor.knockback-resistance", 1.0), AttributeModifier.Operation.ADD_NUMBER, g));

        // "Always gives protection": guarantee a Protection enchant (never lowers an existing one)
        int lvl = c.getInt("armor.protection-level", 5);
        if (m.getEnchantLevel(Enchantment.PROTECTION) < lvl) m.addEnchant(Enchantment.PROTECTION, lvl, true);

        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Txt.c("Celestified", NamedTextColor.AQUA));
        lore.add(Txt.c("100% Knockback Resistance", NamedTextColor.DARK_AQUA));
        m.lore(lore);
        m.getPersistentDataContainer().set(items.ID, PersistentDataType.STRING, Items.CELESTIAL_ARMOR);
        out.setItemMeta(m);
        return out;
    }

    /** Custom items use vanilla base materials; stop them being used as ingredients in vanilla recipes. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null) return;
        if (r instanceof Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return;
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (it != null && items.id(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
