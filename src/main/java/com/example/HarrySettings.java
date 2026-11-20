package com.example;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/** A deep snapshot: reload affects only subsequent attacks. */
final class HarrySettings {
    final ConfigurationSection config;
    HarrySettings(ConfigurationSection source) {
        MemoryConfiguration copy = new MemoryConfiguration();
        if (source != null) source.getValues(true).forEach((key, value) -> {
            if (!(value instanceof ConfigurationSection)) copy.set(key, value);
        });
        config = copy;
    }
    double number(String key, double fallback, double min, double max) {
        double value = config.getDouble(key, fallback);
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    int ticks(String key, int fallback) { return (int) number(key, fallback, 1, 12000); }
    double[] weights() {
        return new double[]{weight("ram"), weight("oil-bomb"), weight("spin"), weight("jump")};
    }
    private double weight(String name) {
        double value = config.getDouble(name + ".weight", 1);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
