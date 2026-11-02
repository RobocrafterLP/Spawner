package com.RobocrafterLP.spawner.rune;

import com.RobocrafterLP.spawner.Spawner;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RuneService implements CommandExecutor, TabCompleter {
    private final Spawner plugin;
    private final NamespacedKey runeKey;
    private final NamespacedKey customItemKey;
    private final NamespacedKey reclaimedMobKey;
    private final NamespacedKey reclaimedTraitsKey;
    private final NamespacedKey reclaimedSettingsKey;
    private final NamespacedKey reclaimedUpgradeKey;
    private final NamespacedKey appliedUpgradeKey;
    private final NamespacedKey noItemDropsKey;
    private final NamespacedKey reclaimedStacksKey;
    private final NamespacedKey reclaimedOriginalRangeKey;
    private final NamespacedKey bonusDropsKey;
    private final NamespacedKey bonusExperienceKey;
    private final NamespacedKey originalRangeKey;
    private final NamespacedKey appliedRunesKey;
    private final NamespacedKey baseSettingsKey;
    private final NamespacedKey twilightSpawnRulesKey;
    private final Map<String, NamespacedKey> traitKeys = new HashMap<>();
    private final Map<String, NamespacedKey> stackKeys = new HashMap<>();
    private final Map<String, Rune> runes = new LinkedHashMap<>();

    public RuneService(Spawner plugin) {
        this.plugin = plugin;
        runeKey = new NamespacedKey(plugin, "rune");
        customItemKey = new NamespacedKey(plugin, "custom_item_id");
        reclaimedMobKey = new NamespacedKey(plugin, "reclaimed_mob");
        reclaimedTraitsKey = new NamespacedKey(plugin, "reclaimed_traits");
        reclaimedSettingsKey = new NamespacedKey(plugin, "reclaimed_settings");
        reclaimedUpgradeKey = new NamespacedKey(plugin, "reclaimed_upgrade");
        appliedUpgradeKey = new NamespacedKey(plugin, "applied_upgrade");
        noItemDropsKey = new NamespacedKey(plugin, "no_item_drops");
        reclaimedStacksKey = new NamespacedKey(plugin, "reclaimed_stacks");
        reclaimedOriginalRangeKey = new NamespacedKey(plugin, "reclaimed_original_range");
        bonusDropsKey = new NamespacedKey(plugin, "bonus_drops");
        bonusExperienceKey = new NamespacedKey(plugin, "bonus_experience");
        originalRangeKey = new NamespacedKey(plugin, "power_original_range");
        appliedRunesKey = new NamespacedKey(plugin, "applied_runes");
        baseSettingsKey = new NamespacedKey(plugin, "rune_base_settings");
        twilightSpawnRulesKey = new NamespacedKey(plugin, "twilight_original_spawn_rules");
        plugin.saveDefaultConfig();
        mergeNewDefaults();
        loadRunes();
    }

    private void loadRunes() {
        runes.clear();
        traitKeys.clear();
        stackKeys.clear();
        loadRuneSection("default-runes");
        loadRuneSection("runes");
    }

    private void loadRuneSection(String sectionName) {
        var section = plugin.getConfig().getConfigurationSection(sectionName);
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = sectionName + "." + id + ".";
            Material icon = Material.matchMaterial(plugin.getConfig().getString(path + "icon", "AMETHYST_SHARD"));
            String defaultType = (plugin.getConfig().isConfigurationSection(path + "upgrade")
                    || plugin.getConfig().isConfigurationSection(path + "item.upgrade"))
                    || List.of("frontiersman", "wayfarer", "mountaineer", "thundering").contains(id)
                    ? "upgrade" : plugin.getConfig().isConfigurationSection(path + "stacking")
                    || plugin.getConfig().isConfigurationSection(path + "item.stacking")
                    ? "stacking" : id.equalsIgnoreCase("reclaim") ? "reclaim" : "trait";
            String type = plugin.getConfig().getString(path + "type", defaultType).toLowerCase(Locale.ROOT);
            int[] oldUpgradeDefaults = legacyUpgradeDefaults(id);
            String itemPath = path + "item.";
            String upgradePath = plugin.getConfig().isConfigurationSection(itemPath + "upgrade") ? itemPath + "upgrade." : path + "upgrade.";
            String stackingPath = plugin.getConfig().isConfigurationSection(itemPath + "stacking") ? itemPath + "stacking." : path + "stacking.";
            String effectsPath = plugin.getConfig().isConfigurationSection(itemPath + "effects") ? itemPath + "effects" : path + "effects";
            Rune rune = new Rune(id.toLowerCase(Locale.ROOT),
                    plugin.getConfig().getString(itemPath + "name", plugin.getConfig().getString(path + "name", id)),
                    plugin.getConfig().getString(itemPath + "description", plugin.getConfig().getString(path + "description", "A mysterious spawner rune.")),
                    icon == null ? Material.AMETHYST_SHARD : icon,
                    plugin.getConfig().getString(path + "color", "d"),
                    plugin.getConfig().getStringList(path + "details"), type,
                    plugin.getConfig().getBoolean(itemPath + "enabled", plugin.getConfig().getBoolean(path + "enabled", true)),
                    plugin.getConfig().getInt(upgradePath + "min-delay", oldUpgradeDefaults[0]),
                    plugin.getConfig().getInt(upgradePath + "max-delay", oldUpgradeDefaults[1]),
                    plugin.getConfig().getInt(upgradePath + "spawn-count", oldUpgradeDefaults[2]),
                    plugin.getConfig().getInt(upgradePath + "max-entities", oldUpgradeDefaults[3]),
                    plugin.getConfig().getInt(upgradePath + "activation-range", oldUpgradeDefaults[4]),
                    plugin.getConfig().getInt(upgradePath + "rank", oldUpgradeDefaults[5]),
                    plugin.getConfig().getInt(stackingPath + "max-stacks", 1),
                    plugin.getConfig().getInt(stackingPath + "extra-drops-per-stack", 0),
                    plugin.getConfig().getInt(stackingPath + "extra-experience-percent-per-stack", 0),
                    readEffects(effectsPath));
            runes.putIfAbsent(rune.id(), rune);
            if (rune.isTrait()) traitKeys.put(rune.id(), new NamespacedKey(plugin, rune.id()));
            if (rune.isStacking()) stackKeys.put(rune.id(), new NamespacedKey(plugin, "stacks_" + rune.id()));
        }
    }

    private int[] legacyUpgradeDefaults(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "frontiersman" -> new int[]{150, 600, 7, 12, 24, 1};
            case "wayfarer" -> new int[]{100, 400, 10, 18, 32, 2};
            case "mountaineer" -> new int[]{50, 200, 13, 24, 40, 3};
            case "thundering" -> new int[]{50, 50, 16, 32, 48, 4};
            default -> new int[]{0, 0, 0, 0, 0, 0};
        };
    }

    private void mergeNewDefaults() {
        try (var resource = plugin.getResource("config.yml")) {
            if (resource == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            boolean changed = false;
            List<String> missingDefaults = new ArrayList<>();
            for (String id : List.of("require-power", "no-item-drops", "silent", "stupor", "gravity", "twilight", "reclaim")) {
                if (!plugin.getConfig().isConfigurationSection("default-runes." + id)) missingDefaults.add(id);
            }
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path) || plugin.getConfig().contains(path)) continue;
                Object defaultValue = defaults.get(path);
                String legacyPath = legacyConfigPath(path);
                if (legacyPath != null && plugin.getConfig().contains(legacyPath)) defaultValue = plugin.getConfig().get(legacyPath);
                plugin.getConfig().set(path, defaultValue);
                changed = true;
            }
            changed |= migrateMenuValue("menu.size", 45, 54);
            changed |= migrateMenuValue("menu.slots.reclaim", 16, 8);
            changed |= migrateMenuValue("menu.slots.traits.twilight", 15, 16);
            changed |= migrateMenuValue("menu.slots.default-runes.require-power", 32, 17);
            changed |= migrateMenuValue("menu.slots.default-runes.no-item-drops", 33, 15);
            changed |= migrateMenuValue("menu.slots.stackable.more-drops", 34, 46);
            changed |= migrateMenuValue("menu.slots.stackable.wisdom", 35, 47);
            changed |= migrateMenuValue("menu.slots.runes.warden", 36, 32);
            var defaultRunes = plugin.getConfig().getConfigurationSection("default-runes");
            if (defaultRunes != null) for (String id : defaultRunes.getKeys(false)) {
                String typePath = "default-runes." + id + ".type";
                if ("default".equalsIgnoreCase(plugin.getConfig().getString(typePath, ""))) {
                    plugin.getConfig().set(typePath, "trait");
                    changed = true;
                }
            }
            if (plugin.getConfig().isConfigurationSection("default-upgrades")) { plugin.getConfig().set("default-upgrades", null); changed = true; }
            for (String id : List.of("require-power", "no-item-drops", "silent", "stupor", "gravity", "twilight", "reclaim")) {
                if (plugin.getConfig().contains("runes." + id)) { plugin.getConfig().set("runes." + id, null); changed = true; }
            }
            if (plugin.getConfig().contains("redstone.require-power")) { plugin.getConfig().set("redstone.require-power", null); changed = true; }
            if (plugin.getConfig().isConfigurationSection("redstone") && plugin.getConfig().getConfigurationSection("redstone").getKeys(false).isEmpty()) {
                plugin.getConfig().set("redstone", null); changed = true;
            }
            if (changed) {
                plugin.saveConfig();
                plugin.getLogger().info("Missing config defaults were added to plugins/Spawner/config.yml.");
            }
            for (String id : missingDefaults) {
                plugin.getLogger().warning("Required default rune config 'default-runes." + id + "' was missing; a default entry was added to plugins/Spawner/config.yml.");
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not merge new config defaults: " + exception.getMessage());
        }
    }

    private boolean migrateMenuValue(String path, int oldValue, int newValue) {
        if (plugin.getConfig().getInt(path, Integer.MIN_VALUE) != oldValue) return false;
        plugin.getConfig().set(path, newValue);
        return true;
    }

    private String legacyConfigPath(String path) {
        if (!path.startsWith("default-runes.")) return null;
        String tail = path.substring("default-runes.".length());
        int split = tail.indexOf('.');
        if (split < 0) return null;
        String id = tail.substring(0, split);
        String property = tail.substring(split + 1);
        if (id.equals("require-power") || id.equals("no-item-drops")) {
            String prior = "default-upgrades." + id + "." + property;
            if (plugin.getConfig().contains(prior)) return prior;
            if (id.equals("require-power") && property.equals("enabled") && plugin.getConfig().contains("redstone.require-power")) return "redstone.require-power";
            return null;
        }
        String prior = "runes." + id + "." + property;
        return plugin.getConfig().contains(prior) ? prior : null;
    }

    public Rune get(String id) { return runes.get(id); }
    public Collection<Rune> all() { return Collections.unmodifiableCollection(runes.values()); }
    public List<Rune> upgrades() { return runes.values().stream().filter(Rune::isUpgrade).sorted(Comparator.comparingInt(Rune::rank)).toList(); }
    public List<Rune> stackables() { return runes.values().stream().filter(Rune::isStacking).toList(); }
    public List<Rune> traits() { return runes.values().stream().filter(Rune::isTrait).toList(); }
    public boolean defaultEnabled(String id) { return false; }
    public Rune reclaimRune() { return runes.values().stream().filter(Rune::isReclaim).findFirst().orElse(null); }
    public boolean hasTrait(org.bukkit.block.CreatureSpawner spawner, String id) {
        NamespacedKey key = traitKeys.get(id);
        Rune rune = runes.get(id);
        return key != null && rune != null && rune.enabled() && (isApplied(spawner, id)
                || spawner.getPersistentDataContainer().has(key, PersistentDataType.BYTE));
    }
    public boolean isApplied(CreatureSpawner spawner, String id) {
        Rune rune = runes.get(id);
        if (rune == null) return false;
        if (rune.isStacking()) return stackLevel(spawner, id) > 0;
        String active = spawner.getPersistentDataContainer().get(appliedRunesKey, PersistentDataType.STRING);
        if (active != null && Arrays.asList(active.split(",")).contains(id)) return true;
        if (rune.isUpgrade() && id.equals(spawner.getPersistentDataContainer().get(appliedUpgradeKey, PersistentDataType.STRING))) return true;
        if (rune.isUpgrade() && spawner.getMinSpawnDelay() == rune.minDelay() && spawner.getMaxSpawnDelay() == rune.maxDelay()
                && spawner.getSpawnCount() == rune.spawnCount() && spawner.getMaxNearbyEntities() == rune.maxEntities()
                && (defaultEnabled("require-power") || spawner.getRequiredPlayerRange() == rune.activationRange())) return true;
        NamespacedKey legacy = traitKeys.get(id);
        return legacy != null && spawner.getPersistentDataContainer().has(legacy, PersistentDataType.BYTE);
    }
    public void markApplied(CreatureSpawner spawner, String id) {
        var data = spawner.getPersistentDataContainer();
        Set<String> active = new LinkedHashSet<>();
        String previous = data.get(appliedRunesKey, PersistentDataType.STRING);
        if (previous != null && !previous.isBlank()) active.addAll(Arrays.asList(previous.split(",")));
        active.add(id);
        data.set(appliedRunesKey, PersistentDataType.STRING, String.join(",", active));
    }
    public void unmarkApplied(CreatureSpawner spawner, String id) {
        var data = spawner.getPersistentDataContainer();
        String previous = data.get(appliedRunesKey, PersistentDataType.STRING);
        if (previous != null) {
            Set<String> active = new LinkedHashSet<>(Arrays.asList(previous.split(",")));
            active.remove(id);
            if (active.isEmpty()) data.remove(appliedRunesKey);
            else data.set(appliedRunesKey, PersistentDataType.STRING, String.join(",", active));
        }
        NamespacedKey legacy = traitKeys.get(id);
        if (legacy != null) data.remove(legacy);
    }
    public boolean hasEffect(CreatureSpawner spawner, String effect) {
        for (Rune rune : runes.values()) {
            if (!rune.enabled() || !isApplied(spawner, rune.id())) continue;
            String runeEffect = normalizedEffectId(rune.id());
            if (Boolean.TRUE.equals(rune.effects().get(effect))
                    || rune.isTrait() && runeEffect.equals(effect) && !rune.effects().containsKey(effect)) return true;
        }
        return false;
    }
    public String effectSources(CreatureSpawner spawner, String effect) {
        List<String> sources = new ArrayList<>();
        for (Rune rune : runes.values()) {
            if (!rune.enabled() || !isApplied(spawner, rune.id())) continue;
            String runeEffect = normalizedEffectId(rune.id());
            if (Boolean.TRUE.equals(rune.effects().get(effect))
                    || rune.isTrait() && runeEffect.equals(effect) && !rune.effects().containsKey(effect)) sources.add(rune.name());
        }
        return sources.isEmpty() ? "None" : String.join(", ", sources);
    }
    public String effectDisplayName(String effect) {
        String normalized = effect.replace('_', '-').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        //if (normalized.equals("no ai")) return "No AI";
        if (normalized.isEmpty()) return effect;
        StringBuilder display = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (display.length() > 0) display.append(' ');
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }
    private Map<String, Boolean> readEffects(String path) {
        var section = plugin.getConfig().getConfigurationSection(path);
        Map<String, Boolean> effects = new LinkedHashMap<>();
        if (section != null) for (String effect : section.getKeys(false)) effects.put(normalizedEffectId(effect), section.getBoolean(effect, false));
        else for (String effect : plugin.getConfig().getStringList(path)) effects.put(normalizedEffectId(effect), true);
        return effects;
    }
    public String normalizedEffectId(String effect) {
        String normalized = effect.toLowerCase(Locale.ROOT);
        return normalized.equals("stupor") ? "no-ai" : normalized;
    }
    public NamespacedKey traitKey(String id) { return traitKeys.get(id); }
    public NamespacedKey reclaimedMobKey() { return reclaimedMobKey; }
    public NamespacedKey customItemKey() { return customItemKey; }
    public NamespacedKey reclaimedTraitsKey() { return reclaimedTraitsKey; }
    public NamespacedKey reclaimedSettingsKey() { return reclaimedSettingsKey; }
    public NamespacedKey reclaimedUpgradeKey() { return reclaimedUpgradeKey; }
    public NamespacedKey appliedUpgradeKey() { return appliedUpgradeKey; }
    public NamespacedKey noItemDropsKey() { return noItemDropsKey; }
    public NamespacedKey reclaimedStacksKey() { return reclaimedStacksKey; }
    public NamespacedKey reclaimedOriginalRangeKey() { return reclaimedOriginalRangeKey; }
    public NamespacedKey originalRangeKey() { return originalRangeKey; }
    public NamespacedKey appliedRunesKey() { return appliedRunesKey; }
    public NamespacedKey baseSettingsKey() { return baseSettingsKey; }
    public NamespacedKey twilightSpawnRulesKey() { return twilightSpawnRulesKey; }
    public NamespacedKey bonusDropsKey() { return bonusDropsKey; }
    public NamespacedKey bonusExperienceKey() { return bonusExperienceKey; }
    public boolean setSpawnDelays(CreatureSpawner spawner, int minimum, int maximum) {
        if (minimum < 0 || maximum < minimum) return false;
        int oldMinimum = spawner.getMinSpawnDelay();
        int oldMaximum = spawner.getMaxSpawnDelay();
        try {
            if (minimum > oldMaximum) {
                spawner.setMaxSpawnDelay(maximum);
                spawner.setMinSpawnDelay(minimum);
            } else if (maximum < oldMinimum) {
                spawner.setMinSpawnDelay(minimum);
                spawner.setMaxSpawnDelay(maximum);
            } else {
                spawner.setMinSpawnDelay(minimum);
                spawner.setMaxSpawnDelay(maximum);
            }
            return true;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not set spawner delay range " + minimum + "-" + maximum + ": " + exception.getMessage());
            return false;
        }
    }
    public void syncTwilightSpawnRules(CreatureSpawner spawner) {
        var data = spawner.getPersistentDataContainer();
        boolean active = hasEffect(spawner, "twilight");
        List<SpawnerEntry> entries = new ArrayList<>(spawner.getPotentialSpawns());
        if (active) {
            if (!data.has(twilightSpawnRulesKey, PersistentDataType.STRING)) {
                data.set(twilightSpawnRulesKey, PersistentDataType.STRING, serializeSpawnRules(entries));
            }
            if (entries.isEmpty()) {
                org.bukkit.entity.EntitySnapshot current = spawner.getSpawnedEntity();
                if (current == null) {
                    plugin.getLogger().warning("Twilight rune is installed on a spawner with no spawn entry; cannot adjust its light rules.");
                    return;
                }
                entries.add(new SpawnerEntry(current, 1, new SpawnRule(0, 15, 0, 15)));
            } else {
                for (SpawnerEntry entry : entries) entry.setSpawnRule(new SpawnRule(0, 15, 0, 15));
            }
            spawner.setPotentialSpawns(entries);
            spawner.update(true, false);
        } else if (data.has(twilightSpawnRulesKey, PersistentDataType.STRING)) {
            restoreSpawnRules(entries, data.get(twilightSpawnRulesKey, PersistentDataType.STRING));
            data.remove(twilightSpawnRulesKey);
            spawner.setPotentialSpawns(entries);
            spawner.update(true, false);
        }
    }

    private String serializeSpawnRules(List<SpawnerEntry> entries) {
        List<String> serialized = new ArrayList<>();
        serialized.add("v1");
        for (SpawnerEntry entry : entries) {
            SpawnRule rule = entry.getSpawnRule();
            serialized.add(rule == null ? "-" : rule.getMinBlockLight() + "," + rule.getMaxBlockLight()
                    + "," + rule.getMinSkyLight() + "," + rule.getMaxSkyLight());
        }
        return String.join(";", serialized);
    }

    private void restoreSpawnRules(List<SpawnerEntry> entries, String serialized) {
        if (serialized == null || !serialized.startsWith("v1")) return;
        String[] values = serialized.split(";", -1);
        if (values.length == 1) {
            entries.clear();
            return;
        }
        if (values.length - 1 != entries.size()) {
            plugin.getLogger().warning("Twilight spawn rules changed while active; restoring saved light rules by entry position where possible.");
        }
        for (int index = 0; index < entries.size() && index + 1 < values.length; index++) {
            String value = values[index + 1];
            if (value.equals("-")) {
                entries.get(index).setSpawnRule(null);
                continue;
            }
            try {
                String[] light = value.split(",");
                if (light.length == 4) entries.get(index).setSpawnRule(new SpawnRule(
                        Integer.parseInt(light[0]), Integer.parseInt(light[1]),
                        Integer.parseInt(light[2]), Integer.parseInt(light[3])));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Could not restore an original Twilight spawn rule.");
            }
        }
    }
    public String runeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(runeKey, PersistentDataType.STRING);
    }
    public ItemStack makeItem(String id) {
        Rune rune = runes.get(id.toLowerCase(Locale.ROOT));
        if (rune == null || rune.isGlobal()) return null;
        ItemStack item = new ItemStack(rune.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§" + rune.color() + rune.name());
        List<String> lore = new ArrayList<>();
        lore.add("§7" + rune.description());
        lore.add("");
        lore.add(rune.enabled() ? "§aEnabled" : "§cDisabled by server owner");
        if (!rune.effects().isEmpty()) {
            lore.add("");
            lore.add("§7Effects:");
            rune.effects().forEach((effect, enabled) -> lore.add("§7" + effectDisplayName(effect) + ": " + (enabled ? "§aEnabled" : "§cDisabled")));
        }
        if (rune.isStacking()) {
            lore.add("");
            lore.add("§7Stacking: §fup to " + rune.maxStacks() + " applications");
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
            for (String line : rune.details()) lore.add("§f" + line);
        }
        lore.add("");
        lore.add("§eRight-click a spawner to apply");
        lore.add("§8Item ID: rune:" + rune.id());
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(runeKey, PersistentDataType.STRING, rune.id());
        meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.STRING, "rune:" + rune.id());
        item.setItemMeta(meta);
        return item;
    }
    public void consume(ItemStack item) { if (item.getAmount() <= 1) item.setAmount(0); else item.setAmount(item.getAmount() - 1); }
    public boolean playerHasRune(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) if (id.equals(runeId(item))) return true;
        return false;
    }

    public int stackLevel(CreatureSpawner spawner, String runeId) {
        NamespacedKey key = stackKeys.get(runeId);
        return key == null ? 0 : spawner.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    public void addStack(CreatureSpawner spawner, String runeId) {
        NamespacedKey key = stackKeys.get(runeId);
        if (key != null) spawner.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, stackLevel(spawner, runeId) + 1);
    }

    public boolean removeRune(CreatureSpawner spawner, String id) {
        Rune rune = runes.get(id);
        if (rune == null || !isApplied(spawner, id)) return false;
        var data = spawner.getPersistentDataContainer();
        if (rune.isStacking()) {
            NamespacedKey key = stackKeys.get(id);
            int level = stackLevel(spawner, id);
            if (level > 1) {
                data.set(key, PersistentDataType.INTEGER, level - 1);
                spawner.update(true, false);
                return true;
            }
            data.remove(key);
        }
        if (rune.isUpgrade()) {
            String base = data.get(baseSettingsKey, PersistentDataType.STRING);
            if (base != null) {
                try {
                    String[] v = base.split(",");
                    if (v.length == 5) {
                        if (!setSpawnDelays(spawner, Integer.parseInt(v[0]), Integer.parseInt(v[1]))) {
                            return false;
                        }
                        spawner.setSpawnCount(Integer.parseInt(v[2])); spawner.setMaxNearbyEntities(Integer.parseInt(v[3]));
                        spawner.setRequiredPlayerRange(Integer.parseInt(v[4]));
                    }
                } catch (NumberFormatException ignored) { }
                data.remove(baseSettingsKey);
            }
            data.remove(appliedUpgradeKey);
        }
        unmarkApplied(spawner, id);
        syncTwilightSpawnRules(spawner);
        spawner.update(true, false);
        syncRequiredPowerRange(spawner);
        return true;
    }

    public String serializeStacks(CreatureSpawner spawner) {
        List<String> values = new ArrayList<>();
        for (Rune rune : stackables()) {
            int level = stackLevel(spawner, rune.id());
            if (level > 0) values.add(rune.id() + "=" + level);
        }
        return String.join(";", values);
    }

    public void restoreStacks(CreatureSpawner spawner, String serialized) {
        if (serialized == null || serialized.isBlank()) return;
        for (String entry : serialized.split(";")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2) continue;
            Rune rune = runes.get(pair[0]);
            NamespacedKey key = stackKeys.get(pair[0]);
            if (rune == null || key == null || !rune.isStacking()) continue;
            try {
                int level = Math.max(0, Math.min(rune.maxStacks(), Integer.parseInt(pair[1])));
                if (level > 0) spawner.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
            } catch (NumberFormatException ignored) { }
        }
    }

    public int highestUpgradeRange() {
        return upgrades().stream().filter(Rune::enabled).max(Comparator.comparingInt(Rune::rank))
                .map(Rune::activationRange).orElse(16);
    }

    public boolean isPowered(CreatureSpawner spawner) {
        return spawner.getBlock().isBlockPowered() || spawner.getBlock().isBlockIndirectlyPowered();
    }

    public void syncRequiredPowerRange(CreatureSpawner spawner) {
        NamespacedKey key = originalRangeKey;
        var data = spawner.getPersistentDataContainer();
        if (hasEffect(spawner, "require-power")) {
            if (!data.has(key, PersistentDataType.INTEGER)) {
                int current = spawner.getRequiredPlayerRange();
                data.set(key, PersistentDataType.INTEGER, current > 0 ? current : 16);
            }
            int desired = isPowered(spawner) ? highestUpgradeRange() : 0;
            if (spawner.getRequiredPlayerRange() != desired) {
                spawner.setRequiredPlayerRange(desired);
                spawner.update(true, false);
            }
        } else if (data.has(key, PersistentDataType.INTEGER)) {
            String upgradeId = data.get(appliedUpgradeKey, PersistentDataType.STRING);
            Rune applied = upgradeId == null ? null : runes.get(upgradeId);
            int restore = applied != null && applied.isUpgrade() ? applied.activationRange() : data.get(key, PersistentDataType.INTEGER);
            spawner.setRequiredPlayerRange(restore);
            data.remove(key);
            spawner.update(true, false);
        }
    }

    public void reload(CommandSender sender) {
        plugin.reloadConfig();
        mergeNewDefaults();
        loadRunes();
        normalizeDisabledUpgradesInLoadedChunks();
        sender.sendMessage("§aSpawner config reloaded. Loaded " + runes.size() + " runes.");
    }

    public void normalizeDisabledUpgradesInLoadedChunks() {
        for (World world : plugin.getServer().getWorlds()) for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) if (state instanceof CreatureSpawner spawner) {
                normalizeDisabledUpgrade(spawner);
                syncRequiredPowerRange(spawner);
                syncTwilightSpawnRules(spawner);
            }
        }
    }

    public boolean normalizeDisabledUpgrade(CreatureSpawner spawner) {
        String applied = spawner.getPersistentDataContainer().get(appliedUpgradeKey, PersistentDataType.STRING);
        Rune marked = applied == null ? null : runes.get(applied);
        boolean disabledMarked = marked != null && marked.isUpgrade() && !marked.enabled();
        boolean matchesDisabled = this.upgrades().stream().anyMatch(r -> !r.enabled()
                && spawner.getMinSpawnDelay() == r.minDelay() && spawner.getMaxSpawnDelay() == r.maxDelay()
                && spawner.getSpawnCount() == r.spawnCount() && spawner.getMaxNearbyEntities() == r.maxEntities()
                && spawner.getRequiredPlayerRange() == r.activationRange());
        if (!disabledMarked && !matchesDisabled) return false;
        setSpawnDelays(spawner, 200, 800); spawner.setSpawnCount(4);
        spawner.setMaxNearbyEntities(6); spawner.setRequiredPlayerRange(16);
        spawner.getPersistentDataContainer().remove(appliedUpgradeKey);
        spawner.update(true, false);
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (args.length == 1) reload(sender);
            else sender.sendMessage("§cUsage: /rune reload");
            return true;
        }
        if (args.length < 2 || args.length > 3) return usage(sender);
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cPlayer must be online. Usage: /rune <player> <rune>"); return true; }
        int amount = 1;
        if (args.length == 3) {
            try { amount = Integer.parseInt(args[2]); }
            catch (NumberFormatException ignored) { return amountError(sender); }
            if (amount < 1 || amount > 64) return amountError(sender);
        }
        if (args[1].equalsIgnoreCase("all")) {
            int count = 0;
            for (Rune rune : runes.values()) {
                if (!rune.enabled() || rune.isGlobal()) continue;
                ItemStack item = makeItem(rune.id());
                if (item == null) continue;
                giveItems(target, item, amount);
                count++;
            }
            sender.sendMessage("§aGave " + amount + " of each of " + count + " enabled runes to " + target.getName() + ".");
            return true;
        }
        Rune rune = get(args[1].toLowerCase(Locale.ROOT));
        if (rune == null) { sender.sendMessage("§cUnknown rune. Use 'all' or choose: " + String.join(", ", runes.keySet())); return true; }
        if (rune.isGlobal()) { sender.sendMessage("§cThis rune is server-wide and cannot be given as an item."); return true; }
        if (!rune.enabled()) { sender.sendMessage("§cThat rune is disabled in the server config."); return true; }
        ItemStack item = makeItem(rune.id());
        giveItems(target, item, amount);
        sender.sendMessage("§aGave " + amount + " × " + item.getItemMeta().getDisplayName() + " §ato " + target.getName() + ".");
        return true;
    }

    private void giveItems(Player target, ItemStack template, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int stackAmount = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(stackAmount);
            var leftovers = target.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            remaining -= stackAmount;
        }
    }

    private boolean amountError(CommandSender sender) {
        sender.sendMessage("§cAmount must be a whole number from 1 to 64.");
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage("§e/rune <player> <rune|all> [amount] §7or §e/rune reload");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("reload")) return List.of();
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("reload");
            options.addAll(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
            return options.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>(runes.keySet());
            options.add("all");
            return options.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3) return List.of("1", "16", "64").stream()
                .filter(v -> v.startsWith(args[2])).toList();
        return List.of();
    }
}
