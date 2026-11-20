package com.example;

import org.junit.Test;

import static org.junit.Assert.*;

public class SkelebassBehaviorTest {
    @Test public void retreatStartsBelowDesiredDistanceMinusHalfBlock() {
        assertTrue(RockFrostPlugin.isRetreating(11.49, 12.0));
        assertFalse(RockFrostPlugin.isRetreating(11.5, 12.0));
        assertFalse(RockFrostPlugin.isRetreating(12.0, 12.0));
    }
}
