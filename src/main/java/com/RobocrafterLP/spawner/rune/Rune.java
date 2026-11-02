package com.RobocrafterLP.spawner.rune;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record Rune(String id, String name, String description, Material icon, String color, List<String> details,
                   String type, boolean enabled, int minDelay, int maxDelay, int spawnCount, int maxEntities,
                   int activationRange, int rank, int maxStacks, int extraDropsPerStack, int extraExperiencePercentPerStack,
                   Map<String, Boolean> effects) {
    public boolean isUpgrade() { return "upgrade".equalsIgnoreCase(type) || rank > 0; }
    public boolean isTrait() { return "trait".equalsIgnoreCase(type) || "effect".equalsIgnoreCase(type) || !effects.isEmpty(); }
    public boolean isReclaim() { return "reclaim".equalsIgnoreCase(type); }
    public boolean isGlobal() { return "default".equalsIgnoreCase(type); }
    public boolean isStacking() { return "stacking".equalsIgnoreCase(type) || maxStacks > 1 || extraDropsPerStack > 0 || extraExperiencePercentPerStack > 0; }
}
