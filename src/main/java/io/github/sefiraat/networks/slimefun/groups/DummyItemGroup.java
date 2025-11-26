package io.github.sefiraat.networks.slimefun.groups;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;

/**
 * DummyItemGroup sederhana yang memastikan icon yang dipassing di constructor
 * tetap dipakai oleh Slimefun (mengembalikan clone dari item yang sudah di-set).
 */
public class DummyItemGroup extends ItemGroup {

    public DummyItemGroup(NamespacedKey key, ItemStack item) {
        super(key, item);
    }

    @Override
    public ItemStack getItem(@Nonnull Player player) {
        ItemStack it = super.getItem(player);
        return (it == null) ? null : it.clone();
    }

    @Override
    public boolean isVisible(@Nonnull Player player) {
        return true;
    }
}
