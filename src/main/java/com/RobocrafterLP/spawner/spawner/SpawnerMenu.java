package com.RobocrafterLP.spawner.spawner;

import com.RobocrafterLP.spawner.Spawner;
import com.RobocrafterLP.spawner.rune.Rune;
import com.RobocrafterLP.spawner.rune.RuneService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpawnerMenu implements Listener, CommandExecutor {
    private final Spawner plugin;
    private final RuneService runes;
    private final NamespacedKey menuRuneKey;
    private final Map<UUID, Location> openSpawnerLocations = new HashMap<>();

    public SpawnerMenu(Spawner plugin, RuneService runes) {
        this.plugin = plugin;
        this.runes = runes;
        this.menuRuneKey = new NamespacedKey(plugin, "menu_rune_id");
    }

    private String title() { return plugin.getConfig().getString("menu.title", "§8Spawner overview"); }

    public void open(Player player, CreatureSpawner spawner) {
        openSpawnerLocations.put(player.getUniqueId(), spawner.getLocation());
        int size = plugin.getConfig().getInt("menu.size", 45);
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;
        Inventory inv = Bukkit.createInventory(null, size, title());
        Material filler = material(plugin.getConfig().getString("menu.filler.material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        String fillerName = plugin.getConfig().getString("menu.filler.name", " ");
        for (int i = 0; i < size; i++) inv.setItem(i, item(filler, fillerName, List.of()));
        for (int row : plugin.getConfig().getIntegerList("menu.spacer-rows")) {
            if (row < 0 || row >= size / 9) continue;
            for (int column = 0; column < 9; column++) inv.setItem(row * 9 + column, null);
        }
        putCategoryHeaders(inv);

        EntityType type = spawner.getSpawnedType();
        String activeUpgrade = activeUpgrade(spawner);
        boolean redstoneRequired = runes.hasEffect(spawner, "require-power");
        boolean noDrops = runes.hasEffect(spawner, "no-item-drops");
        boolean powered = runes.isPowered(spawner);
        int overviewSlot = slot("menu.slots.overview", 4, size);
        if (overviewSlot >= 0) inv.setItem(overviewSlot, item(
                material(plugin.getConfig().getString("menu.overview.material", "SPAWNER"), Material.SPAWNER),
                plugin.getConfig().getString("menu.overview.name", "§6Spawner overview"),
                List.of("§7Mob: §f" + (type == null ? "Unknown" : type.name()),
                        "§7Active upgrade: " + (activeUpgrade == null ? "§8None detected" : "§a" + activeUpgrade),
                        "§7Redstone: " + (redstoneRequired ? (powered ? "§aPowered" : "§cNeeds power") : "§7Not required"),
                        "§7Redstone source: §f" + runes.effectSources(spawner, "require-power"),
                        "§7Spawner mob drops: " + (noDrops ? "§cDisabled" : "§aEnabled"),
                        "§7No-drop source: §f" + runes.effectSources(spawner, "no-item-drops"),
                        "§7Current stats are retained on this spawner.")));

        Rune reclaim = runes.reclaimRune();
        int reclaimSlot = slot("menu.slots.reclaim", 16, size);
        if (reclaim != null && reclaimSlot >= 0) inv.setItem(reclaimSlot, item(reclaim.icon(), "§" + reclaim.color() + reclaim.name(),
                List.of("§7" + reclaim.description(), "§7Required in your inventory to mine.", "§7Preserves mob type, settings and traits.", "", reclaim.enabled() ? "§aEnabled" : "§cDisabled by server owner")));

        for (Rune rune : runes.all()) {
            if (rune.isReclaim() || rune.isGlobal()) continue;
            int fallback = rune.isUpgrade() ? defaultUpgradeSlot(rune.id())
                    : rune.isStacking() ? defaultStackSlot(rune.id()) : defaultTraitSlot(rune.id());
            String group = rune.isUpgrade() ? "upgrades" : rune.isStacking() ? "stackable" : "traits";
            if (fallback < 0) fallback = slot("menu.slots.default-runes." + rune.id(), -1, size);
            int position = slot("menu.slots.runes." + rune.id(), slot("menu.slots." + group + "." + rune.id(), fallback, size), size);
            if (position < 0) continue;
            boolean active = runes.isApplied(spawner, rune.id());
            int stacks = runes.stackLevel(spawner, rune.id());
            List<String> lore = new ArrayList<>(List.of("§7" + rune.description()));
            lore.add("");
            lore.add(rune.enabled() ? "§aEnabled" : "§cDisabled by server owner");
            if (!rune.effects().isEmpty()) {
                lore.add("");
                lore.add("§7Effects:");
                rune.effects().forEach((effect, enabled) -> lore.add("§7" + runes.effectDisplayName(effect) + ": " + (enabled ? "§aEnabled" : "§cDisabled")));
            }
            if (rune.isStacking()) {
                lore.add("");
                lore.add("§7Stacking: §fup to " + rune.maxStacks() + " applications");
                lore.add("§7Applied stacks: §f" + stacks);
                if (rune.extraDropsPerStack() > 0) lore.add("§7Extra drops per stack: §f" + rune.extraDropsPerStack());
                if (rune.extraExperiencePercentPerStack() > 0) lore.add("§7Extra XP per stack: §f" + rune.extraExperiencePercentPerStack() + "%");
            }
            if (rune.isUpgrade()) {
                lore.add("");
                lore.add("§7Upgrade:");
                lore.add("§7Rank: §f" + rune.rank());
                lore.add("§7Min delay: §f" + rune.minDelay() + " ticks");
                lore.add("§7Max delay: §f" + rune.maxDelay() + " ticks");
                lore.add("§7Spawn count: §f" + rune.spawnCount());
                lore.add("§7Max entities: §f" + rune.maxEntities());
                lore.add("§7Activation range: §f" + rune.activationRange());
            }
            if (!rune.details().isEmpty()) {
                lore.add("");
                for (String detail : rune.details()) lore.add("§f" + detail);
            }
            lore.add("");
            if (!rune.enabled()) {
                lore.add("§c✘ DISABLED BY SERVER OWNER");
                if (active) lore.add("§7Installed, but its effects are disabled.");
            } else if (active) {
                lore.add("§a✔ INSTALLED ON THIS SPAWNER");
                if (rune.isStacking()) lore.add("§7Active stacks: §f" + stacks + "/" + rune.maxStacks());
                for (String effect : List.of("require-power", "no-item-drops", "silent", "no-ai", "gravity", "twilight")) {
                    if (Boolean.TRUE.equals(rune.effects().get(effect))
                            || rune.isTrait() && runes.normalizedEffectId(rune.id()).equals(effect)
                            && !rune.effects().containsKey(effect)) {
                        lore.add("§7Active effect: §f" + runes.effectDisplayName(effect));
                    }
                }
                lore.add("§eClick to remove and return the rune");
            } else if (runes.playerHasRune(player, rune.id())) {
                lore.add("§e⬆ READY TO APPLY");
                lore.add("§7Right-click a spawner while holding this rune.");
            } else {
                lore.add("§c✘ NOT INSTALLED");
                lore.add("§7This rune is not affecting this spawner.");
            }
            if (rune.isStacking() && stacks >= rune.maxStacks() && !active) lore.add("§cMaximum stacks reached");
            inv.setItem(position, runeItem(rune, lore, rune.enabled() && active));
        }
        player.openInventory(inv);
    }

    private void putCategoryHeaders(Inventory inv) {
        var categories = plugin.getConfig().getConfigurationSection("menu.categories");
        if (categories == null) return;
        for (String id : categories.getKeys(false)) {
            String path = "menu.categories." + id + ".";
            int position = slot(path + "slot", -1, inv.getSize());
            if (position < 0) continue;
            Material icon = material(plugin.getConfig().getString(path + "material", "BOOK"), Material.BOOK);
            String name = plugin.getConfig().getString(path + "name", id);
            inv.setItem(position, item(icon, name, List.of()));
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command can only be used in-game."); return true; }
        var target = player.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof CreatureSpawner spawner)) {
            player.sendMessage("§cLook at a spawner within 6 blocks, then run /spawner.");
            return true;
        }
        open(player, spawner);
        return true;
    }

    private int activeRank(CreatureSpawner spawner) {
        return runes.upgrades().stream().filter(Rune::enabled).filter(rune -> matches(spawner, rune)).mapToInt(Rune::rank).max().orElse(0);
    }
    private String activeUpgrade(CreatureSpawner spawner) {
        return runes.upgrades().stream().filter(Rune::enabled).filter(rune -> matches(spawner, rune))
                .max(java.util.Comparator.comparingInt(Rune::rank)).map(Rune::name).orElse(null);
    }
    private boolean matches(CreatureSpawner spawner, Rune rune) {
        String marked = spawner.getPersistentDataContainer().get(runes.appliedUpgradeKey(), PersistentDataType.STRING);
        return rune.isUpgrade() && (rune.id().equals(marked) || spawner.getMinSpawnDelay() == rune.minDelay()
                && spawner.getMaxSpawnDelay() == rune.maxDelay() && spawner.getSpawnCount() == rune.spawnCount()
                && spawner.getMaxNearbyEntities() == rune.maxEntities()
                && (runes.hasEffect(spawner, "require-power") || spawner.getRequiredPlayerRange() == rune.activationRange()));
    }
    private int slot(String path, int fallback, int size) {
        int value = plugin.getConfig().getInt(path, fallback);
        return value >= 0 && value < size ? value : -1;
    }
    private int defaultTraitSlot(String id) {
        return switch (id) { case "silent" -> 10; case "stupor" -> 12; case "gravity" -> 14; case "twilight" -> 15; default -> -1; };
    }
    private int defaultUpgradeSlot(String id) {
        return switch (id) { case "frontiersman" -> 28; case "wayfarer" -> 29; case "mountaineer" -> 30; case "thundering" -> 31; default -> -1; };
    }
    private int defaultStackSlot(String id) {
        return switch (id) { case "more-drops" -> 34; case "wisdom" -> 35; default -> -1; };
    }
    private void putDefaultRune(Inventory inv, String id, int defaultSlot, String detail, boolean powered) {
        int position = slot("menu.slots.default-runes." + id, defaultSlot, inv.getSize());
        if (position < 0) return;
        Rune rune = runes.get(id);
        if (rune == null) return;
        List<String> lore = new ArrayList<>(List.of("§7" + rune.description()));
        for (String line : rune.details()) lore.add("§7" + line);
        lore.add("§7" + detail);
        if (id.equals("require-power")) lore.add("§7Current state: " + (powered ? "§aPowered" : "§cUnpowered"));
        lore.add("");
        lore.add(rune.enabled() ? "§aEnabled" : "§cDisabled by server owner");
        lore.add("§8Built-in rune; configure it in default-runes");
        inv.setItem(position, item(rune.icon(), "§" + rune.color() + rune.name(), lore));
    }
    private Material material(String value, Material fallback) {
        Material parsed = Material.matchMaterial(value);
        return parsed == null ? fallback : parsed;
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); stack.setItemMeta(meta);
        return stack;
    }
    private ItemStack runeItem(Rune rune, List<String> lore) {
        ItemStack stack = item(rune.icon(), "§" + rune.color() + rune.name(), lore);
        return markRuneItem(stack, rune);
    }
    private ItemStack runeItem(Rune rune, List<String> lore, boolean active) {
        ItemStack stack = item(rune.icon(), "§"+rune.color() + rune.name(), lore); //(active ? "§a✔ " : "§c✘ ") +
        return markRuneItem(stack, rune);
    }
    private ItemStack markRuneItem(ItemStack stack, Rune rune) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(menuRuneKey, PersistentDataType.STRING, rune.id());
        stack.setItemMeta(meta);
        return stack;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(title())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || !(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(menuRuneKey, PersistentDataType.STRING);
        if (id == null) return;
        Location location = openSpawnerLocations.get(player.getUniqueId());
        if (location == null || !(location.getBlock().getState() instanceof CreatureSpawner spawner)) return;
        if (!runes.removeRune(spawner, id)) return;
        spawner.update(true, false);
        ItemStack runeItem = runes.makeItem(id);
        if (runeItem != null) {
            var leftovers = player.getInventory().addItem(runeItem);
            for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage("§aRemoved " + runes.get(id).name() + " from the spawner and returned the rune.");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (location.getBlock().getState() instanceof CreatureSpawner refreshed) open(player, refreshed);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(title())) event.setCancelled(true);
    }
}
