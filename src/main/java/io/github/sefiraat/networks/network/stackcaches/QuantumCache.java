package io.github.sefiraat.networks.network.stackcaches;

import io.github.sefiraat.networks.utils.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class QuantumCache extends ItemStackCache {

    @Nullable
    private final ItemMeta storedItemMeta;
    private final boolean supportsCustomMaxAmount;
    private int limit;
    private int amount;
    private boolean voidExcess;

    public QuantumCache(@Nullable ItemStack storedItem, int amount, int limit, boolean voidExcess, boolean supportsCustomMaxAmount) {
        super(storedItem);
        this.storedItemMeta = storedItem == null ? null : storedItem.getItemMeta();
        this.amount = amount;
        this.limit = limit;
        this.voidExcess = voidExcess;
        this.supportsCustomMaxAmount = supportsCustomMaxAmount;
    }

    @Nullable
    public ItemMeta getStoredItemMeta() {
        return this.storedItemMeta;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public boolean supportsCustomMaxAmount() {
        return this.supportsCustomMaxAmount;
    }

    public int increaseAmount(int amount) {
        long total = (long) this.amount + (long) amount;
        if (total > this.limit) {
            this.amount = this.limit;
            if (!this.voidExcess) {
                return (int) (total - this.limit);
            }
        } else {
            this.amount = this.amount + amount;
        }
        return 0;
    }

    public void reduceAmount(int amount) {
        this.amount = this.amount - amount;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isVoidExcess() {
        return voidExcess;
    }

    public void setVoidExcess(boolean voidExcess) {
        this.voidExcess = voidExcess;
    }

    @Nullable
    public ItemStack withdrawItem(int amount) {
        if (this.getItemStack() == null) {
            return null;
        }
        final ItemStack clone = this.getItemStack().clone();
        clone.setAmount(Math.min(this.amount, amount));
        reduceAmount(clone.getAmount());
        return clone;
    }

    @Nullable
    public ItemStack withdrawItem() {
        if (this.getItemStack() == null) {
            return null;
        }
        return withdrawItem(this.getItemStack().getMaxStackSize());
    }

    /**
     * Add metadata lore lines to an ItemMeta.
     * Uses Adventure Component API when available; falls back to reflective legacy handling to avoid deprecation warnings.
     */
    public void addMetaLore(ItemMeta itemMeta) {
        // Try to get existing lore as legacy strings, preferring Component API if present
        List<String> legacyLore = null;

        try {
            // If component-based lore exists, convert it to legacy strings
            List<Component> comps = itemMeta.lore();
            if (comps != null) {
                legacyLore = new ArrayList<>(comps.size());
                for (Component c : comps) {
                    legacyLore.add(LegacyComponentSerializer.legacySection().serialize(c));
                }
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // no component API available; handle via reflection below
        }

        if (legacyLore == null) {
            List<String> fromLegacy = invokeGetLore(itemMeta);
            legacyLore = fromLegacy != null ? new ArrayList<>(fromLegacy) : new ArrayList<>();
        }

        legacyLore.add("");
        // determine display name (try component displayName first)
        String displayName = null;
        try {
            Component comp = itemMeta.displayName();
            if (comp != null) displayName = LegacyComponentSerializer.legacySection().serialize(comp);
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }
        if (displayName == null) {
            String legacyName = invokeGetDisplayName(itemMeta);
            displayName = legacyName != null ? legacyName : (this.getItemStack() != null ? this.getItemStack().getType().name() : "Unknown");
        }

        legacyLore.add(Theme.CLICK_INFO + "Holding: " + displayName);
        legacyLore.add(Theme.CLICK_INFO + "Amount: " + this.getAmount());
        if (this.supportsCustomMaxAmount) {
            legacyLore.add(Theme.CLICK_INFO + "Current capacity limit: " + Theme.ERROR + this.getLimit());
        }

        invokeSetLore(itemMeta, legacyLore);
    }

    /**
     * Update existing lore lines that were added by addMetaLore.
     * Uses Component API when available; otherwise performs reflective legacy updates.
     */
    public void updateMetaLore(ItemMeta itemMeta) {
        List<String> legacyLore = null;

        try {
            List<Component> comps = itemMeta.lore();
            if (comps != null) {
                legacyLore = new ArrayList<>(comps.size());
                for (Component c : comps) legacyLore.add(LegacyComponentSerializer.legacySection().serialize(c));
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }

        if (legacyLore == null) {
            List<String> fromLegacy = invokeGetLore(itemMeta);
            legacyLore = fromLegacy != null ? new ArrayList<>(fromLegacy) : new ArrayList<>();
        }

        final int loreIndexModifier = this.supportsCustomMaxAmount ? 1 : 0;
        // ensure there's enough lines to update from the end
        while (legacyLore.size() < 3 + loreIndexModifier) {
            legacyLore.add("");
        }

        int holdingIndex = legacyLore.size() - 2 - loreIndexModifier;
        int amountIndex = legacyLore.size() - 1 - loreIndexModifier;

        if (holdingIndex < 0) holdingIndex = 0;
        if (amountIndex < 0) amountIndex = 1;

        String displayName = null;
        try {
            Component comp = itemMeta.displayName();
            if (comp != null) displayName = LegacyComponentSerializer.legacySection().serialize(comp);
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }
        if (displayName == null) {
            String legacyName = invokeGetDisplayName(itemMeta);
            displayName = legacyName != null ? legacyName : (this.getItemStack() != null ? this.getItemStack().getType().name() : "Unknown");
        }

        legacyLore.set(holdingIndex, Theme.CLICK_INFO + "Holding: " + displayName);
        legacyLore.set(amountIndex, Theme.CLICK_INFO + "Amount: " + this.getAmount());

        if (this.supportsCustomMaxAmount) {
            int capIndex = legacyLore.size() - loreIndexModifier;
            if (capIndex < 0) capIndex = legacyLore.size() - 1;
            legacyLore.set(capIndex, Theme.CLICK_INFO + "Current capacity limit: " + Theme.ERROR + this.getLimit());
        }

        invokeSetLore(itemMeta, legacyLore);
    }

    /* ---------------- reflection helpers to avoid compile-time deprecation warnings ---------------- */

    @Nullable
    private static List<String> invokeGetLore(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("getLore");
            Object res = m.invoke(meta);
            if (res instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) res;
                return list;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        } catch (SecurityException ignored) {
        }
        return null;
    }

    @Nullable
    private static String invokeGetDisplayName(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("getDisplayName");
            Object res = m.invoke(meta);
            if (res instanceof String) {
                return (String) res;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private static void invokeSetLore(ItemMeta meta, List<String> lore) {
        try {
            Method m = meta.getClass().getMethod("setLore", List.class);
            m.invoke(meta, lore);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // nothing we can do
        } catch (SecurityException ignored) {
        }
    }
}
