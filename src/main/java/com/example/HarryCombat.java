package com.example;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-independent combat clocks and weighted selection. */
final class HarryCombat {
    enum Attack { RAM, OIL, SPIN, JUMP }
    Attack attack;
    boolean active;
    private int remaining;
    int remaining() { return remaining; }

    HarryCombat(int interval) { remaining = Math.max(1, interval); }
    boolean tick(boolean hasTarget) { return attack == null && hasTarget && --remaining <= 0; }
    void begin(Attack value, int ticks) { attack = value; active = false; remaining = Math.max(1, ticks); }
    void active(int ticks) { active = true; remaining = Math.max(1, ticks); }
    boolean advance() { return --remaining <= 0; }
    void finish(int interval) { attack = null; active = false; remaining = Math.max(1, interval); }

    static Attack choose(double[] weights, double roll) {
        double max = 0;
        for (double weight : weights) if (Double.isFinite(weight) && weight > max) max = weight;
        if (max == 0) return null;
        double total = 0;
        for (double weight : weights) if (Double.isFinite(weight) && weight > 0) total += weight / max;
        double cursor = Math.max(0, Math.min(Math.nextDown(1.0), roll)) * total;
        for (int i = 0; i < Math.min(weights.length, Attack.values().length); i++) {
            double weight = weights[i];
            if (!Double.isFinite(weight) || weight <= 0) continue;
            cursor -= weight / max;
            if (cursor < 0) return Attack.values()[i];
        }
        return null;
    }

    static final class HitWindow {
        private final Map<UUID, Long> next = new HashMap<>();
        boolean allow(UUID id, long now, int period) {
            if (now < next.getOrDefault(id, Long.MIN_VALUE)) return false;
            next.put(id, now + Math.max(1, period));
            return true;
        }
        void remove(UUID id) { next.remove(id); }
    }
}
