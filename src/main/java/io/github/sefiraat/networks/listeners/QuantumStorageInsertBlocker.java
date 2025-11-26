package io.github.sefiraat.networks.listeners;

import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.Utils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Minimal blocker: hanya mencegah memasukkan ITEM yang merupakan NetworkQuantumStorage
 * ke BlockMenu yang memang merupakan placed NetworkQuantumStorage di dunia.
 *
 * Penting: DETEKSI tujuan hanya memakai BlockStorage.check(block) sehingga GUIs lain
 * (mis. Quantum Workbench) tidak akan dianggap sebagai quantum storage.
 */
public class QuantumStorageInsertBlocker implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public QuantumStorageInsertBlocker() { }

    private String getDisplayNameSafely(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";

        try {
            Method displayNameMethod = meta.getClass().getMethod("displayName");
            Object compObj = displayNameMethod.invoke(meta);
            if (compObj instanceof Component) {
                return LEGACY.serialize((Component) compObj);
            }
            if (compObj != null) return compObj.toString();
        } catch (NoSuchMethodException ignored) {}
        catch (Throwable ignored) {}

        try {
            Method getDisplayNameMethod = meta.getClass().getMethod("getDisplayName");
            Object res = getDisplayNameMethod.invoke(meta);
            if (res instanceof String) return (String) res;
        } catch (NoSuchMethodException ignored) {}
        catch (Throwable ignored) {}

        return "";
    }

    private void notify(HumanEntity who, String msg) {
        if (who instanceof Player) {
            ((Player) who).sendMessage(Utils.color("&c" + msg));
        }
    }

    /** Cek apakah ItemStack adalah Slimefun item yang merupakan NetworkQuantumStorage */
    private boolean isQuantumItem(ItemStack item) {
        if (item == null) return false;
        try {
            SlimefunItem sf = SlimefunItem.getByItem(item);
            return sf instanceof NetworkQuantumStorage;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ambil BlockMenu tujuan dari InventoryClickEvent (best-effort) */
    private BlockMenu getDestinationBlockMenu(InventoryClickEvent e) {
        try {
            Inventory clicked = e.getClickedInventory();
            InventoryView view = e.getView();
            Inventory top = view.getTopInventory();

            if (clicked != null) {
                InventoryHolder holder = clicked.getHolder();
                if (holder instanceof BlockMenu) return (BlockMenu) holder;
            }

            // Shift-click biasanya menaruh ke top inventory
            if (e.isShiftClick()) {
                InventoryHolder topHolder = top.getHolder();
                if (topHolder instanceof BlockMenu) return (BlockMenu) topHolder;
            }

            // Hotbar moves -> top inventory might be target
            InventoryAction action = e.getAction();
            if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
                InventoryHolder topHolder = top.getHolder();
                if (topHolder instanceof BlockMenu) return (BlockMenu) topHolder;
            }

            // fallback: if top is BlockMenu
            if (top != null) {
                InventoryHolder topHolder = top.getHolder();
                if (topHolder instanceof BlockMenu) return (BlockMenu) topHolder;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Very strict: pastikan BlockMenu tersebut benar-benar milik sebuah placed block,
     * dan BlockStorage.check(block) mengembalikan instance NetworkQuantumStorage.
     *
     * --> tidak menggunakan caches/title fallback agar tidak salah tangkap GUIs lain.
     */
    private boolean blockMenuIsPlacedQuantumStorage(BlockMenu menu) {
        if (menu == null) return false;
        try {
            Location loc = menu.getLocation();
            if (loc == null) return false;
            Block block = loc.getBlock();
            if (block == null) return false;

            Object maybe = BlockStorage.check(block);
            return maybe instanceof NetworkQuantumStorage;
        } catch (Throwable ignored) {}
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        BlockMenu dest = getDestinationBlockMenu(e);
        if (!blockMenuIsPlacedQuantumStorage(dest)) return; // hanya urus bila tujuan memang placed QuantumStorage

        // SHIFT-CLICK
        if (e.isShiftClick()) {
            ItemStack shiftItem = e.getCurrentItem() != null ? e.getCurrentItem() : e.getCursor();
            if (isQuantumItem(shiftItem)) {
                e.setCancelled(true);
                notify(e.getWhoClicked(), "You cannot put a Quantum Storage item into another Quantum Storage.");
            }
            return;
        }

        // HOTBAR swap/move
        InventoryAction action = e.getAction();
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            try {
                ItemStack hotbarItem = e.getView().getBottomInventory().getItem(e.getHotbarButton());
                if (isQuantumItem(hotbarItem)) {
                    e.setCancelled(true);
                    notify(e.getWhoClicked(), "You cannot put a Quantum Storage item into another Quantum Storage.");
                }
            } catch (Throwable ignored) {}
            return;
        }

        // normal placement: cursor -> target (dest is BlockMenu)
        ItemStack cursor = e.getCursor();
        if (cursor != null && isQuantumItem(cursor)) {
            if (e.getClickedInventory() == dest || e.getClickedInventory() == e.getView().getTopInventory()) {
                e.setCancelled(true);
                notify(e.getWhoClicked(), "You cannot put a Quantum Storage item into another Quantum Storage.");
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        try {
            Map<Integer, ItemStack> newItems = e.getNewItems();
            if (newItems == null || newItems.isEmpty()) return;

            Inventory top = e.getView().getTopInventory();
            InventoryHolder topHolder = top.getHolder();
            if (!(topHolder instanceof BlockMenu)) return;

            BlockMenu menu = (BlockMenu) topHolder;
            if (!blockMenuIsPlacedQuantumStorage(menu)) return;

            for (ItemStack s : newItems.values()) {
                if (s == null || s.getType() == Material.AIR) continue;
                if (isQuantumItem(s)) {
                    e.setCancelled(true);
                    if (e.getWhoClicked() != null) notify(e.getWhoClicked(), "You cannot put a Quantum Storage item into another Quantum Storage.");
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent e) {
        Inventory dest = e.getDestination();
        try {
            InventoryHolder holder = dest.getHolder();
            if (!(holder instanceof BlockMenu)) return;
            BlockMenu menu = (BlockMenu) holder;
            if (!blockMenuIsPlacedQuantumStorage(menu)) return;

            ItemStack item = e.getItem();
            if (isQuantumItem(item)) {
                e.setCancelled(true);
                String name = getDisplayNameSafely(item);
                Bukkit.getLogger().info("[QuantumBlocker] Prevented transfer into quantum via hopper/plugin for item: " +
                        (name == null || name.isEmpty() ? "<no-name>" : Utils.stripColor(name)));
            }
        } catch (Throwable ignored) {}
    }
}
