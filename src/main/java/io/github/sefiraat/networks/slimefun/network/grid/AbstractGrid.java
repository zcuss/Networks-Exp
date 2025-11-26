package io.github.sefiraat.networks.slimefun.network.grid;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.GridItemRequest;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;

public abstract class AbstractGrid extends NetworkObject {

    private static final ItemStack BLANK_SLOT_STACK = ItemCreator.create(
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            " "
    );

    private static final ItemStack PAGE_PREVIOUS_STACK = ItemCreator.create(
            Material.RED_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Previous Page"
    );

    private static final ItemStack PAGE_NEXT_STACK = ItemCreator.create(
            Material.RED_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Next Page"
    );

    private static final ItemStack CHANGE_SORT_STACK = ItemCreator.create(
            Material.BLUE_STAINED_GLASS_PANE,
            Theme.CLICK_INFO.getColor() + "Change Sort Order"
    );

    private static final ItemStack FILTER_STACK = ItemCreator.create(
            Material.NAME_TAG,
            Theme.CLICK_INFO.getColor() + "Set Filter (Right Click to Clear)"
    );

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Comparator<Map.Entry<ItemStack, Integer>> ALPHABETICAL_SORT = Comparator.comparing(
            itemStackIntegerEntry -> {
                ItemStack itemStack = itemStackIntegerEntry.getKey();
                SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
                if (slimefunItem != null) {
                    // Slimefun item name is usually a String; try to convert safely via Adventure if possible
                    try {
                        // Slimefun API returns plain String name; strip color codes if present using legacy serializer
                        return PLAIN.serialize(LEGACY.deserialize(slimefunItem.getItemName()));
                    } catch (Throwable ignored) {
                        return slimefunItem.getItemName();
                    }
                } else {
                    ItemMeta itemMeta = itemStackIntegerEntry.getKey().getItemMeta();
                    if (itemMeta != null && hasDisplayNameSafe(itemMeta)) {
                        String display = getPlainDisplayNameSafe(itemMeta);
                        return display != null ? display : itemStackIntegerEntry.getKey().getType().name();
                    } else {
                        return itemStackIntegerEntry.getKey().getType().name();
                    }
                }
            }
    );

    private static final Comparator<Map.Entry<ItemStack, Integer>> NUMERICAL_SORT = Map.Entry.comparingByValue();

    private final ItemSetting<Integer> tickRate;

    protected AbstractGrid(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.GRID);

        this.getSlotsToDrop().add(getInputSlot());

        this.tickRate = new IntRangeSetting(this, "tick_rate", 1, 1, 10);
        addItemSetting(this.tickRate);

        addItemHandler(
                new BlockTicker() {

                    private int tick = 1;

                    @Override
                    public boolean isSynchronized() {
                        return false;
                    }

                    @Override
                    public void tick(Block block, SlimefunItem item, Config data) {
                        if (tick <= 1) {
                            final BlockMenu blockMenu = BlockStorage.getInventory(block);
                            addToRegistry(block);
                            tryAddItem(blockMenu);
                            updateDisplay(blockMenu);
                        }
                    }

                    @Override
                    public void uniqueTick() {
                        tick = tick <= 1 ? tickRate.getValue() : tick - 1;
                    }
                }
        );
    }

    @Nonnull
    private static List<String> getLoreAddition(int amount) {
        final MessageFormat format = new MessageFormat("{0}Amount: {1}{2}", Locale.ROOT);
        return List.of(
                "",
                format.format(new Object[]{Theme.CLICK_INFO.getColor(), Theme.PASSIVE.getColor(), amount}, new StringBuffer(), null).toString()
        );
    }

    protected void tryAddItem(@Nonnull BlockMenu blockMenu) {
        final ItemStack itemStack = blockMenu.getItemInSlot(getInputSlot());

        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());
        if (definition.getNode() == null) {
            return;
        }

        definition.getNode().getRoot().addItemStack0(blockMenu.getLocation(), itemStack);
    }

    protected void updateDisplay(@Nonnull BlockMenu blockMenu) {
        // No viewer - lets not bother updating
        if (!blockMenu.hasViewer()) {
            return;
        }

        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());

        // No node located, weird
        if (definition == null || definition.getNode() == null) {
            clearDisplay(blockMenu);
            return;
        }

        // Update Screen
        final NetworkRoot root = definition.getNode().getRoot();
        final GridCache gridCache = getCacheMap().get(blockMenu.getLocation().clone());
        final List<Map.Entry<ItemStack, Integer>> entries = getEntries(root, gridCache);
        final int pages = (int) Math.ceil(entries.size() / (double) getDisplaySlots().length) - 1;

        gridCache.setMaxPages(pages);

        // Set everything to blank and return if there are no pages (no items)
        if (pages < 0) {
            clearDisplay(blockMenu);
            return;
        }

        // Reset selected page if it no longer exists due to items being removed
        if (gridCache.getPage() > pages) {
            gridCache.setPage(0);
        }

        final int start = gridCache.getPage() * getDisplaySlots().length;
        final int end = Math.min(start + getDisplaySlots().length, entries.size());
        final List<Map.Entry<ItemStack, Integer>> validEntries = entries.subList(start, end);

        getCacheMap().put(blockMenu.getLocation(), gridCache);

        for (int i = 0; i < getDisplaySlots().length; i++) {
            if (validEntries.size() > i) {
                final Map.Entry<ItemStack, Integer> entry = validEntries.get(i);
                final ItemStack displayStack = entry.getKey().clone();
                final ItemMeta itemMeta = displayStack.getItemMeta();

                // Obtain existing lore safely (component-first, fallback reflection)
                List<String> legacyLore = null;
                try {
                    List<Component> comps = itemMeta.lore();
                    if (comps != null) {
                        legacyLore = new ArrayList<>(comps.size());
                        for (Component c : comps) legacyLore.add(LEGACY.serialize(c));
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) { /* fallback below */ }

                if (legacyLore == null) {
                    List<String> maybe = invokeGetLore(itemMeta);
                    legacyLore = maybe != null ? new ArrayList<>(maybe) : new ArrayList<>();
                }

                if (legacyLore.isEmpty()) {
                    legacyLore = getLoreAddition(entry.getValue());
                } else {
                    legacyLore.addAll(getLoreAddition(entry.getValue()));
                }

                // write back: prefer Component-based set, else reflective setLore
                try {
                    // convert legacy strings to Components and set via new API
                    List<Component> comps = new ArrayList<>(legacyLore.size());
                    for (String s : legacyLore) comps.add(LEGACY.deserialize(s));
                    itemMeta.lore(comps);
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    // fallback: reflective setLore
                    invokeSetLore(itemMeta, legacyLore);
                }

                displayStack.setItemMeta(itemMeta);
                blockMenu.replaceExistingItem(getDisplaySlots()[i], displayStack);
                blockMenu.addMenuClickHandler(getDisplaySlots()[i], (player, slot, item, action) -> {
                    retrieveItem(player, definition, item, action, blockMenu);
                    return false;
                });
            } else {
                blockMenu.replaceExistingItem(getDisplaySlots()[i], BLANK_SLOT_STACK);
                blockMenu.addMenuClickHandler(getDisplaySlots()[i], (p, slot, item, action) -> false);
            }
        }
    }

    protected void clearDisplay(BlockMenu blockMenu) {
        for (int displaySlot : getDisplaySlots()) {
            blockMenu.replaceExistingItem(displaySlot, BLANK_SLOT_STACK);
            blockMenu.addMenuClickHandler(displaySlot, (p, slot, item, action) -> false);
        }
    }

    @Nonnull
    protected List<Map.Entry<ItemStack, Integer>> getEntries(@Nonnull NetworkRoot networkRoot, @Nonnull GridCache cache) {
        return networkRoot.getAllNetworkItems().entrySet().stream()
                .filter(entry -> {
                    if (cache.getFilter() == null) {
                        return true;
                    }

                    final ItemStack itemStack = entry.getKey();
                    String name = itemStack.getType().name().toLowerCase(Locale.ROOT);
                    if (itemStack.hasItemMeta()) {
                        final ItemMeta itemMeta = itemStack.getItemMeta();
                        if (itemMeta != null && hasDisplayNameSafe(itemMeta)) {
                            String plain = getPlainDisplayNameSafe(itemMeta);
                            if (plain != null) name = plain.toLowerCase(Locale.ROOT);
                        }
                    }
                    return name.contains(cache.getFilter());
                })
                .sorted(cache.getSortOrder() == GridCache.SortOrder.ALPHABETICAL ? ALPHABETICAL_SORT : NUMERICAL_SORT.reversed())
                .toList();
    }

    protected boolean setFilter(@Nonnull Player player, @Nonnull BlockMenu blockMenu, @Nonnull GridCache gridCache, @Nonnull ClickAction action) {
        if (action.isRightClicked()) {
            gridCache.setFilter(null);
        } else {
            player.closeInventory();
            player.sendMessage(Theme.WARNING + "Type what you would like to filter this grid to");
            ChatUtils.awaitInput(player, s -> {
                if (s.isBlank()) {
                    return;
                }
                gridCache.setFilter(s.toLowerCase(Locale.ROOT));
                player.sendMessage(Theme.SUCCESS + "Filter applied");
            });
        }
        return false;
    }

    @ParametersAreNonnullByDefault
    protected void retrieveItem(Player player, NodeDefinition definition, @Nullable ItemStack itemStack, ClickAction action, BlockMenu blockMenu) {
        // Todo Item can be null here. No idea how - investigate later
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        final ItemStack clone = itemStack.clone();
        final ItemMeta cloneMeta = clone.getItemMeta();

        // get lore safely (component-first -> reflective)
        List<String> cloneLore = null;
        try {
            List<Component> comps = cloneMeta.lore();
            if (comps != null) {
                cloneLore = new ArrayList<>(comps.size());
                for (Component c : comps) cloneLore.add(LEGACY.serialize(c));
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) { /* fallback */ }

        if (cloneLore == null) {
            List<String> maybe = invokeGetLore(cloneMeta);
            cloneLore = maybe != null ? new ArrayList<>(maybe) : new ArrayList<>();
        }

        // remove last two lore lines (assumes there are at least two; guard)
        if (!cloneLore.isEmpty()) {
            int removeCount = Math.min(2, cloneLore.size());
            for (int i = 0; i < removeCount; i++) {
                cloneLore.remove(cloneLore.size() - 1);
            }
        }

        // write lore back safely
        try {
            List<Component> comps = new ArrayList<>(cloneLore.size());
            for (String s : cloneLore) comps.add(LEGACY.deserialize(s));
            cloneMeta.lore(comps);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            invokeSetLore(cloneMeta, cloneLore);
        }

        clone.setItemMeta(cloneMeta);
        int amount = 1;

        if (action.isRightClicked()) {
            amount = clone.getMaxStackSize();
        }

        final GridItemRequest request = new GridItemRequest(clone, amount, player);

        if (action.isShiftClicked()) {
            addToInventory(player, definition, request, blockMenu);
        } else {
            addToCursor(player, definition, request, action, blockMenu);
        }

        updateDisplay(blockMenu);
    }

    @ParametersAreNonnullByDefault
    private void addToInventory(Player player, NodeDefinition definition, GridItemRequest request, BlockMenu menu) {
        ItemStack requestingStack = definition.getNode().getRoot().getItemStack0(menu.getLocation(), request);

        if (requestingStack == null) {
            return;
        }

        HashMap<Integer, ItemStack> remnant = player.getInventory().addItem(requestingStack);
        requestingStack = remnant.values().stream().findFirst().orElse(null);
        if (requestingStack != null) {
            definition.getNode().getRoot().addItemStack0(player.getLocation(), requestingStack);
        }
    }

    @ParametersAreNonnullByDefault
    private void addToCursor(Player player, NodeDefinition definition, GridItemRequest request, ClickAction action, BlockMenu menu) {
        final ItemStack cursor = player.getItemOnCursor();

        // Quickly check if the cursor has an item and if we can add more to it
        if (cursor.getType() != Material.AIR && !canAddMore(action, cursor, request)) {
            return;
        }

        ItemStack requestingStack = definition.getNode().getRoot().getItemStack0(menu.getLocation(), request);
        setCursor(player, cursor, requestingStack);
    }

    private void setCursor(Player player, ItemStack cursor, ItemStack requestingStack) {
        if (requestingStack != null) {
            if (cursor.getType() != Material.AIR) {
                requestingStack.setAmount(cursor.getAmount() + 1);
            }
            player.setItemOnCursor(requestingStack);
        }
    }

    private boolean canAddMore(@Nonnull ClickAction action, @Nonnull ItemStack cursor, @Nonnull GridItemRequest request) {
        return !action.isRightClicked()
                && request.getAmount() == 1
                && cursor.getAmount() < cursor.getMaxStackSize()
                && StackUtils.itemsMatch(request, cursor, true);
    }

    @Override
    public void postRegister() {
        getPreset();
    }

    @Nonnull
    protected abstract BlockMenuPreset getPreset();

    @Nonnull
    protected abstract Map<Location, GridCache> getCacheMap();

    protected abstract int[] getBackgroundSlots();

    protected abstract int[] getDisplaySlots();

    protected abstract int getInputSlot();

    protected abstract int getChangeSort();

    protected abstract int getPagePrevious();

    protected abstract int getPageNext();

    protected abstract int getFilterSlot();

    protected ItemStack getBlankSlotStack() {
        return BLANK_SLOT_STACK;
    }

    protected ItemStack getPagePreviousStack() {
        return PAGE_PREVIOUS_STACK;
    }

    protected ItemStack getPageNextStack() {
        return PAGE_NEXT_STACK;
    }

    protected ItemStack getChangeSortStack() {
        return CHANGE_SORT_STACK;
    }

    protected ItemStack getFilterStack() {
        return FILTER_STACK;
    }

    public void receiveItem(
            Player player, ItemStack itemStack, ClickAction action, BlockMenu blockMenu) {
        NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());
        receiveItem(definition.getNode().getRoot(), player, itemStack, action, blockMenu);
    }

    // @SuppressWarnings({"unused"})
    public void receiveItem(
            NetworkRoot root,
            Player player,
            @Nullable ItemStack itemStack,
            ClickAction action,
            BlockMenu blockMenu) {
        NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());
        if (definition == null || definition.getNode() == null) {
            clearDisplay(blockMenu);
            blockMenu.close();
            return;
        }

        if (itemStack != null && itemStack.getType() != Material.AIR) {
            root.addItemStack0(blockMenu.getLocation(), itemStack);
        }
    }

    /* ---------------- reflection helpers (local to this file) ---------------- */

    @Nullable
    private static List<String> invokeGetLore(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("getLore");
            Object res = m.invoke(meta);
            if (res instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) res;
                return list;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private static void invokeSetLore(ItemMeta meta, List<String> lore) {
        try {
            Method m = meta.getClass().getMethod("setLore", List.class);
            m.invoke(meta, lore);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        } catch (SecurityException ignored) {
        }
    }

    @Nullable
    private static String invokeGetDisplayName(ItemMeta meta) {
        try {
            Method m = meta.getClass().getMethod("getDisplayName");
            Object res = m.invoke(meta);
            if (res instanceof String) {
                return (String) res;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        } catch (SecurityException ignored) {
        }
        return null;
    }

    @Nullable
    private static String getPlainDisplayNameSafe(ItemMeta meta) {
        try {
            Component comp = meta.displayName();
            if (comp != null) {
                return PLAIN.serialize(comp);
            }
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // fallback to reflective legacy
        }

        String legacy = invokeGetDisplayName(meta);
        if (legacy != null) {
            try {
                return PLAIN.serialize(LEGACY.deserialize(legacy));
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean hasDisplayNameSafe(ItemMeta meta) {
        try {
            return meta.hasDisplayName();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            String legacy = invokeGetDisplayName(meta);
            return legacy != null && !legacy.isEmpty();
        }
    }
}
