package io.github.sefiraat.networks.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ItemCreator {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static ItemStack create(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        setDisplayNameBoth(meta, name);

        if (meta instanceof BookMeta) {
            setBookTitleSafe((BookMeta) meta, name);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack create(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        setDisplayNameBoth(meta, name);
        setLoreBoth(meta, lore);

        if (meta instanceof BookMeta) {
            setBookTitleSafe((BookMeta) meta, name);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack create(ItemStack item, String name, String... lore) {
        item = item.clone();
        ItemMeta meta = item.getItemMeta();

        setDisplayNameBoth(meta, name);
        setLoreBoth(meta, lore);

        if (meta instanceof BookMeta) {
            setBookTitleSafe((BookMeta) meta, name);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack create(ItemStack item, String name) {
        item = item.clone();
        ItemMeta meta = item.getItemMeta();

        setDisplayNameBoth(meta, name);

        if (meta instanceof BookMeta) {
            setBookTitleSafe((BookMeta) meta, name);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static void setBookTitleSafe(BookMeta bm, String legacyString) {
        try {
            String colored = Utils.color(Objects.requireNonNull(legacyString));
            String stripped = Utils.stripColor(colored);
            if (stripped.length() > 32) stripped = stripped.substring(0, 32);
            bm.setTitle(stripped);
            bm.setAuthor("Networks");
        } catch (Throwable ignored) { }
    }

    /**
     * Set BOTH legacy string display name (meta.setDisplayName) and attempt to set Component displayName if available.
     * Legacy string is the most compatible and ensures GUIs that don't read Component still show names.
     */
    @SuppressWarnings("deprecation")
    private static void setDisplayNameBoth(@Nullable ItemMeta meta, String legacyString) {
        if (meta == null) return;
        String colored = Utils.color(Objects.requireNonNull(legacyString));

        // 1) always set legacy display name (most compatible)
        try {
            meta.setDisplayName(colored);
        } catch (Throwable ignored) { }

        // 2) try to set Component displayName if API present (optional, non-fatal)
        try {
            Component comp = LEGACY.deserialize(colored);
            Method compSetter = meta.getClass().getMethod("displayName", Component.class);
            compSetter.invoke(meta, comp);
        } catch (NoSuchMethodException | NoClassDefFoundError nsme) {
            // component support not available — ignore
        } catch (Throwable ignored) {
            // ignore other reflection issues
        }
    }

    /**
     * Set lore as legacy List<String> and try to set component lore if available.
     */
    @SuppressWarnings("deprecation")
    private static void setLoreBoth(@Nullable ItemMeta meta, String... legacyLore) {
        if (meta == null) return;
        List<String> colored = Utils.colorLore(legacyLore);

        // 1) always set legacy lore
        try {
            meta.setLore(colored);
        } catch (Throwable ignored) { }

        // 2) try to set Component lore (ItemMeta.lore(List<Component>))
        try {
            List<Component> comps = new ArrayList<>(colored.size());
            for (String s : colored) comps.add(LEGACY.deserialize(s));
            Method loreSetter = meta.getClass().getMethod("lore", List.class);
            loreSetter.invoke(meta, comps);
        } catch (NoSuchMethodException | NoClassDefFoundError nsme) {
            // no component lore support
        } catch (Throwable ignored) { }
    }
}
