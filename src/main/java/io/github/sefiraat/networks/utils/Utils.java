package io.github.sefiraat.networks.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public final class Utils {

    private Utils() { }

    // Pastikan tidak pernah mengembalikan null
    public static String color(String str) {
        if (str == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public static List<String> colorLore(String... lore) {
        if (lore == null || lore.length == 0) return new ArrayList<>();
        List<String> ll = new ArrayList<>();
        for (String l : lore) {
            ll.add(color(l));
        }
        return ll;
    }

    public static void giveOrDropItem(Player p, ItemStack toGive) {
        for (ItemStack leftover : p.getInventory().addItem(toGive).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
    }

    public static void send(CommandSender p, String message) {
        p.sendMessage(color("&7[&6Network&7] &r" + message));
    }

    // Strip color codes (berguna untuk BookMeta title)
    public static String stripColor(String input) {
        if (input == null) return "";
        // Pastikan & -> § lalu strip
        String t = ChatColor.translateAlternateColorCodes('&', input);
        return ChatColor.stripColor(t);
    }

    // helper untuk debug (opsional)
    public static String loreToString(String... lines) {
        if (lines == null || lines.length == 0) return "[]";
        return Arrays.toString(colorLore(lines).toArray(new String[0]));
    }
}
