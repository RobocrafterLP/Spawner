package com.RobocrafterLP.spawner;

import com.RobocrafterLP.spawner.rune.RuneService;
import com.RobocrafterLP.spawner.spawner.SpawnerListener;
import com.RobocrafterLP.spawner.spawner.SpawnerMenu;
import org.bukkit.plugin.java.JavaPlugin;

public final class Spawner extends JavaPlugin {
    @Override public void onEnable() {
        saveDefaultConfig();
        RuneService runes = new RuneService(this);
        runes.normalizeDisabledUpgradesInLoadedChunks();
        SpawnerMenu menu = new SpawnerMenu(this, runes);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(this, runes, menu), this);
        getCommand("rune").setExecutor(runes);
        getCommand("rune").setTabCompleter(runes);
        getCommand("spawner").setExecutor(menu);
        getLogger().info("Spawner runes enabled.");
    }
}
