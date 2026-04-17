package com.philosophy.lark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HourglassSimulationTest {
    @Test
    void litCellsRemainInsideDiamondHourglassAfterLongRun() {
        HourglassSimulation simulation = new HourglassSimulation(320, 7L);
        for (int step = 0; step < 900; step++) {
            simulation.step(1.0 / 60.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.maxBoundaryViolation() < 1.0E-9, "lit cells should stay inside the hourglass shell");
    }

    @Test
    void particleCountStaysStable() {
        HourglassSimulation simulation = new HourglassSimulation(512, 21L);
        for (int step = 0; step < 360; step++) {
            simulation.step(1.0 / 90.0, new HourglassSimulation.Vector2(0.35, 0.94));
        }

        assertEquals(512, simulation.getParticleCount());
    }

    @Test
    void tiltingGravityMovesTheFluidMassSideways() {
        HourglassSimulation leftTilt = new HourglassSimulation(280, 99L);
        HourglassSimulation rightTilt = new HourglassSimulation(280, 99L);

        for (int step = 0; step < 720; step++) {
            leftTilt.step(1.0 / 90.0, new HourglassSimulation.Vector2(-0.75, 0.7));
            rightTilt.step(1.0 / 90.0, new HourglassSimulation.Vector2(0.75, 0.7));
        }

        assertTrue(leftTilt.centerOfMass().x() < -0.03, "left tilt should pull the liquid mass to the left");
        assertTrue(rightTilt.centerOfMass().x() > 0.03, "right tilt should pull the liquid mass to the right");
    }

    @Test
    void bottomBulbEventuallyReceivesLight() {
        HourglassSimulation simulation = new HourglassSimulation(360, 5L);
        for (int step = 0; step < 4_200; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.centerOfMass().y() > 0.20,
                "downward flow should shift the liquid mass into the lower bulb");
        assertTrue(simulation.topFraction() < 0.60,
                "after a long run, a substantial portion of the water should have transferred into the lower bulb");
        assertTrue(simulation.throatOccupancy() <= 12,
                "the throat should remain a narrow transfer region rather than a full reservoir");
    }

    @Test
    void waterBeginsDrainingWithoutImmediatelyEmptyingTheTopBulb() {
        HourglassSimulation simulation = new HourglassSimulation(360, 5L);
        double initialTopFraction = simulation.topFraction();
        for (int step = 0; step < 720; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }

        double drained = initialTopFraction - simulation.topFraction();
        assertTrue(drained > 0.08,
                "steady downward gravity should start moving water out of the top bulb");
        assertTrue(drained < 0.80,
                "the top bulb should not empty unrealistically fast during the early flow period");
    }

    @Test
    void throatDevelopsAVisibleLiquidColumnDuringTransfer() {
        HourglassSimulation simulation = new HourglassSimulation(360, 5L);
        int maxOccupancy = 0;
        int occupiedSteps = 0;

        for (int step = 0; step < 1_800; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
            int occupancy = simulation.throatOccupancy();
            maxOccupancy = Math.max(maxOccupancy, occupancy);
            if (occupancy > 0) {
                occupiedSteps++;
            }
        }

        assertTrue(maxOccupancy >= 1,
                "as water transfers, the throat should occasionally contain visible liquid");
        assertTrue(maxOccupancy <= 14,
                "the narrow neck should not become an unrealistically wide solid block of particles");
        assertTrue(occupiedSteps > 60,
                "the neck should remain active for a noticeable portion of the transfer");
    }

    @Test
    void liquidStartsLeavingTheTopBulb() {
        HourglassSimulation simulation = new HourglassSimulation(360, 5L);
        double initialTopFraction = simulation.topFraction();
        for (int step = 0; step < 3_000; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.topFraction() < initialTopFraction - 0.05,
                "with sustained downward gravity, particles should start draining through the neck");
    }

    @Test
    void steadyGravityEventuallySettlesTheFluid() {
        HourglassSimulation simulation = new HourglassSimulation(320, 7L);
        for (int step = 0; step < 9_600; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.averageParticleSpeed() < 0.08,
                "long-running liquid motion should dissipate and settle instead of jittering forever");
        assertTrue(simulation.maxBoundaryViolation() < 1.0E-9,
                "settling should still keep particles inside the hourglass");
    }

    @Test
    void splashBurstCanSendSingleParticlesOutIndependently() {
        HourglassSimulation simulation = new HourglassSimulation(320, 11L);
        for (int step = 0; step < 480; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }

        int maxIsolatedFastParticles = 0;
        for (int step = 0; step < 180; step++) {
            HourglassSimulation.Vector2 gravity = step < 90
                    ? new HourglassSimulation.Vector2(0.86, 0.51)
                    : new HourglassSimulation.Vector2(0.18, 0.98);
            double agitation = step < 90 ? 1.0 : 0.55;
            HourglassSimulation.Vector2 inertia = new HourglassSimulation.Vector2(1.0, -0.18);
            simulation.step(1.0 / 120.0, gravity, agitation, inertia);
            maxIsolatedFastParticles = Math.max(maxIsolatedFastParticles,
                    simulation.isolatedFastParticleCount(0.55, simulation.getLightSpacing() * 1.10));
        }

        assertTrue(maxIsolatedFastParticles >= 1,
                "a sharp splash should be able to eject at least one fast particle without forcing a small cluster to move with it");
    }
}

