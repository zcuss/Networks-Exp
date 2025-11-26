package io.github.sefiraat.networks.slimefun.network;

import dev.sefiraat.sefilib.misc.ParticleUtils;
import dev.sefiraat.sefilib.world.LocationUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.ItemCreator;
import io.github.sefiraat.networks.utils.Theme;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * NetworkControlX optimized to reduce per-tick allocations and expensive calls.
 */
public class NetworkControlX extends NetworkDirectional {

    public static final ItemStack TEMPLATE_BACKGROUND_STACK = ItemCreator.create(
            Material.BLUE_STAINED_GLASS_PANE,
            Theme.PASSIVE + "Cut items matching template.",
            Theme.PASSIVE + "Leaving blank will cut anything"
    );
    private static final int[] BACKGROUND_SLOTS = new int[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 15, 17, 18, 20, 22, 23, 24, 26, 27, 28, 30, 31, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] TEMPLATE_BACKGROUND = new int[]{16};
    private static final int TEMPLATE_SLOT = 25;
    private static final int NORTH_SLOT = 11;
    private static final int SOUTH_SLOT = 29;
    private static final int EAST_SLOT = 21;
    private static final int WEST_SLOT = 19;
    private static final int UP_SLOT = 14;
    private static final int DOWN_SLOT = 32;
    private static final int REQUIRED_POWER = 100;

    // particles: reduced count for less server overhead
    private static final Particle.DustOptions DUST_OPTIONS = new Particle.DustOptions(Color.GRAY, 1f);
    private final Set<BlockPosition> blockCache = new HashSet<>();

    // Common set of blocked container-like materials to skip fast
    private static final Set<Material> BLOCKED_CONTAINERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.ENDER_CHEST,
            Material.BARREL,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.HOPPER,
            Material.DROPPER,
            Material.DISPENSER,
            Material.BREWING_STAND
    )));

    public NetworkControlX(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.CUTTER);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @Nonnull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            tryBreakBlock(blockMenu);
        }
    }

    @Override
    protected void onUniqueTick() {
        this.blockCache.clear();
    }

    private void tryBreakBlock(@Nonnull BlockMenu blockMenu) {
        // minimal early-exit checks (avoid object creation if not needed)
        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());
        if (definition == null || definition.getNode() == null) return;

        // minimal power check
        if (definition.getNode().getRoot().getRootPower() < REQUIRED_POWER) return;

        final BlockFace direction = getCurrentDirection(blockMenu);
        if (direction == BlockFace.SELF) return;

        final Block targetBlock = blockMenu.getBlock().getRelative(direction);
        final BlockPosition targetPosition = new BlockPosition(targetBlock);
        if (this.blockCache.contains(targetPosition)) return;

        final Material material = targetBlock.getType();

        // cheap rejects
        if (material.isAir() || material.getHardness() < 0) return;

        // skip Slimefun blocks quickly
        final SlimefunItem slimefunItem = BlockStorage.check(targetBlock);
        if (slimefunItem != null) return;

        // template handling
        final ItemStack templateStack = blockMenu.getItemInSlot(TEMPLATE_SLOT);
        boolean hasTemplate = templateStack != null && !templateStack.getType().isAir();
        if (hasTemplate) {
            Material tplMat = templateStack.getType();
            // If template is a Slimefun item or not same material, skip
            if (tplMat != material || SlimefunItem.getByItem(templateStack) != null) {
                return;
            }
        }

        // permission check: only when needed do a parse/lookup
        final String ownerString = BlockStorage.getLocationInfo(blockMenu.getLocation(), OWNER_KEY);
        if (ownerString == null) return;
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerString);
        } catch (IllegalArgumentException ex) {
            return;
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        if (!Slimefun.getProtectionManager().hasPermission(offlinePlayer, targetBlock, Interaction.BREAK_BLOCK)) {
            return;
        }

        // Blocked materials set fast-check, plus fallback to name checks for shulker-like names
        if (BLOCKED_CONTAINERS.contains(material)) return;
        String matName = material.name();
        if (matName.endsWith("_SHULKER_BOX") || matName.endsWith("_SHELF") || matName.equals("SHULKER_BOX")) return;

        // create a 1-item stack representing the block type to add to network
        final ItemStack resultStack = new ItemStack(material, 1);

        // add to network, then if amount becomes 0, physically remove block on main thread
        definition.getNode().getRoot().addItemStack0(blockMenu.getBlock().getLocation(), resultStack);

        if (resultStack.getAmount() == 0) {
            // mark as processed and schedule the block removal + particle + power removal on main thread
            this.blockCache.add(targetPosition);

            Bukkit.getScheduler().runTask(Networks.getInstance(), () -> {
                try {
                    // remove block without physics
                    targetBlock.setType(Material.AIR, false);

                    // visual feedback (cheaper parameters)
                    ParticleUtils.displayParticleRandomly(
                            LocationUtils.centre(targetBlock.getLocation()),
                            1, // count
                            3, // spread/attempts
                            DUST_OPTIONS
                    );

                    // consume power from network
                    definition.getNode().getRoot().removeRootPower(REQUIRED_POWER);
                } catch (Exception ex) {
                    Networks.getInstance().getLogger().warning("Failed to cut/paste block via Bukkit API: " + ex.getMessage());
                }
            });
        }
    }

    @Nonnull
    @Override
    protected int[] getBackgroundSlots() {
        return BACKGROUND_SLOTS;
    }

    @Nullable
    @Override
    protected int[] getOtherBackgroundSlots() {
        return TEMPLATE_BACKGROUND;
    }

    @Nullable
    @Override
    protected ItemStack getOtherBackgroundStack() {
        return TEMPLATE_BACKGROUND_STACK;
    }

    @Override
    public int getNorthSlot() { return NORTH_SLOT; }
    @Override
    public int getSouthSlot() { return SOUTH_SLOT; }
    @Override
    public int getEastSlot() { return EAST_SLOT; }
    @Override
    public int getWestSlot() { return WEST_SLOT; }
    @Override
    public int getUpSlot() { return UP_SLOT; }
    @Override
    public int getDownSlot() { return DOWN_SLOT; }
    @Override
    public int[] getItemSlots() { return new int[]{TEMPLATE_SLOT}; }
    @Override
    protected Particle.DustOptions getDustOptions() { return DUST_OPTIONS; }
}
