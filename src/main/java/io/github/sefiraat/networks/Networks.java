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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    private final String branch;

    private ListenerManager listenerManager;
    private SupportedPluginManager supportedPluginManager;

    public Networks() {
        this.username = "Sefiraat";
        this.repo = "Networks";
        this.branch = "master";
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
        getLogger().info("     Changed by mmmjjkx and balugaq     ");
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

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Location c : controllersSet) {
                if (BlockStorage.check(c) instanceof NetworkController) {
                    CraftBlock cb = ((CraftBlock) c.getBlock());
                    CraftWorld cw = ((CraftWorld) c.getWorld());

                    ServerLevel level = cw.getHandle();
                    LevelLightEngine ll = level.chunkSource.getLightEngine();

                    BlockStorage.clearBlockInfo(c);
                    NetworkController.wipeNetwork(c);

                    Bukkit.getScheduler().runTask(this, () -> {
                        cw.dropItemNaturally(cb.getLocation(), Converter.getItem(NetworksSlimefunItemStacks.NETWORK_CONTROLLER));

                        level.setBlock(cb.getPosition(), Blocks.AIR.defaultBlockState(), 0);
                        level.getMinecraftWorld().sendBlockUpdated(cb.getPosition(), cb.getNMS(), Blocks.AIR.defaultBlockState(), 3);
                        ll.checkBlock(cb.getPosition());
                    });
                }
            }

            controllersSet.clear();
        }, 5, 10);

        setupMetrics();
    }

    public void tryUpdate() {
        if (getConfig().getBoolean("auto-update") && getDescription().getVersion().startsWith("Dev")) {
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
