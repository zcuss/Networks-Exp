package io.github.sefiraat.networks.utils;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@Getter
@SuppressWarnings("DuplicatedCode")
public enum Theme {
    GOLD(ChatColor.GOLD, ""),
    WARNING(ChatColor.YELLOW, "Warning"),
    ERROR(ChatColor.RED, "Error"),
    NOTICE(ChatColor.WHITE, "Notice"),
    PASSIVE(ChatColor.GRAY, ""),
    SUCCESS(ChatColor.GREEN, "Success"),
    MAIN(ChatColor.of("#9a00f8"), "Alone"),
    CLICK_INFO(ChatColor.of("#e4ed32"), "Click here"),
    RESEARCH(ChatColor.of("#a60e03"), "Research"),
    CRAFTING(ChatColor.of("#dbcea9"), "Crafting Material"),
    MACHINE(ChatColor.of("#3295a8"), "Machine"),
    TOOL(ChatColor.of("#6b32a8"), "Tool"),
    MECHANISM(ChatColor.of("#3295a8"), "Mechanism"),
    FUEL(ChatColor.of("#112211"), "Fossil Fuel"),
    MATERIAL_CLASS(ChatColor.of("#a4c2ba"), "Material Class"),
    RECIPE_TYPE(ChatColor.of("#ffe89c"), "Recipe Type"),
    GUIDE(ChatColor.of("#444444"), "Guide");

    @Getter
    private static final Theme[] cachedValues = values();
    private final ChatColor color;
    private final String loreLine;

    @ParametersAreNonnullByDefault
    Theme(ChatColor color, String loreLine) {
        this.color = color;
        this.loreLine = loreLine;
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public static SlimefunItemStack themedSlimefunItemStack(String id, ItemStack itemStack, Theme themeType, String name, String... lore) {
        ChatColor passiveColor = Theme.PASSIVE.getColor();
        List<String> finalLore = new ArrayList<>();
        finalLore.add("");
        for (String s : lore) {
            finalLore.add(passiveColor.toString() + s);
        }
        finalLore.add("");
        finalLore.add(applyThemeToString(Theme.CLICK_INFO, themeType.getLoreLine()));
        return new SlimefunItemStack(
                id,
                itemStack,
                Theme.applyThemeToString(themeType, name),
                finalLore.toArray(new String[0])
        );
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public static String applyThemeToString(Theme themeType, String string) {
        // Pastikan kita menggunakan kode warna legacy (ChatColor.toString())
        return themeType.getColor().toString() + string;
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public static ItemStack themedItemStack(Material material, Theme themeType, String name, String... lore) {
        ChatColor passiveColor = Theme.PASSIVE.getColor();
        List<String> finalLore = new ArrayList<>();
        finalLore.add("");
        for (String s : lore) {
            finalLore.add(passiveColor.toString() + s);
        }
        finalLore.add("");
        finalLore.add(applyThemeToString(Theme.CLICK_INFO, themeType.getLoreLine()));
        return ItemCreator.create(
                material,
                Theme.applyThemeToString(themeType, name),
                finalLore.toArray(new String[0])
        );
    }

    @Override
    public String toString() {
        return this.color.toString();
    }
}
