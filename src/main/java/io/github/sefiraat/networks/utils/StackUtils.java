package io.github.sefiraat.networks.utils;

import com.balugaq.netex.utils.Converter;
import io.github.sefiraat.networks.network.stackcaches.ItemStackCache;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
// import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Method;
import java.util.*;

@UtilityClass
public class StackUtils {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    @Nonnull
    public static ItemStack getAsQuantity(@Nonnull ItemStack itemStack, int amount) {
        return Converter.getItem(itemStack, amount);
    }

    public static boolean itemsMatch(@Nullable ItemStack itemStack1, @Nullable ItemStack itemStack2) {
        return itemsMatch(new ItemStackCache(itemStack1), itemStack2, true);
    }

    /**
     * Checks if items match each other, checks go in order from lightest to heaviest
     *
     * @param cache     The cached {@link ItemStack} to compare against
     * @param itemStack The {@link ItemStack} being evaluated
     * @return True if items match
     */
    public static boolean itemsMatch(@Nonnull ItemStackCache cache, @Nullable ItemStack itemStack, boolean checkLore) {
        // Null check
        if (cache.getItemStack() == null || itemStack == null) {
            return itemStack == null && cache.getItemStack() == null;
        }

        // If types do not match, then the items cannot possibly match
        if (itemStack.getType() != cache.getItemType()) {
            return false;
        }

        // If either item does not have a meta then either a mismatch or both without meta = vanilla
        if (!itemStack.hasItemMeta() || !cache.getItemStack().hasItemMeta()) {
            return itemStack.hasItemMeta() == cache.getItemStack().hasItemMeta();
        }

        // Now we need to compare meta's directly - cache is already out, but let's fetch the 2nd meta also
        final ItemMeta itemMeta = itemStack.getItemMeta();
        final ItemMeta cachedMeta = cache.getItemMeta();

        // ItemMetas are different types and cannot match
        if (!itemMeta.getClass().equals(cachedMeta.getClass())) {
            return false;
        }

        // Quick meta-extension escapes
        if (canQuickEscapeMetaVariant(itemMeta, cachedMeta)) {
            return false;
        }

        // Has a display name (checking the name occurs later)
        if (itemMeta.hasDisplayName() != cachedMeta.hasDisplayName()) {
            return false;
        }

        // Custom model data is different, no match
        final boolean hasCustomOne = itemMeta.hasCustomModelData();
        final boolean hasCustomTwo = cachedMeta.hasCustomModelData();
        if (hasCustomOne) {
            if (!hasCustomTwo || itemMeta.getCustomModelData() != cachedMeta.getCustomModelData()) {
                return false;
            }
        } else if (hasCustomTwo) {
            return false;
        }

        // PDCs don't match
        if (!itemMeta.getPersistentDataContainer().equals(cachedMeta.getPersistentDataContainer())) {
            return false;
        }

        // Make sure enchantments match
        if (!itemMeta.getEnchants().equals(cachedMeta.getEnchants())) {
            return false;
        }

        // Check item flags
        if (!itemMeta.getItemFlags().equals(cachedMeta.getItemFlags())) {
            return false;
        }

        // Check the lore (use safe getter that accounts for component vs legacy)
        if (checkLore && !Objects.equals(getLoreAsStrings(itemMeta), getLoreAsStrings(cachedMeta))) {
            return false;
        }

        // Slimefun ID check no need to worry about distinction, covered in PDC + lore
        final Optional<String> optionalStackId1 = Slimefun.getItemDataService().getItemData(itemMeta);
        final Optional<String> optionalStackId2 = Slimefun.getItemDataService().getItemData(cachedMeta);
        if (optionalStackId1.isPresent() && optionalStackId2.isPresent()) {
            if (!optionalStackId1.get().equals(optionalStackId2.get())) return false;
        }

        // Finally, check the display name using safe getter
        String name1 = getDisplayNamePlain(itemMeta);
        String name2 = getDisplayNamePlain(cachedMeta);
        return name1 == null || name1.equals(name2);
    }

    public boolean canQuickEscapeMetaVariant(@Nonnull ItemMeta metaOne, @Nonnull ItemMeta metaTwo) {

        // Damageable (first as everything can be damageable apparently)
        if (metaOne instanceof Damageable instanceOne && metaTwo instanceof Damageable instanceTwo) {
            if (instanceOne.getDamage() != instanceTwo.getDamage()) {
                return true;
            }
        }

        // Axolotl
        if (metaOne instanceof AxolotlBucketMeta instanceOne && metaTwo instanceof AxolotlBucketMeta instanceTwo) {
            if (instanceOne.hasVariant() != instanceTwo.hasVariant()) {
                return true;
            }

            if (!instanceOne.hasVariant() || !instanceTwo.hasVariant())
                return true;

            if (instanceOne.getVariant() != instanceTwo.getVariant()) {
                return true;
            }
        }

        // Banner
        if (metaOne instanceof BannerMeta instanceOne && metaTwo instanceof BannerMeta instanceTwo) {
            if (!instanceOne.getPatterns().equals(instanceTwo.getPatterns())) {
                return true;
            }
        }

        // Books
        if (metaOne instanceof BookMeta instanceOne && metaTwo instanceof BookMeta instanceTwo) {
            if (instanceOne.getPageCount() != instanceTwo.getPageCount()) {
                return true;
            }
            if (!Objects.equals(instanceOne.getAuthor(), instanceTwo.getAuthor())) {
                return true;
            }
            if (!Objects.equals(instanceOne.getTitle(), instanceTwo.getTitle())) {
                return true;
            }
            if (!Objects.equals(instanceOne.getGeneration(), instanceTwo.getGeneration())) {
                return true;
            }
        }

        // Bundle
        if (metaOne instanceof BundleMeta instanceOne && metaTwo instanceof BundleMeta instanceTwo) {
            if (instanceOne.hasItems() != instanceTwo.hasItems()) {
                return true;
            }
            if (!instanceOne.getItems().equals(instanceTwo.getItems())) {
                return true;
            }
        }

        // Compass
        if (metaOne instanceof CompassMeta instanceOne && metaTwo instanceof CompassMeta instanceTwo) {
            if (instanceOne.isLodestoneTracked() != instanceTwo.isLodestoneTracked()) {
                return true;
            }
            if (!Objects.equals(instanceOne.getLodestone(), instanceTwo.getLodestone())) {
                return true;
            }
        }

        // Crossbow
        if (metaOne instanceof CrossbowMeta instanceOne && metaTwo instanceof CrossbowMeta instanceTwo) {
            if (instanceOne.hasChargedProjectiles() != instanceTwo.hasChargedProjectiles()) {
                return true;
            }
            if (!instanceOne.getChargedProjectiles().equals(instanceTwo.getChargedProjectiles())) {
                return true;
            }
        }

        // Enchantment Storage
        if (metaOne instanceof EnchantmentStorageMeta instanceOne && metaTwo instanceof EnchantmentStorageMeta instanceTwo) {
            if (instanceOne.hasStoredEnchants() != instanceTwo.hasStoredEnchants()) {
                return true;
            }
            if (!instanceOne.getStoredEnchants().equals(instanceTwo.getStoredEnchants())) {
                return true;
            }
        }

        // Firework Star
        if (metaOne instanceof FireworkEffectMeta instanceOne && metaTwo instanceof FireworkEffectMeta instanceTwo) {
            if (!Objects.equals(instanceOne.getEffect(), instanceTwo.getEffect())) {
                return true;
            }
        }

        // Firework
        if (metaOne instanceof FireworkMeta instanceOne && metaTwo instanceof FireworkMeta instanceTwo) {
            if (instanceOne.getPower() != instanceTwo.getPower()) {
                return true;
            }
            if (!instanceOne.getEffects().equals(instanceTwo.getEffects())) {
                return true;
            }
        }

        // Leather Armor
        if (metaOne instanceof LeatherArmorMeta instanceOne && metaTwo instanceof LeatherArmorMeta instanceTwo) {
            if (!instanceOne.getColor().equals(instanceTwo.getColor())) {
                return true;
            }
        }

        // Maps
        if (metaOne instanceof MapMeta instanceOne && metaTwo instanceof MapMeta instanceTwo) {
            if (instanceOne.hasMapView() != instanceTwo.hasMapView()) {
                return true;
            }
            // gunakan helper safe agar tidak panggil deprecated langsung
            if (mapHasLocationNameSafe(instanceOne) != mapHasLocationNameSafe(instanceTwo)) {
                return true;
            }
            if (instanceOne.hasColor() != instanceTwo.hasColor()) {
                return true;
            }
            if (!Objects.equals(instanceOne.getMapView(), instanceTwo.getMapView())) {
                return true;
            }
            if (!Objects.equals(mapGetLocationNameSafe(instanceOne), mapGetLocationNameSafe(instanceTwo))) {
                return true;
            }
            if (!Objects.equals(instanceOne.getColor(), instanceTwo.getColor())) {
                return true;
            }
        }

        // Potion
        if (metaOne instanceof PotionMeta instanceOne && metaTwo instanceof PotionMeta instanceTwo) {
            if (Slimefun.getMinecraftVersion().isAtLeast(MinecraftVersion.MINECRAFT_1_20_5)) {
                // gunakan safe getter untuk base potion type
                if (!Objects.equals(getBasePotionTypeSafe(instanceOne), getBasePotionTypeSafe(instanceTwo))) {
                    return true;
                }
            } else {
                if (!Objects.equals(getBasePotionDataSafe(instanceOne), getBasePotionDataSafe(instanceTwo))) {
                    return true;
                }
            }
            if (instanceOne.hasCustomEffects() != instanceTwo.hasCustomEffects()) {
                return true;
            }
            if (instanceOne.hasColor() != instanceTwo.hasColor()) {
                return true;
            }
            if (!Objects.equals(instanceOne.getColor(), instanceTwo.getColor())) {
                return true;
            }
            if (!instanceOne.getCustomEffects().equals(instanceTwo.getCustomEffects())) {
                return true;
            }
        }

        // Skull
        if (metaOne instanceof SkullMeta instanceOne && metaTwo instanceof SkullMeta instanceTwo) {
            if (instanceOne.hasOwner() != instanceTwo.hasOwner()) {
                return true;
            }
            if (!Objects.equals(instanceOne.getOwningPlayer(), instanceTwo.getOwningPlayer())) {
                return true;
            }
        }

        // Stew
        if (metaOne instanceof SuspiciousStewMeta instanceOne && metaTwo instanceof SuspiciousStewMeta instanceTwo) {
            if (!Objects.equals(instanceOne.getCustomEffects(), instanceTwo.getCustomEffects())) {
                return true;
            }
        }

        // Fish Bucket
        if (metaOne instanceof TropicalFishBucketMeta instanceOne && metaTwo instanceof TropicalFishBucketMeta instanceTwo) {
            if (instanceOne.hasVariant() != instanceTwo.hasVariant()) {
                return true;
            }
            if (!instanceOne.getPattern().equals(instanceTwo.getPattern())) {
                return true;
            }
            if (!instanceOne.getBodyColor().equals(instanceTwo.getBodyColor())) {
                return true;
            }
            return !instanceOne.getPatternColor().equals(instanceTwo.getPatternColor());
        }

        // Cannot escape via any meta extension check
        return false;
    }

    // -------------------------
    // Safe helpers (component-aware + reflection fallback)
    // -------------------------

    private static List<String> getLoreAsStrings(@Nullable ItemMeta meta) {
        if (meta == null) return Collections.emptyList();

        // coba component-based lore() returning List<Component>
        try {
            Method loreMethod = meta.getClass().getMethod("lore");
            Object res = loreMethod.invoke(meta);
            if (res instanceof List) {
                List<?> comps = (List<?>) res;
                List<String> out = new ArrayList<>(comps.size());
                for (Object c : comps) {
                    if (c instanceof Component) out.add(LEGACY.serialize((Component) c));
                    else out.add(c == null ? null : String.valueOf(c));
                }
                return out;
            }
        } catch (NoSuchMethodException ignored) {
            // no component lore
        } catch (Throwable ignored) {
            // other failure -> fallback
        }

        // fallback ke legacy getLore() via refleksi (hindari compile-time deprecation)
        try {
            Method legacyGetLore = meta.getClass().getMethod("getLore");
            Object legacy = legacyGetLore.invoke(meta);
            if (legacy instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> ll = (List<String>) legacy;
                return ll == null ? Collections.emptyList() : new ArrayList<>(ll);
            }
        } catch (Throwable ignored) {
        }

        return Collections.emptyList();
    }

    private static @Nullable String getDisplayNamePlain(@Nullable ItemMeta meta) {
        if (meta == null) return null;

        // coba component-based displayName()
        try {
            Method m = meta.getClass().getMethod("displayName");
            Object comp = m.invoke(meta);
            if (comp instanceof Component) return LEGACY.serialize((Component) comp);
        } catch (NoSuchMethodException ignored) {
            // no component displayName
        } catch (Throwable ignored) {
        }

        // fallback ke legacy getDisplayName()
        try {
            Method legacy = meta.getClass().getMethod("getDisplayName");
            Object res = legacy.invoke(meta);
            return res == null ? null : String.valueOf(res);
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean mapHasLocationNameSafe(@Nullable MapMeta meta) {
        if (meta == null) return false;
        try {
            Method m = meta.getClass().getMethod("hasLocationName");
            Object r = m.invoke(meta);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) {
        }
        // As ultimate fallback try legacy method name (same name) via reflection already attempted - return false
        return false;
    }

    private static @Nullable String mapGetLocationNameSafe(@Nullable MapMeta meta) {
        if (meta == null) return null;
        try {
            Method m = meta.getClass().getMethod("getLocationName");
            Object r = m.invoke(meta);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static @Nullable Object getBasePotionDataSafe(@Nullable PotionMeta meta) {
        if (meta == null) return null;
        try {
            Method m = meta.getClass().getMethod("getBasePotionData");
            return m.invoke(meta);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static @Nullable Object getBasePotionTypeSafe(@Nullable PotionMeta meta) {
        if (meta == null) return null;
        try {
            Method m = meta.getClass().getMethod("getBasePotionType");
            return m.invoke(meta);
        } catch (Throwable ignored) {
        }
        return null;
    }

    // -------------------------
    // Cooldown helpers (unchanged)
    // -------------------------

    /**
     * Put item on cooldown (PDC-based)
     *
     * @param itemStack         The {@link ItemStack} to tag
     * @param durationInSeconds Duration in seconds
     */
    @ParametersAreNonnullByDefault
    public static void putOnCooldown(ItemStack itemStack, int durationInSeconds) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            PersistentDataAPI.setLong(itemMeta, Keys.ON_COOLDOWN, System.currentTimeMillis() + (durationInSeconds * 1000L));
            itemStack.setItemMeta(itemMeta);
        }
    }

    /**
     * Check cooldown (PDC-based)
     *
     * @param itemStack The {@link ItemStack} to check
     * @return true if still on cooldown
     */
    @ParametersAreNonnullByDefault
    public static boolean isOnCooldown(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            long cooldownUntil = PersistentDataAPI.getLong(itemMeta, Keys.ON_COOLDOWN, 0);
            return System.currentTimeMillis() < cooldownUntil;
        }
        return false;
    }
}
