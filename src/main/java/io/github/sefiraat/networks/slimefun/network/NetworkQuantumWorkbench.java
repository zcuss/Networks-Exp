package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quantum Workbench with support for using quantum items as ingredient sources,
 * and avoiding double-crafting / races by placing result deterministically.
 */
public class NetworkQuantumWorkbench extends SlimefunItem {

    private static final int[] BACKGROUND_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13, 14, 15, 16, 17, 18, 22, 24, 26, 27, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 19, 20, 21, 28, 29, 30
    };
    private static final int CRAFT_SLOT = 23;
    private static final int OUTPUT_SLOT = 25;

    private static final ItemStack CRAFT_BUTTON_STACK = ItemCreator.create(
            Material.CRAFTING_TABLE,
            Theme.CLICK_INFO + "Click to entangle"
    );

    private static final Map<ItemStack[], ItemStack> RECIPES = new HashMap<>();

    public static final RecipeType TYPE = new RecipeType(
            Keys.newKey("quantum-workbench"),
            Theme.themedItemStack(
                    Material.BRAIN_CORAL_BLOCK,
                    Theme.MACHINE,
                    "Quantum Workbench",
                    "Crafted using the Quantum Workbench."
            ),
            NetworkQuantumWorkbench::addRecipe
    );

    @ParametersAreNonnullByDefault
    public NetworkQuantumWorkbench(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    public static void addRecipe(ItemStack[] input, ItemStack output) {
        RECIPES.put(input, output);
    }

    @Override
    public void preRegister() {
        addItemHandler(getBlockBreakHandler());
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {
            @Override
            public void init() {
                drawBackground(BACKGROUND_SLOTS);
                addItem(CRAFT_SLOT, CRAFT_BUTTON_STACK, (p, slot, item, action) -> false);
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return BlockStorage.check(block).canUse(player, false)
                        && Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return new int[]{OUTPUT_SLOT};
                }
                return new int[0];
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
                menu.addMenuClickHandler(CRAFT_SLOT, (p, slot, item, action) -> {
                    craft(menu);
                    return false;
                });
            }
        };
    }

    public void craft(@Nonnull BlockMenu menu) {
        final ItemStack itemInOutput = menu.getItemInSlot(OUTPUT_SLOT);

        // only allow if output empty
        if (itemInOutput != null && itemInOutput.getType() != Material.AIR) {
            return;
        }

        final ItemStack[] inputs = new ItemStack[RECIPE_SLOTS.length];
        int i = 0;
        for (int recipeSlot : RECIPE_SLOTS) {
            ItemStack stack = menu.getItemInSlot(recipeSlot);
            inputs[i] = (stack == null ? null : stack.clone());
            i++;
        }

        // build effectiveInputs (unwrap quantum items)
        final ItemStack[] effectiveInputs = new ItemStack[inputs.length];
        for (int idx = 0; idx < inputs.length; idx++) {
            ItemStack s = inputs[idx];
            if (s == null) {
                effectiveInputs[idx] = null;
                continue;
            }
            boolean assigned = false;
            try {
                SlimefunItem sf = SlimefunItem.getByItem(s);
                if (sf instanceof NetworkQuantumStorage) {
                    try {
                        ItemMeta im = s.getItemMeta();
                        QuantumCache qc = DataTypeMethods.getCustom(im, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE);
                        if (qc != null && qc.getItemStack() != null && qc.getAmount() > 0) {
                            ItemStack base = qc.getItemStack().clone();
                            base.setAmount(1);
                            effectiveInputs[idx] = base;
                            assigned = true;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            if (!assigned) {
                effectiveInputs[idx] = s.clone();
                effectiveInputs[idx].setAmount(1);
            }
        }

        ItemStack crafted = null;

        recipeLoop:
        for (Map.Entry<ItemStack[], ItemStack> entry : RECIPES.entrySet()) {
            ItemStack[] recipeKey = entry.getKey();
            if (recipeKey.length != effectiveInputs.length) continue;

            boolean ok = true;
            for (int t = 0; t < recipeKey.length; t++) {
                ItemStack need = recipeKey[t];
                ItemStack have = effectiveInputs[t];

                if (!SlimefunUtils.isItemSimilar(have, need, true, false, false)) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                crafted = entry.getValue().clone();
                break recipeLoop;
            }
        }

        if (crafted != null) {
            // ensure crafted result not already present (avoid double-craft)
            boolean alreadyExists = false;
            final int MENU_SIZE = 45; // typical BlockMenu size for this preset
            for (int si = 0; si < MENU_SIZE; si++) {
                try {
                    ItemStack it = menu.getItemInSlot(si);
                    if (it != null && it.getType() != Material.AIR) {
                        if (SlimefunUtils.isItemSimilar(it, crafted, true, false, false)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (alreadyExists) return;

            // transfer quantum cache if core item is quantum
            final ItemStack coreItem = inputs[4]; // center slot
            final SlimefunItem oldQuantum = coreItem == null ? null : SlimefunItem.getByItem(coreItem);

            if (oldQuantum instanceof NetworkQuantumStorage) {
                try {
                    final ItemMeta oldMeta = coreItem.getItemMeta();
                    final ItemMeta newMeta = crafted.getItemMeta();
                    final NetworkQuantumStorage newQuantum = (NetworkQuantumStorage) SlimefunItem.getByItem(crafted);
                    final QuantumCache oldCache = DataTypeMethods.getCustom(oldMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE);

                    if (oldCache != null) {
                        final QuantumCache newCache = new QuantumCache(
                                oldCache.getItemStack().clone(),
                                oldCache.getAmount(),
                                newQuantum.getMaxAmount(),
                                oldCache.isVoidExcess(),
                                newQuantum.supportsCustomMaxAmount()
                        );
                        DataTypeMethods.setCustom(newMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, newCache);
                        newCache.addMetaLore(newMeta);
                        crafted.setItemMeta(newMeta);
                    }
                } catch (Throwable ignored) {}
            }

            // place crafted into output deterministically
            ItemStack outBefore = menu.getItemInSlot(OUTPUT_SLOT);
            if (outBefore == null || outBefore.getType() == Material.AIR) {
                menu.replaceExistingItem(OUTPUT_SLOT, crafted.clone());
            } else {
                return; // someone filled it in the meantime
            }

            // re-check placed
            ItemStack outNow = menu.getItemInSlot(OUTPUT_SLOT);
            if (outNow == null || outNow.getType() == Material.AIR || !SlimefunUtils.isItemSimilar(outNow, crafted, true, false, false)) {
                return; // failed to place
            }

            // consume ingredients AFTER successful placement
            for (int slotIndex = 0; slotIndex < RECIPE_SLOTS.length; slotIndex++) {
                int recipeSlot = RECIPE_SLOTS[slotIndex];
                ItemStack slotItem = menu.getItemInSlot(recipeSlot);

                if (slotItem == null || slotItem.getType() == Material.AIR) continue;

                boolean consumed = false;

                // If Quantum item, reduce its cached amount
                try {
                    SlimefunItem sf = SlimefunItem.getByItem(slotItem);
                    if (sf instanceof NetworkQuantumStorage) {
                        ItemMeta im = slotItem.getItemMeta();
                        QuantumCache qc = DataTypeMethods.getCustom(im, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE);
                        if (qc != null && qc.getItemStack() != null) {
                            long before = qc.getAmount();
                            if (before > 0) {
                                qc.reduceAmount(1);
                                DataTypeMethods.setCustom(im, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, qc);
                                qc.addMetaLore(im);
                                slotItem.setItemMeta(im);

                                if (qc.getAmount() <= 0) {
                                    menu.replaceExistingItem(recipeSlot, null);
                                } else {
                                    menu.replaceExistingItem(recipeSlot, slotItem);
                                }
                                consumed = true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                if (!consumed) {
                    menu.consumeItem(recipeSlot, 1, true);
                }
            }
        }
    }

    private BlockBreakHandler getBlockBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent event, ItemStack itemStack, List<ItemStack> drops) {
                BlockMenu menu = BlockStorage.getInventory(event.getBlock());
                if (menu != null) {
                    menu.dropItems(menu.getLocation(), RECIPE_SLOTS);
                    menu.dropItems(menu.getLocation(), OUTPUT_SLOT);
                }
            }
        };
    }
}
