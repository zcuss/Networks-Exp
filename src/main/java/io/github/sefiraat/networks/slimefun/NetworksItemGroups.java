package io.github.sefiraat.networks.slimefun;

import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.slimefun.groups.DummyItemGroup;
import io.github.sefiraat.networks.slimefun.groups.MainFlexGroup;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@UtilityClass
public final class NetworksItemGroups {

    // Guard supaya registerAll hanya berjalan sekali
    private static boolean registered = false;

    // helper pembuat ItemStack (tetap sederhana)
    private static ItemStack mk(Material m, String name) {
        ItemStack it = ItemCreator.create(m, name);

        try {
            Networks plugin = Networks.getInstance();
            if (plugin != null && it.hasItemMeta()) {
                if (it.getItemMeta().hasDisplayName()) {
                    plugin.getLogger().fine("[DBG] Created item (hasDisplayName): " + name);
                }
                if (it.getItemMeta().hasLore()) {
                    plugin.getLogger().fine("[DBG] Created item (hasLore): " + name);
                }
            }
        } catch (Throwable ignored) {}

        return it;
    }

    // NOTE: menggunakan NamespacedKey(Networks.getInstance(), "...") untuk memastikan namespace konsisten
    public static final MainFlexGroup MAIN = new MainFlexGroup(
            new NamespacedKey(Networks.getInstance(), "main"),
            mk(Material.CHORUS_FLOWER, Theme.applyThemeToString(Theme.MAIN, "Networks V2"))
    );

    public static final DummyItemGroup MATERIALS = new DummyItemGroup(
            new NamespacedKey(Networks.getInstance(), "materials"),
            mk(Material.WHITE_STAINED_GLASS, Theme.applyThemeToString(Theme.MAIN, "Crafting Materials"))
    );

    public static final DummyItemGroup TOOLS = new DummyItemGroup(
            new NamespacedKey(Networks.getInstance(), "tools"),
            mk(Material.PAINTING, Theme.applyThemeToString(Theme.MAIN, "Network Management Tools"))
    );

    public static final DummyItemGroup NETWORK_ITEMS = new DummyItemGroup(
            new NamespacedKey(Networks.getInstance(), "network_items"),
            mk(Material.BLACK_STAINED_GLASS, Theme.applyThemeToString(Theme.MAIN, "Network Items"))
    );

    public static final DummyItemGroup NETWORK_QUANTUMS = new DummyItemGroup(
            new NamespacedKey(Networks.getInstance(), "network_quantums"),
            mk(Material.WHITE_TERRACOTTA, Theme.applyThemeToString(Theme.MAIN, "Network Quantum Storage Devices"))
    );

    public static final DummyItemGroup MORE_NETWORK_BRIDGE = new DummyItemGroup(
            new NamespacedKey(Networks.getInstance(), "more_network_bridge"),
            mk(Material.PINK_STAINED_GLASS, Theme.applyThemeToString(Theme.MAIN, "More Network Bridge"))
    );

    public static final ItemGroup DISABLED_ITEMS = new HiddenItemGroup(
            new NamespacedKey(Networks.getInstance(), "disabled_items"),
            mk(Material.BARRIER, Theme.applyThemeToString(Theme.MAIN, "Disabled/Removed Items"))
    );

    /**
     * Daftarkan semua grup Slimefun — guarded supaya hanya terjadi sekali.
     * Panggil ini dari onEnable() SEBELUM mendaftarkan item.
     */
    public static synchronized void registerAll(Networks plugin) {
        if (registered) {
            plugin.getLogger().fine("NetworksItemGroups.registerAll() called but groups already registered — skipping.");
            return;
        }
        registered = true;

        // 1) register MAIN first (parent must exist in registry)
        registerSafely(plugin, MAIN, "MAIN");

        // 2) set parent -> agar grup tampil *hanya* di dalam MAIN (bukan top-level juga)
        NamespacedKey mainKey = MAIN.getKey();
        setParentIfPossible(MATERIALS, mainKey, plugin);
        setParentIfPossible(TOOLS, mainKey, plugin);
        setParentIfPossible(NETWORK_ITEMS, mainKey, plugin);
        setParentIfPossible(NETWORK_QUANTUMS, mainKey, plugin);
        setParentIfPossible(MORE_NETWORK_BRIDGE, mainKey, plugin);
        setParentIfPossible(DISABLED_ITEMS, mainKey, plugin);

        // 3) register children AFTER parent has been registered and parent field set
        registerSafely(plugin, MATERIALS, "MATERIALS");
        registerSafely(plugin, TOOLS, "TOOLS");
        registerSafely(plugin, NETWORK_ITEMS, "NETWORK_ITEMS");
        registerSafely(plugin, NETWORK_QUANTUMS, "NETWORK_QUANTUMS");
        registerSafely(plugin, DISABLED_ITEMS, "DISABLED_ITEMS");
        registerSafely(plugin, MORE_NETWORK_BRIDGE, "MORE_NETWORK_BRIDGE");
    }

    private static void registerSafely(Networks plugin, ItemGroup g, String name) {
        try {
            NamespacedKey key = g.getKey();
            plugin.getLogger().info("[DBG] Registering group " + name + " key=" + key.toString());
            g.register(plugin);
            plugin.getLogger().info("[DBG] Registered group " + name);
        } catch (Throwable t) {
            plugin.getLogger().severe("[ERR] Failed to register group " + name + " : " + t);
            t.printStackTrace();
        }
    }

    /**
     * Try to set the parent for a child ItemGroup so it only appears inside parent menu.
     * Uses either setParent(NamespacedKey) or sets a private 'parent' field via reflection.
     */
    private static void setParentIfPossible(ItemGroup child, NamespacedKey parentKey, Networks plugin) {
        if (child == null || parentKey == null) return;

        // debug: log current parent field if any
        logParentField(child, "BEFORE", plugin);

        try {
            Method m = child.getClass().getMethod("setParent", NamespacedKey.class);
            m.invoke(child, parentKey);
            plugin.getLogger().fine("[DBG] setParent via method for group " + child.getKey());
            logParentField(child, "AFTER(setParent method)", plugin);
            return;
        } catch (NoSuchMethodException ignored) {
            // try field fallback
        } catch (Throwable t) {
            plugin.getLogger().warning("[WARN] Failed to call setParent method reflectively on group "
                    + child.getKey() + " : " + t.getMessage());
        }

        try {
            Field f = null;
            Class<?> cls = child.getClass();
            while (cls != null) {
                try {
                    f = cls.getDeclaredField("parent");
                    break;
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (f != null) {
                f.setAccessible(true);
                f.set(child, parentKey);
                plugin.getLogger().fine("[DBG] setParent via field for group " + child.getKey());
            } else {
                plugin.getLogger().fine("[DBG] No parent setter/field found for group " + child.getKey());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[WARN] Failed to set parent field reflectively for group "
                    + child.getKey() + " : " + t.getMessage());
        }

        logParentField(child, "AFTER(fallback)", plugin);
    }

    private static void logParentField(ItemGroup g, String when, Networks plugin) {
        try {
            Field f = null;
            Class<?> cls = g.getClass();
            while (cls != null) {
                try {
                    f = cls.getDeclaredField("parent");
                    break;
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(g);
                plugin.getLogger().info("[DBG] parent field for " + g.getKey() + " " + when + " = " + String.valueOf(val));
            } else {
                plugin.getLogger().fine("[DBG] no parent field on " + g.getKey() + " " + when);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[WARN] failed to read parent field for " + g.getKey() + " : " + t.getMessage());
        }
    }

    public static class HiddenItemGroup extends ItemGroup {

        public HiddenItemGroup(NamespacedKey key, ItemStack item) {
            super(key, item);
        }

        @Override
        public boolean isHidden(@Nonnull Player p) {
            return true;
        }
    }
}
