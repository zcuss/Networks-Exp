package io.github.sefiraat.networks;

import com.balugaq.netex.utils.Converter;
import io.github.sefiraat.networks.commands.NetworksMain;
import io.github.sefiraat.networks.integrations.HudCallbacks;
import io.github.sefiraat.networks.integrations.NetheoPlants;
import io.github.sefiraat.networks.managers.ListenerManager;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.slimefun.NetworksSlimefunItemStacks;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.utils.NetworkUtils;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.updater.BlobBuildUpdater;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Networks extends JavaPlugin implements SlimefunAddon {
    @Getter
    private static final Set<Location> controllersSet = new HashSet<>();

    private static Networks instance;

    private final String username;
    private final String repo;

    private ListenerManager listenerManager;
    private SupportedPluginManager supportedPluginManager;

    public Networks() {
        this.username = "Sefiraat";
        this.repo = "Networks";
    }

    @Nonnull
    public static PluginManager getPluginManager() {
        return Networks.getInstance().getServer().getPluginManager();
    }

    public static Networks getInstance() {
        return Networks.instance;
    }

    public static SupportedPluginManager getSupportedPluginManager() {
        return Networks.getInstance().supportedPluginManager;
    }

    public static ListenerManager getListenerManager() {
        return Networks.getInstance().listenerManager;
    }

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("########################################");
        getLogger().info("         Networks - By Sefiraat         ");
        getLogger().info("           Changed by mmmjjkx           ");
        getLogger().info("########################################");

        saveDefaultConfig();
        tryUpdate();

        this.supportedPluginManager = new SupportedPluginManager();

        setupSlimefun();

        this.listenerManager = new ListenerManager();
        this.getCommand("networks").setExecutor(new NetworksMain());

        // Fix dupe bug which break the network controller data without player interaction
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    Set<Location> wrongs = new HashSet<>();
                    Set<Location> controllers = new HashSet<>(
                            NetworkController.getNetworks().keySet());
                    for (Location controller : controllers) {
                        if (!(BlockStorage.check(controller) instanceof NetworkController)) {
                            wrongs.add(controller);
                        }
                    }

                    for (Location wrong : wrongs) {
                        NetworkUtils.clearNetwork(wrong);
                    }
                },
                5, Slimefun.getTickerTask().getTickRate()
        );

        // replaced CraftBlock/CraftWorld usage with reflection + Bukkit fallback
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Location c : controllersSet) {
                if (BlockStorage.check(c) instanceof NetworkController) {
                    final Block block = c.getBlock();
                    final World world = c.getWorld();

                    // Attempt to use NMS via reflection; fallback to Bukkit API if any step fails.
                    try {
                        // drop item naturally using Bukkit world (safe)
                        Bukkit.getScheduler().runTask(this, () ->
                                world.dropItemNaturally(block.getLocation(), Converter.getItem(NetworksSlimefunItemStacks.NETWORK_CONTROLLER))
                        );

                        // Try to call getHandle() on world to obtain NMS world (ServerLevel)
                        Object nmsWorld = null;
                        try {
                            Method getHandle = world.getClass().getMethod("getHandle");
                            nmsWorld = getHandle.invoke(world);
                        } catch (NoSuchMethodException ignored) {
                            // Some implementations may not expose getHandle; we'll fallback below
                        }

                        if (nmsWorld != null) {
                            // Obtain BlockPos and current NMS BlockState from the craft-block object (via reflection)
                            Object craftBlock = block; // runtime object; methods may exist on implementation class
                            Object blockPos = null;
                            Object nmsOldState = null;

                            // attempt getPosition()
                            try {
                                Method getPosition = craftBlock.getClass().getMethod("getPosition");
                                blockPos = getPosition.invoke(craftBlock);
                            } catch (NoSuchMethodException ignored) {
                                // older/newer impl might use different method names; try "getBlockPosition"
                                try {
                                    Method getBlockPos = craftBlock.getClass().getMethod("getBlockPosition");
                                    blockPos = getBlockPos.invoke(craftBlock);
                                } catch (NoSuchMethodException ignored2) {
                                    // if still not found, leave blockPos null and fallback later
                                }
                            }

                            // attempt getNMS() to get BlockState
                            try {
                                Method getNMS = craftBlock.getClass().getMethod("getNMS");
                                nmsOldState = getNMS.invoke(craftBlock);
                            } catch (NoSuchMethodException ignored) {
                                // try alternative "getTileEntity" or "getBlockData" if present - but likely not same type
                                // we accept nmsOldState possibly null
                            }

                            // Prepare AIR defaultBlockState via reflection
                            Object airState = null;
                            try {
                                Class<?> blocksClass = Class.forName("net.minecraft.world.level.block.Blocks");
                                Field airField = blocksClass.getField("AIR");
                                Object airBlock = airField.get(null);
                                Method defaultBlockState = airBlock.getClass().getMethod("defaultBlockState");
                                airState = defaultBlockState.invoke(airBlock);
                            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
                                // can't obtain AIR default state, fallback later
                            }

                            // Now attempt to set block to air and send update + light check
                            try {
                                if (blockPos != null && airState != null) {
                                    // setBlock(BlockPos, BlockState, int)
                                    try {
                                        Method setBlockMethod = nmsWorld.getClass().getMethod("setBlock", blockPos.getClass(), airState.getClass(), int.class);
                                        setBlockMethod.invoke(nmsWorld, blockPos, airState, 0);
                                    } catch (NoSuchMethodException e) {
                                        // sometimes method signature differs; try searching methods reflectively
                                        for (Method m : nmsWorld.getClass().getMethods()) {
                                            if (m.getName().equals("setBlock") && m.getParameterCount() == 3) {
                                                m.invoke(nmsWorld, blockPos, airState, 0);
                                                break;
                                            }
                                        }
                                    }

                                    // sendBlockUpdated via mc world if available
                                    try {
                                        Method getMinecraftWorld = nmsWorld.getClass().getMethod("getMinecraftWorld");
                                        Object mcWorld = getMinecraftWorld.invoke(nmsWorld);
                                        if (mcWorld != null && nmsOldState != null) {
                                            Method sendBlockUpdated = null;
                                            for (Method m : mcWorld.getClass().getMethods()) {
                                                if (m.getName().equals("sendBlockUpdated") && m.getParameterCount() == 4) {
                                                    sendBlockUpdated = m;
                                                    break;
                                                }
                                            }
                                            if (sendBlockUpdated != null) {
                                                sendBlockUpdated.invoke(mcWorld, blockPos, nmsOldState, airState, 3);
                                            }
                                        }
                                    } catch (NoSuchMethodException ignored) {
                                    }

                                    // light engine: try chunkSource.getLightEngine().checkBlock(BlockPos) or level.getLightEngine().checkBlock(...)
                                    try {
                                        // try level.chunkSource.getLightEngine()
                                        Field chunkSourceField = nmsWorld.getClass().getField("chunkSource");
                                        Object chunkSource = chunkSourceField.get(nmsWorld);
                                        Method getLightEngine = null;
                                        for (Method m : chunkSource.getClass().getMethods()) {
                                            if (m.getName().toLowerCase().contains("light") || m.getName().toLowerCase().contains("getlight")) {
                                                if (m.getParameterCount() == 0) {
                                                    getLightEngine = m;
                                                    break;
                                                }
                                            }
                                        }
                                        if (getLightEngine != null) {
                                            Object lightEngine = getLightEngine.invoke(chunkSource);
                                            if (lightEngine != null) {
                                                Method checkBlock = null;
                                                for (Method m : lightEngine.getClass().getMethods()) {
                                                    if (m.getName().equals("checkBlock") && m.getParameterCount() == 1) {
                                                        checkBlock = m;
                                                        break;
                                                    }
                                                }
                                                if (checkBlock != null) {
                                                    checkBlock.invoke(lightEngine, blockPos);
                                                }
                                            }
                                        }
                                    } catch (NoSuchFieldException ignored) {
                                        // fallback: ignore
                                    }
                                } else {
                                    // fallback: use Bukkit API to set block to air
                                    Bukkit.getScheduler().runTask(this, () -> block.setType(org.bukkit.Material.AIR, false));
                                }
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                // if any reflection invocation fails, fallback to Bukkit API
                                getLogger().warning("NMS block update failed: " + e.getMessage());
                                Bukkit.getScheduler().runTask(this, () -> block.setType(org.bukkit.Material.AIR, false));
                            }
                        } else {
                            // no NMS available: fallback to Bukkit API to remove block
                            Bukkit.getScheduler().runTask(this, () -> block.setType(org.bukkit.Material.AIR, false));
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        getLogger().warning("Reflection error while updating block: " + e.getMessage());
                        Bukkit.getScheduler().runTask(this, () -> block.setType(org.bukkit.Material.AIR, false));
                    } catch (Exception ex) {
                        getLogger().warning("Unexpected error while updating block: " + ex.getMessage());
                        Bukkit.getScheduler().runTask(this, () -> block.setType(org.bukkit.Material.AIR, false));
                    }

                    BlockStorage.clearBlockInfo(c);
                    NetworkUtils.clearNetwork(c);
                }
            }

            controllersSet.clear();
        }, 5, 10);

        setupMetrics();
    }

    public void tryUpdate() {
        if (getConfig().getBoolean("auto-update") && getPluginMeta().getVersion().startsWith("Dev")) {
            new BlobBuildUpdater(this, getFile(), "Networks", "Dev").start();
        }
    }

    public void setupSlimefun() {
        NetworkSlimefunItems.setup();
        if (supportedPluginManager.isNetheopoiesis()) {
            try {
                NetheoPlants.setup();
            } catch (NoClassDefFoundError e) {
                getLogger().severe("Netheopoiesis must be updated to meet Networks' requirements.");
            }
        }
        if (supportedPluginManager.isSlimeHud()) {
            try {
                HudCallbacks.setup();
            } catch (NoClassDefFoundError e) {
                getLogger().severe("SlimeHUD must be updated to meet Networks' requirements.");
            }
        }
    }

    public void setupMetrics() {
        final Metrics metrics = new Metrics(this, 13644);

        AdvancedPie networksChart = new AdvancedPie("networks", () -> {
            Map<String, Integer> networksMap = new HashMap<>();
            networksMap.put("Number of networks", NetworkController.getNetworks().size());
            return networksMap;
        });

        metrics.addCustomChart(networksChart);
    }

    @Nonnull
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Nullable
    @Override
    public String getBugTrackerURL() {
        return MessageFormat.format("https://github.com/{0}/{1}/issues/", this.username, this.repo);
    }
}
