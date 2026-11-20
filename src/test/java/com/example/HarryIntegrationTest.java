package com.example;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import org.junit.Test;
import static org.junit.Assert.*;

public class HarryIntegrationTest {
    @Test public void bossBarIsVisibleOnlyWithinOneHundredBlocks() {
        Location boss = new Location(null, 10, 64, -20);
        assertTrue(HarryMonoController.inBossBarRange(boss, boss));
        assertTrue(HarryMonoController.inBossBarRange(boss, new Location(null, 110, 64, -20)));
        assertTrue(HarryMonoController.inBossBarRange(boss, new Location(null, 10, 164, -20)));
        assertFalse(HarryMonoController.inBossBarRange(boss, new Location(null, 110.01, 64, -20)));
        assertFalse(HarryMonoController.inBossBarRange(boss, new Location(null, 10, 164.01, -20)));
        assertFalse(HarryMonoController.inBossBarRange(boss, new Location(null, 90, 64, 60)));
    }

    @Test public void spinPullStartsAfterOneSecondAndStopsOutsideBoss() {
        assertFalse(HarryMonoController.spinPullEnabled(0));
        assertFalse(HarryMonoController.spinPullEnabled(19));
        assertTrue(HarryMonoController.spinPullEnabled(20));
        assertEquals(2.5, HarryMonoController.spinPullDistance(4, 1.5), 0);
        assertEquals(0, HarryMonoController.spinPullDistance(1, 1.5), 0);
    }

    @Test public void fastRamHitsVictimBetweenEndpoints() {
        assertTrue(HarryMonoController.crosses(new Location(null, 0, 0, 0), new Vector(5, 0, 0),
                new BoundingBox(2, 0, -.3, 2.6, 1.8, .3), .2));
        assertFalse(HarryMonoController.crosses(new Location(null, 0, 0, 0), new Vector(5, 0, 0),
                new BoundingBox(2, 0, 3, 2.6, 1.8, 4), .2));
    }
    @Test public void oilProjectileRadiusCannotPassBesideBlockCollisionShape() {
        org.bukkit.util.BoundingBox partialBlock = new BoundingBox(1, 0, 0, 1.5, .8, 1);
        assertTrue(partialBlock.clone().expand(.3).contains(new org.bukkit.util.Vector(1.6, .5, .5)));
    }
    @Test public void stunOnlyBlocksCoordinatesAndExpires() {
        HarryMonoController.Stuns stuns = new HarryMonoController.Stuns();
        java.util.UUID id = java.util.UUID.randomUUID();
        stuns.add(id, 10, 20);
        stuns.add(id, 11, 2);
        assertTrue(stuns.contains(id, 29));
        assertFalse(stuns.contains(id, 30));
        stuns.add(id, 30, 20);
        stuns.remove(id);
        assertFalse(stuns.contains(id, 31));
    }

    @Test public void teleportClearsStunWithoutResettingAttackHitHistory() {
        HarryMonoController.Stuns stuns = new HarryMonoController.Stuns();
        java.util.UUID id = java.util.UUID.randomUUID();
        HarryCombat.HitWindow ramHits = new HarryCombat.HitWindow();
        stuns.add(id, 1, 40);
        assertTrue(ramHits.allow(id, 2, 20));
        stuns.remove(id); // Teleport lifecycle must not clear this separate hit window.
        assertFalse(stuns.contains(id, 3));
        assertFalse(ramHits.allow(id, 3, 20));
    }
}
