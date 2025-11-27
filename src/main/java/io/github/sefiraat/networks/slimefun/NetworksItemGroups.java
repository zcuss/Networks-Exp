package io.github.sefiraat.networks.slimefun;

import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.slimefun.groups.DummyItemGroup;
import io.github.sefiraat.networks.slimefun.groups.MainFlexGroup;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;

@UtilityClass
public final class NetworksItemGroups {

    // debug-safe: jangan pakai refleksi ke CraftMeta (menghasilkan IllegalAccessException)
    private static ItemStack mk(Material m, String name) {
        ItemStack it = ItemCreator.create(m, name);

        // Log apa yang kita kirim (nama yang kita berikan)
        System.out.println("[DBG] Created item request: material=" + m + " requestedDisplayName=" + name);

        try {
            if (it.hasItemMeta()) {
                if (it.getItemMeta().hasDisplayName()) {
                    System.out.println("[DBG]   meta.hasDisplayName()=true");
                }
                if (it.getItemMeta().hasLore()) {
                    System.out.println("[DBG]   meta.hasLore()=true");
                }
            }
        } catch (Throwable ignored) {}

        return it;
    }

    public static final MainFlexGroup MAIN = new MainFlexGroup(
            Keys.newKey("main"),
            mk(Material.CHORUS_FLOWER, Theme.applyThemeToString(Theme.MAIN, "Networks V2"))
    );

    public static final DummyItemGroup MATERIALS = new DummyItemGroup(
            Keys.newKey("materials"),
            mk(Material.WHITE_STAINED_GLASS, Theme.applyThemeToString(Theme.MAIN, "Crafting Materials"))
    );

    public static final DummyItemGroup TOOLS = new DummyItemGroup(
            Keys.newKey("tools"),
            mk(Material.PAINTING, Theme.applyThemeToString(Theme.MAIN, "Network Management Tools"))
    );

    public static final DummyItemGroup NETWORK_ITEMS = new DummyItemGroup(
            Keys.newKey("network_items"),
            mk(Material.BLACK_STAINED_GLASS, Theme.applyThemeToString(Theme.MAIN, "Network Items"))
    );

    public static final DummyItemGroup NETWORK_QUANTUMS = new DummyItemGroup(
            Keys.newKey("network_quantums"),
            mk(Material.WHITE_TERRACOTTA, Theme.applyThemeToString(Theme.MAIN, "Network Quantum Storage Devices"))
    );

    public static final DummyItemGroup MORE_NETWORK_BRIDGE = new DummyItemGroup(
            Keys.newKey("more_network_bridge"),
            mk(Material.PINK_STAINED_GLASS, Theme.applyThemeToString(Theme.MAIN, "More Network Bridge"))
    );

    public static final ItemGroup DISABLED_ITEMS = new HiddenItemGroup(
            Keys.newKey("disabled_items"),
            mk(Material.BARRIER, Theme.applyThemeToString(Theme.MAIN, "Disabled/Removed Items"))
    );

    static {
        final Networks plugin = Networks.getInstance();

        // Slimefun Registry with per-group try/catch + logging
        registerSafely(plugin, MAIN, "MAIN");
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
            System.out.println("[DBG] Registering group " + name + " key=" + key.toString());
            g.register(plugin);
            System.out.println("[DBG] Registered group " + name);
        } catch (Throwable t) {
            System.out.println("[ERR] Failed to register group " + name + " : " + t);
            t.printStackTrace();
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
