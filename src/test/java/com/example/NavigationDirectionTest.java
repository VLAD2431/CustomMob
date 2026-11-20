package com.example;

import org.junit.Test;

import static org.junit.Assert.*;

public class NavigationDirectionTest {
    @Test public void retainsPathOnlyWhileItsDestinationMakesProgress() {
        assertTrue(RockFrostPlugin.pathMakesProgress(16, 25, true));
        assertFalse(RockFrostPlugin.pathMakesProgress(16, 9, true));
        assertTrue(RockFrostPlugin.pathMakesProgress(16, 9, false));
        assertFalse(RockFrostPlugin.pathMakesProgress(16, 25, false));
        assertFalse(RockFrostPlugin.pathMakesProgress(16, 16, true));
        assertFalse(RockFrostPlugin.pathMakesProgress(16, 16, false));
    }
}
