package com.nightfall.riftautotune.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * A complete set of knob levels. Mutable, cheap to copy. The adapters translate a resolved
 * {@code GraphicsSettings} into real config writes; the optimizer produces it.
 */
public final class GraphicsSettings {

    private final int[] levels = new int[Knob.values().length];

    public GraphicsSettings() {
        // default: everything at level 0 (lowest). Presets raise from here.
    }

    private GraphicsSettings(int[] source) {
        System.arraycopy(source, 0, levels, 0, levels.length);
    }

    public int get(Knob knob) {
        return levels[knob.ordinal()];
    }

    /** The real value for a knob at its current level. */
    public int value(Knob knob) {
        return knob.valueAt(get(knob));
    }

    public GraphicsSettings set(Knob knob, int level) {
        levels[knob.ordinal()] = knob.clampLevel(level);
        return this;
    }

    /** Set by the knob's real value, snapping to the nearest defined level (used when reading
     *  back what a mod currently has configured). */
    public GraphicsSettings setByValue(Knob knob, int value) {
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int lvl = 0; lvl <= knob.maxLevel(); lvl++) {
            int dist = Math.abs(knob.valueAt(lvl) - value);
            if (dist < bestDist) {
                bestDist = dist;
                best = lvl;
            }
        }
        return set(knob, best);
    }

    public GraphicsSettings copy() {
        return new GraphicsSettings(levels);
    }

    public Map<Knob, Integer> asLevelMap() {
        Map<Knob, Integer> map = new EnumMap<>(Knob.class);
        for (Knob k : Knob.values()) {
            map.put(k, get(k));
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphicsSettings other)) return false;
        return java.util.Arrays.equals(levels, other.levels);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(levels);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GraphicsSettings{");
        boolean first = true;
        for (Knob k : Knob.values()) {
            if (!first) sb.append(", ");
            sb.append(k.name()).append('=').append(get(k)).append("(v").append(value(k)).append(')');
            first = false;
        }
        return sb.append('}').toString();
    }
}
