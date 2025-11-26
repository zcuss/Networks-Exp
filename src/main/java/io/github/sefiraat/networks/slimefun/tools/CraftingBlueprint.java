package io.github.sefiraat.networks.slimefun.tools;

import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StringUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentCraftingBlueprintType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.UnplaceableBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CraftingBlueprint extends UnplaceableBlock implements DistinctiveItem {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public CraftingBlueprint(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @ParametersAreNonnullByDefault
    public static void setBlueprint(ItemStack blueprint, ItemStack[] recipe, ItemStack output) {
        final ItemMeta itemMeta = blueprint.getItemMeta();
        final ItemMeta outputMeta = output.getItemMeta();

        // store blueprint instance into persistent data
        DataTypeMethods.setCustom(itemMeta, Keys.BLUEPRINT_INSTANCE, PersistentCraftingBlueprintType.TYPE, new BlueprintInstance(recipe, output));

        final List<String> legacyLoreStrings = new ArrayList<>();
        legacyLoreStrings.add(Theme.CLICK_INFO + "Assigned Recipe");

        // helper: get plain display name safely (component-first, fallback legacy -> plain)
        for (ItemStack item : recipe) {
            if (item == null) {
                legacyLoreStrings.add(Theme.PASSIVE + "Nothing");
                continue;
            }

            String plainName = getPlainDisplayNameSafe(item.getItemMeta());
            if (plainName != null && !plainName.isBlank()) {
                legacyLoreStrings.add(Theme.PASSIVE + plainName);
            } else {
                legacyLoreStrings.add(Theme.PASSIVE + StringUtils.toTitleCase(item.getType().name()));
            }
        }

        legacyLoreStrings.add("");
        legacyLoreStrings.add(Theme.CLICK_INFO + "Outputting");

        String outPlain = getPlainDisplayNameSafe(outputMeta);
        if (outPlain != null && !outPlain.isBlank()) {
            legacyLoreStrings.add(Theme.PASSIVE + outPlain);
        } else {
            legacyLoreStrings.add(Theme.PASSIVE + StringUtils.toTitleCase(output.getType().name()));
        }

        // Try to set lore using Component API first; fallback to reflective setLore(List<String>)
        try {
            List<Component> comps = new ArrayList<>(legacyLoreStrings.size());
            for (String s : legacyLoreStrings) {
                comps.add(LEGACY.deserialize(s));
            }
            // ItemMeta.lore(List<Component>) exists in newer API
            itemMeta.lore(comps);
        } catch (NoSuchMethodError | NoClassDefFoundError nsme) {
            // fallback: setLore(List<String>) reflectively
            try {
                Method setLore = itemMeta.getClass().getMethod("setLore", List.class);
                setLore.invoke(itemMeta, legacyLoreStrings);
            } catch (Throwable ignored) {
                // if even fallback fails, ignore silently to preserve compatibility
            }
        }

        blueprint.setItemMeta(itemMeta);
    }

    /**
     * Dapatkan nama display polos dari ItemMeta.
     * Coba gunakan API Component (displayName()), kalau tidak tersedia fallback ke legacy getDisplayName()
     * dan konversi ke plain text lewat LegacyComponentSerializer -> PlainTextComponentSerializer.
     */
    private static String getPlainDisplayNameSafe(ItemMeta meta) {
        if (meta == null) return null;

        // try component-based
        try {
            Component comp = meta.displayName();
            if (comp != null) {
                return PLAIN.serialize(comp);
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // fallback below
        }

        // legacy fallback: getDisplayName() (may be deprecated but still available on older APIs)
        try {
            String legacy = (String) meta.getClass().getMethod("getDisplayName").invoke(meta);
            if (legacy != null && !legacy.isEmpty()) {
                try {
                    // convert legacy-coloured string into Component then to plain text to strip colors correctly
                    Component c = LEGACY.deserialize(legacy);
                    return PLAIN.serialize(c);
                } catch (Throwable ignored) {
                    // last-resort: return raw legacy stripped of section codes by removing '§' and following char
                    return legacy.replaceAll("(?i)§[0-9A-FK-OR]", "");
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException | SecurityException ignored) {
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }

        return null;
    }

    @Override
    public boolean canStack(@Nonnull ItemMeta itemMetaOne, @Nonnull ItemMeta itemMetaTwo) {
        return itemMetaOne.getPersistentDataContainer().equals(itemMetaTwo.getPersistentDataContainer());
    }

}
