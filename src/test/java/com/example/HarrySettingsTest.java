package com.example;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class HarrySettingsTest {
    @Test public void snapshotsSurviveReloadAndClampUnsafeNumbers() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("ram.weight", 4);
        config.set("spin.weight", Double.NaN);
        config.set("jump.weight", -5);
        config.set("ram.speed-per-tick", Double.POSITIVE_INFINITY);
        config.set("spin.duration-ticks", -100);
        HarrySettings settings = new HarrySettings(config);
        config.set("ram.weight", 100);
        assertArrayEquals(new double[]{4, 1, 0, 0}, settings.weights(), 0);
        assertEquals(2.5, settings.number("ram.speed-per-tick", 2.5, 0, 8), 0);
        assertEquals(1, settings.ticks("spin.duration-ticks", 200));
        assertEquals(12000, settings.ticks("missing", 20000));
    }
    @Test public void missingSectionStillEnablesAllFourAttacks() {
        assertArrayEquals(new double[]{1, 1, 1, 1}, new HarrySettings(null).weights(), 0);
    }
    @Test public void bundledConfigDrivesCombatAndSnapshotsEffects() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config;
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(
                getClass().getResourceAsStream("/config.yml")), java.nio.charset.StandardCharsets.UTF_8)) {
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
        }
        HarrySettings settings = new HarrySettings(config.getConfigurationSection("mobs.harry_mono"));
        assertEquals("SQUID_INK", ((java.util.Map<?, ?>) settings.config.getMapList("particles.oil-flight").get(0)).get("type"));
        HarryCombat combat = new HarryCombat(settings.ticks("special-attack-interval-ticks", 1));
        for (int i = 0; i < settings.ticks("special-attack-interval-ticks", 1) - 1; i++) assertFalse(combat.tick(true));
        assertTrue(combat.tick(true));
        assertFalse(settings.config.getMapList("particles.oil-pool").isEmpty());
        config.set("mobs.harry_mono.particles.oil-pool", java.util.List.of());
        assertFalse(settings.config.getMapList("particles.oil-pool").isEmpty());
        combat.begin(HarryCombat.Attack.SPIN, settings.ticks("spin.windup-ticks", 1));
        for (int i = 0; i < 19; i++) assertFalse(combat.advance());
        assertTrue(combat.advance());
        combat.active(settings.ticks("spin.duration-ticks", 1));
        for (int i = 0; i < 199; i++) assertFalse(combat.advance());
        assertTrue(combat.advance());
    }
    @Test public void commonMobAttributesUseFiniteSafeFallbacksForHarry() {
        org.bukkit.configuration.MemoryConfiguration root = new org.bukkit.configuration.MemoryConfiguration();
        root.set("max-health", Double.NaN);
        root.set("movement-speed", Double.POSITIVE_INFINITY);
        HarrySettings settings = new HarrySettings(root);
        assertEquals(200, settings.number("max-health", 200, 1, 2048), 0);
        assertEquals(.32, settings.number("movement-speed", .32, 0, 1.2), 0);
    }
}
