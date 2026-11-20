package com.example;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectileMathTest {
    @Test public void highArcReachesTargetWithDiscreteGravity() {
        double gravity = .03;
        double[] velocity = ProjectileMath.highArc(12, 0, 0, 1.1, gravity);
        double time = 12 / velocity[0];
        assertTrue(velocity[1] > velocity[0]);
        assertEquals(0, velocity[1] * time - gravity * time * (time + 1) / 2, 1e-8);
    }

    @Test public void nearbyTargetUsesHigherArcAndStillHits() {
        double gravity = .03;
        double[] velocity = ProjectileMath.highArc(12, 0, 0, 1.1, gravity);
        double time = 12 / velocity[0];
        assertTrue((velocity[1] - gravity / 2) / velocity[0] > Math.sqrt(3));
        assertEquals(0, velocity[1] * time - gravity * time * (time + 1) / 2, 1e-8);
        assertTrue(Math.hypot(velocity[0], velocity[1] - gravity / 2) <= 1.1 + 1e-9);
    }

    @Test public void elevatedTargetStillHitsWithinSpeedLimit() {
        double gravity = .03;
        double[] velocity = ProjectileMath.highArc(20, 8, 10, 1.1, gravity);
        double time = 20 / velocity[0];
        assertEquals(10, velocity[2] * time, 1e-8);
        assertEquals(8, velocity[1] * time - gravity * time * (time + 1) / 2, 1e-8);
        assertTrue(Math.sqrt(velocity[0] * velocity[0] + velocity[2] * velocity[2]
                + Math.pow(velocity[1] - gravity / 2, 2)) <= 1.1 + 1e-9);
    }

    @Test public void unreachableTargetUsesFiniteFallback() {
        double[] velocity = ProjectileMath.highArc(1000, 0, 0, 1.1, .03);
        assertTrue(Double.isFinite(velocity[0]));
        assertTrue(velocity[1] > 0);
        assertEquals(1.1, Math.hypot(velocity[0], velocity[1] - .015), 1e-9);
    }

    @Test public void zeroGravityUsesDirectDirection() {
        double[] velocity = ProjectileMath.highArc(3, 4, 0, 1, 0);
        assertEquals(.6, velocity[0], 1e-9);
        assertEquals(.8, velocity[1], 1e-9);
    }

    @Test public void verticalAndZeroSpeedAreFinite() {
        assertTrue(ProjectileMath.highArc(0, 5, 0, 1, .03)[1] > 0);
        assertArrayEquals(new double[] {0, 0, 0}, ProjectileMath.highArc(1, 0, 0, 0, .03), 0);
    }
}
