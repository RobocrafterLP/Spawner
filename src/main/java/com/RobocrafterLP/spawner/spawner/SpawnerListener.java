package com.RobocrafterLP.spawner.spawner;

import com.RobocrafterLP.spawner.Spawner;
import com.RobocrafterLP.spawner.rune.Rune;
import com.RobocrafterLP.spawner.rune.RuneService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class SpawnerListener implements Listener {
    private final Spawner plugin;
    private final RuneService runes;
    private final SpawnerMenu menu;
    public SpawnerListener(Spawner plugin, RuneService runes, SpawnerMenu menu) {
        this.plugin = plugin; this.runes = runes; this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.SPAWNER) return;
        if (!(event.getClickedBlock().getState() instanceof CreatureSpawner spawner)) return;
        runes.normalizeDisabledUpgrade(spawner);
        runes.syncRequiredPowerRange(spawner);
        String id = runes.runeId(event.getItem());
        if (id == null) {
            if ((event.getItem() == null || event.getItem().getType().isAir()) || event.getPlayer().isSneaking()) {
                event.setCancelled(true);
                menu.open(event.getPlayer(), spawner);
            }
            return;
        }
        event.setCancelled(true);
        var rune = runes.get(id);
        if (rune == null) return;
        if (!rune.enabled()) {
            event.getPlayer().sendMessage("§cThis rune is disabled by the server owner.");
            return;
        }
        if (rune.isReclaim()) {
            event.getPlayer().sendMessage("§eKeep this rune in your inventory while mining a spawner.");
            return;
        }
        if (!rune.isStacking() && runes.isApplied(spawner, id)) {
            event.getPlayer().sendMessage("§cThis rune is already active on this spawner. Remove it from the menu first.");
            return;
        }
        if (rune.isUpgrade() && !runes.isApplied(spawner, id)) {
            if (activeRank(spawner) > 0) {
                event.getPlayer().sendMessage("§cRemove the current upgrade in the menu before applying another.");
                return;
            }
            spawner.getPersistentDataContainer().set(runes.baseSettingsKey(), PersistentDataType.STRING,
                    spawner.getMinSpawnDelay() + "," + spawner.getMaxSpawnDelay() + "," + spawner.getSpawnCount()
                            + "," + spawner.getMaxNearbyEntities() + "," + spawner.getRequiredPlayerRange());
            if (!runes.setSpawnDelays(spawner, rune.minDelay(), rune.maxDelay())) {
                event.getPlayer().sendMessage("§cThe configured rune delay range is invalid for this spawner.");
                return;
            }
            spawner.setSpawnCount(rune.spawnCount()); spawner.setMaxNearbyEntities(rune.maxEntities());
            spawner.setRequiredPlayerRange(rune.activationRange());
            spawner.getPersistentDataContainer().set(runes.appliedUpgradeKey(), PersistentDataType.STRING, rune.id());
        }
        if (rune.isStacking()) {
            int current = runes.stackLevel(spawner, id);
            if (current >= rune.maxStacks()) {
                event.getPlayer().sendMessage("§cThis rune has reached its maximum of " + rune.maxStacks() + " stacks.");
                return;
            }
            runes.addStack(spawner, id);
        }
        if (!rune.isTrait() && !rune.isUpgrade() && !rune.isStacking()) return;
        if (rune.isTrait()) {
            NamespacedKey key = runes.traitKey(id);
            if (key != null) spawner.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        }
        runes.markApplied(spawner, id);
        runes.syncTwilightSpawnRules(spawner);
        spawner.update(true, false);
        runes.syncRequiredPowerRange(spawner);
        runes.consume(event.getItem());
        event.getPlayer().sendMessage("§aRune applied to spawner.");
    }

    @EventHandler(ignoreCancelled = true)
    public void reclaim(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER || !(event.getBlock().getState() instanceof CreatureSpawner spawner)) return;
        ItemStack rune = null;
        var reclaimDefinition = runes.reclaimRune();
        String reclaimId = reclaimDefinition == null || !reclaimDefinition.enabled() ? null : reclaimDefinition.id();
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            if (reclaimId != null && reclaimId.equals(runes.runeId(item))) { rune = item; break; }
        }
        if (rune == null) return;
        event.setDropItems(false);
        ItemStack drop = new ItemStack(Material.SPAWNER);
        var meta = drop.getItemMeta();
        meta.setDisplayName("§dReclaimed " + (spawner.getSpawnedType() == null ? "Unknown" : spawner.getSpawnedType().name()) + " Spawner");
        meta.setLore(java.util.List.of("§7Spawner settings and runes are preserved."));
        meta.getPersistentDataContainer().set(runes.customItemKey(), PersistentDataType.STRING, "reclaimed_spawner");
        if (spawner.getSpawnedType() != null) meta.getPersistentDataContainer().set(runes.reclaimedMobKey(), PersistentDataType.STRING, spawner.getSpawnedType().name());
        String appliedUpgrade = spawner.getPersistentDataContainer().get(runes.appliedUpgradeKey(), PersistentDataType.STRING);
        if (appliedUpgrade != null) meta.getPersistentDataContainer().set(runes.reclaimedUpgradeKey(), PersistentDataType.STRING, appliedUpgrade);
        String activeRunes = spawner.getPersistentDataContainer().get(runes.appliedRunesKey(), PersistentDataType.STRING);
        if (activeRunes != null) meta.getPersistentDataContainer().set(runes.appliedRunesKey(), PersistentDataType.STRING, activeRunes);
        String baseSettings = spawner.getPersistentDataContainer().get(runes.baseSettingsKey(), PersistentDataType.STRING);
        if (baseSettings != null) meta.getPersistentDataContainer().set(runes.baseSettingsKey(), PersistentDataType.STRING, baseSettings);
        String twilightRules = spawner.getPersistentDataContainer().get(runes.twilightSpawnRulesKey(), PersistentDataType.STRING);
        if (twilightRules != null) meta.getPersistentDataContainer().set(runes.twilightSpawnRulesKey(), PersistentDataType.STRING, twilightRules);
        java.util.List<String> traits = new java.util.ArrayList<>();
        for (var trait : runes.traits()) if (runes.hasTrait(spawner, trait.id())) traits.add(trait.id());
        meta.getPersistentDataContainer().set(runes.reclaimedTraitsKey(), PersistentDataType.STRING, String.join(",", traits));
        meta.getPersistentDataContainer().set(runes.reclaimedSettingsKey(), PersistentDataType.STRING,
                spawner.getMinSpawnDelay() + "," + spawner.getMaxSpawnDelay() + "," + spawner.getSpawnCount() + "," + spawner.getMaxNearbyEntities() + "," + spawner.getRequiredPlayerRange());
        meta.getPersistentDataContainer().set(runes.reclaimedStacksKey(), PersistentDataType.STRING, runes.serializeStacks(spawner));
        Integer oldRange = spawner.getPersistentDataContainer().get(runes.originalRangeKey(), PersistentDataType.INTEGER);
        if (oldRange != null) meta.getPersistentDataContainer().set(runes.reclaimedOriginalRangeKey(), PersistentDataType.INTEGER, oldRange);
        drop.setItemMeta(meta);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
        runes.consume(rune);
    }

    @EventHandler(ignoreCancelled = true)
    public void placeReclaimed(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;
        var data = item.getItemMeta().getPersistentDataContainer();
        if (!"reclaimed_spawner".equals(data.get(runes.customItemKey(), PersistentDataType.STRING))) return;
        String mob = data.get(runes.reclaimedMobKey(), PersistentDataType.STRING);
        String settings = data.get(runes.reclaimedSettingsKey(), PersistentDataType.STRING);
        String upgrade = data.get(runes.reclaimedUpgradeKey(), PersistentDataType.STRING);
        String stacks = data.get(runes.reclaimedStacksKey(), PersistentDataType.STRING);
        String activeRunes = data.get(runes.appliedRunesKey(), PersistentDataType.STRING);
        String baseSettings = data.get(runes.baseSettingsKey(), PersistentDataType.STRING);
        String twilightRules = data.get(runes.twilightSpawnRulesKey(), PersistentDataType.STRING);
        Integer oldRange = data.get(runes.reclaimedOriginalRangeKey(), PersistentDataType.INTEGER);
        String traits = data.get(runes.reclaimedTraitsKey(), PersistentDataType.STRING);
        var location = event.getBlockPlaced().getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> restoreSpawner(location, mob, settings, traits, upgrade, stacks, oldRange, activeRunes, baseSettings, twilightRules));
    }

    private void restoreSpawner(org.bukkit.Location location, String mob, String settings, String traits, String upgrade, String stacks,
                                Integer oldRange, String activeRunes, String baseSettings, String twilightRules) {
        if (!(location.getBlock().getState() instanceof CreatureSpawner spawner)) return;
        if (mob != null) {
            try { spawner.setSpawnedType(org.bukkit.entity.EntityType.valueOf(mob)); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Unknown saved spawner entity type: " + mob); }
        }
        if (settings != null) {
            try {
                String[] v = settings.split(",");
                if (v.length != 5) throw new IllegalArgumentException("Expected five values");
                if (!runes.setSpawnDelays(spawner, Integer.parseInt(v[0]), Integer.parseInt(v[1]))) {
                    plugin.getLogger().warning("Could not restore reclaimed spawner delay settings.");
                    return;
                }
                spawner.setSpawnCount(Integer.parseInt(v[2])); spawner.setMaxNearbyEntities(Integer.parseInt(v[3]));
                spawner.setRequiredPlayerRange(Integer.parseInt(v[4]));
            } catch (RuntimeException ignored) { plugin.getLogger().warning("Could not restore reclaimed spawner settings."); }
        }
        if (traits != null && !traits.isBlank()) for (String id : traits.split(",")) {
            var key = runes.traitKey(id);
            if (key != null) spawner.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        }
        if (upgrade != null) spawner.getPersistentDataContainer().set(runes.appliedUpgradeKey(), PersistentDataType.STRING, upgrade);
        if (activeRunes != null) spawner.getPersistentDataContainer().set(runes.appliedRunesKey(), PersistentDataType.STRING, activeRunes);
        if (baseSettings != null) spawner.getPersistentDataContainer().set(runes.baseSettingsKey(), PersistentDataType.STRING, baseSettings);
        if (twilightRules != null) spawner.getPersistentDataContainer().set(runes.twilightSpawnRulesKey(), PersistentDataType.STRING, twilightRules);
        if (oldRange != null) spawner.getPersistentDataContainer().set(runes.originalRangeKey(), PersistentDataType.INTEGER, oldRange);
        runes.restoreStacks(spawner, stacks);
        spawner.update(true, false);
        runes.normalizeDisabledUpgrade(spawner);
        runes.syncRequiredPowerRange(spawner);
        runes.syncTwilightSpawnRules(spawner);
    }

    @EventHandler
    public void chunkLoaded(ChunkLoadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (!(state instanceof CreatureSpawner spawner)) continue;
            runes.syncRequiredPowerRange(spawner);
            runes.syncTwilightSpawnRules(spawner);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void spawned(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        var location = event.getLocation();
        CreatureSpawner closest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -5; x <= 5; x++) for (int y = -3; y <= 3; y++) for (int z = -5; z <= 5; z++) {
            var candidate = location.getBlock().getRelative(x, y, z).getState();
            if (!(candidate instanceof CreatureSpawner spawner)) continue;
            double distance = spawner.getLocation().distanceSquared(location);
            if (distance < nearestDistance) { nearestDistance = distance; closest = spawner; }
        }
        if (closest != null) {
            CreatureSpawner spawner = closest;
            runes.syncRequiredPowerRange(spawner);
            if (runes.normalizeDisabledUpgrade(spawner)) {
                event.setCancelled(true);
                return;
            }
            if (runes.hasEffect(spawner, "require-power")) {
                int range = runes.highestUpgradeRange();
                boolean nearby = spawner.getWorld().getPlayers().stream().anyMatch(player ->
                        player.getLocation().distanceSquared(spawner.getLocation()) <= (double) range * range);
                if (!runes.isPowered(spawner) || !nearby) { event.setCancelled(true); return; }
            }
            Rune dropsRune = runes.get("more-drops");
            int dropStacks = dropsRune != null && dropsRune.enabled() ? runes.stackLevel(spawner, dropsRune.id()) : 0;
            Rune xpRune = runes.get("wisdom");
            int xpStacks = xpRune != null && xpRune.enabled() ? runes.stackLevel(spawner, xpRune.id()) : 0;
            if (dropStacks > 0) event.getEntity().getPersistentDataContainer().set(runes.bonusDropsKey(), PersistentDataType.INTEGER, dropStacks * dropsRune.extraDropsPerStack());
            if (xpStacks > 0) event.getEntity().getPersistentDataContainer().set(runes.bonusExperienceKey(), PersistentDataType.INTEGER, xpStacks * xpRune.extraExperiencePercentPerStack());
            if (runes.hasEffect(spawner, "no-item-drops") && dropStacks == 0) {
                event.getEntity().getPersistentDataContainer().set(runes.noItemDropsKey(), PersistentDataType.BYTE, (byte) 1);
            }
            if (runes.hasEffect(spawner, "silent")) event.getEntity().setSilent(true);
            if (runes.hasEffect(spawner, "no-ai") && event.getEntity() instanceof org.bukkit.entity.Mob mob) {
                // Paper's aware flag disables autonomous actions/pathfinding while leaving physics and gravity enabled.
                mob.setAware(false);
            }
            if (runes.hasEffect(spawner, "gravity")) event.getEntity().setGravity(false);
        }
    }

    @EventHandler
    public void redstoneChanged(BlockRedstoneEvent event) {
        Block changed = event.getBlock();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            var block = changed.getRelative(x, y, z);
            if (block.getType() != Material.SPAWNER) continue;
            var location = block.getLocation();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (location.getBlock().getState() instanceof CreatureSpawner spawner) runes.syncRequiredPowerRange(spawner);
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void mobDrops(EntityDeathEvent event) {
        var data = event.getEntity().getPersistentDataContainer();
        int bonusDrops = data.getOrDefault(runes.bonusDropsKey(), PersistentDataType.INTEGER, 0);
        if (bonusDrops == 0 && data.has(runes.noItemDropsKey(), PersistentDataType.BYTE)) {
            event.getDrops().clear();
        }
        if (bonusDrops > 0 && !event.getDrops().isEmpty()) {
            var originalDrops = event.getDrops().stream().map(ItemStack::clone).toList();
            for (ItemStack stack : originalDrops) {
                int extra = stack.getAmount() * bonusDrops;
                while (extra > 0) {
                    int amount = Math.min(extra, stack.getMaxStackSize());
                    ItemStack additional = stack.clone();
                    additional.setAmount(amount);
                    event.getDrops().add(additional);
                    extra -= amount;
                }
            }
        }
        int xpBonus = data.getOrDefault(runes.bonusExperienceKey(), PersistentDataType.INTEGER, 0);
        if (xpBonus > 0) event.setDroppedExp(event.getDroppedExp() + event.getDroppedExp() * xpBonus / 100);
    }
    private int activeRank(CreatureSpawner s) {
        String appliedId = s.getPersistentDataContainer().get(runes.appliedUpgradeKey(), PersistentDataType.STRING);
        Rune applied = appliedId == null ? null : runes.get(appliedId);
        if (applied != null && applied.isUpgrade() && applied.enabled()) return applied.rank();
        return runes.upgrades().stream().filter(Rune::enabled).filter(rune -> s.getMinSpawnDelay() == rune.minDelay()
                        && s.getMaxSpawnDelay() == rune.maxDelay() && s.getSpawnCount() == rune.spawnCount()
                        && s.getMaxNearbyEntities() == rune.maxEntities()
                        && (runes.defaultEnabled("require-power") || s.getRequiredPlayerRange() == rune.activationRange()))
                .mapToInt(com.RobocrafterLP.spawner.rune.Rune::rank).max().orElse(0);
    }
}
