package io.github.sefiraat.networks.network.stackcaches;

import io.github.sefiraat.networks.utils.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CardInstance extends ItemStackCache {

    private final int limit;
    private int amount;

    public CardInstance(@Nullable ItemStack itemStack, int amount, int limit) {
        super(itemStack);
        this.amount = amount;
        this.limit = limit;
    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getLimit() {
        return this.limit;
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

    public void increaseAmount(int amount) {
        long total = (long) this.amount + (long) amount;
        if (total > this.limit) {
            this.amount = this.limit;
        } else {
            this.amount = this.amount + amount;
        }
    }

    public void reduceAmount(int amount) {
        this.amount = this.amount - amount;
    }

    /**
     * Update lore line index 10 with the new lore text.
     * Uses Adventure API when available; falls back to legacy getLore/setLore via reflection when necessary.
     */
    public void updateLore(@Nonnull ItemMeta itemMeta) {
        // Build component from legacy-formatted string (supports color codes like § or & via legacy serializer)
        Component newLineComp = LegacyComponentSerializer.legacySection().deserialize(getLoreLine());

        try {
            // Try new Component-based API first
            List<Component> comps = itemMeta.lore();
            if (comps == null) {
                // fallback: try legacy String lore and convert to components via reflection
                List<String> legacy = invokeGetLore(itemMeta);
                if (legacy != null) {
                    comps = new ArrayList<>(legacy.size());
                    for (String s : legacy) {
                        comps.add(LegacyComponentSerializer.legacySection().deserialize(s));
                    }
                } else {
                    comps = new ArrayList<>();
                }
            } else {
                comps = new ArrayList<>(comps); // make mutable copy
            }

            // ensure index 10 exists
            while (comps.size() <= 10) comps.add(Component.empty());
            comps.set(10, newLineComp);

            // set back as components
            itemMeta.lore(comps);
            return;
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // runtime doesn't support Component-based lore API -> fallback to reflective legacy handling below
        }

        // Legacy fallback using reflection to avoid compile-time deprecation warnings
        List<String> legacy = invokeGetLore(itemMeta);
        if (legacy == null) {
            legacy = new ArrayList<>();
        }
        while (legacy.size() <= 10) legacy.add("");
        legacy.set(10, LegacyComponentSerializer.legacySection().serialize(newLineComp));
        invokeSetLore(itemMeta, legacy);
    }

    public String getLoreLine() {
        if (this.getItemStack() == null) {
            return Theme.WARNING + "Empty";
        }
        ItemMeta itemMeta = this.getItemMeta();
        String name;

        if (itemMeta != null && hasDisplayNameSafe(itemMeta)) {
            // Prefer Component-based display name and convert to plain text (no color codes)
            try {
                Component comp = itemMeta.displayName(); // new API
                if (comp != null) {
                    name = PlainTextComponentSerializer.plainText().serialize(comp);
                } else {
                    // fallback to legacy string display name via reflection
                    String legacy = invokeGetDisplayName(itemMeta);
                    if (legacy != null && !legacy.isEmpty()) {
                        name = PlainTextComponentSerializer.plainText().serialize(
                                LegacyComponentSerializer.legacySection().deserialize(legacy)
                        );
                    } else if (this.getItemType() != null) {
                        name = this.getItemType().name();
                    } else {
                        name = "Unknown/Error";
                    }
                }
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // runtime missing new API: fallback to reflective legacy getDisplayName()
                String legacy = invokeGetDisplayName(itemMeta);
                if (legacy != null && !legacy.isEmpty()) {
                    name = PlainTextComponentSerializer.plainText().serialize(
                            LegacyComponentSerializer.legacySection().deserialize(legacy)
                    );
                } else if (this.getItemType() != null) {
                    name = this.getItemType().name();
                } else {
                    name = "Unknown/Error";
                }
            }
        } else if (this.getItemType() != null) {
            name = this.getItemType().name();
        } else {
            name = "Unknown/Error";
        }

        return Theme.CLICK_INFO + name + ": " + Theme.PASSIVE + this.amount;
    }

    // helper to check availability of displayName without throwing NoSuchMethodError at call site
    private boolean hasDisplayNameSafe(ItemMeta meta) {
        try {
            return meta.hasDisplayName();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // older runtimes may not have new API; try reflective legacy getDisplayName
            String legacy = invokeGetDisplayName(meta);
            return legacy != null && !legacy.isEmpty();
        }
    }

    /* ---------------- reflection helpers to avoid compile-time deprecation warnings ---------------- */

    @Nullable
    private static List<String> invokeGetLore(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("getLore");
            Object res = m.invoke(meta);
            if (res instanceof List) {
                // unchecked cast but acceptable here
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
