package com.example;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class HarryCombatTest {
    @Test public void weightedBoundariesAndDisabledAttacks() {
        double[] weights = {4, 3, 1, 2};
        assertEquals(HarryCombat.Attack.RAM, HarryCombat.choose(weights, 0));
        assertEquals(HarryCombat.Attack.OIL, HarryCombat.choose(weights, .4));
        assertEquals(HarryCombat.Attack.SPIN, HarryCombat.choose(weights, .7));
        assertEquals(HarryCombat.Attack.JUMP, HarryCombat.choose(weights, .999));
        assertEquals(HarryCombat.Attack.OIL, HarryCombat.choose(new double[]{0, 1, 0, 0}, 0));
        assertNull(HarryCombat.choose(new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}, .5));
        assertEquals(HarryCombat.Attack.JUMP, HarryCombat.choose(new double[]{Double.MAX_VALUE, 0, 0, Double.MAX_VALUE}, .75));
    }

    @Test public void intervalPausesAndRestartsAfterFullAttack() {
        HarryCombat combat = new HarryCombat(2);
        assertFalse(combat.tick(false));
        assertFalse(combat.tick(true));
        assertTrue(combat.tick(true));
        combat.begin(HarryCombat.Attack.RAM, 20);
        for (int i = 0; i < 19; i++) assertFalse(combat.advance());
        assertTrue(combat.advance());
        combat.active(3);
        assertFalse(combat.advance());
        assertFalse(combat.advance());
        assertTrue(combat.advance());
        combat.finish(2);
        assertNull(combat.attack);
        assertFalse(combat.tick(true));
        assertTrue(combat.tick(true));
        combat.begin(HarryCombat.Attack.RAM, 1); // consecutive repetitions permitted
        assertEquals(HarryCombat.Attack.RAM, combat.attack);
    }

    @Test public void hitWindowsPreventDuplicateAndExpireExactly() {
        HarryCombat.HitWindow hits = new HarryCombat.HitWindow();
        UUID player = UUID.randomUUID();
        assertTrue(hits.allow(player, 10, 20));
        assertFalse(hits.allow(player, 10, 20));
        assertFalse(hits.allow(player, 29, 20));
        assertTrue(hits.allow(player, 30, 20));
        hits.remove(player);
        assertTrue(hits.allow(player, 30, 20));
    }
}
