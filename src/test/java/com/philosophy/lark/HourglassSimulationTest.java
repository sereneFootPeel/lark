package com.philosophy.lark;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class HourglassSimulationTest {
    @Test
    void downwardGravityMovesWaterTowardBottomAndKeepsItInsideTheHourglass() {
        HourglassSimulation simulation = new HourglassSimulation(192, 42L);
        double initialTopFraction = simulation.topFraction();
        double initialCenterY = simulation.centerOfMass().y();

        for (int i = 0; i < 480; i++) {
            simulation.step(1.0 / 60.0,
                    new HourglassSimulation.Vector2(0.0, 1.0),
                    0.08,
                    new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.maxBoundaryViolation() <= 1.0E-9, "fluid should stay inside the hourglass");
        assertTrue(simulation.topFraction() < initialTopFraction - 0.08, "water should visibly drain into the lower chamber");
        assertTrue(simulation.centerOfMass().y() > initialCenterY + 0.08, "center of mass should move downward");
        assertTrue(simulation.throatOccupancy() > 0, "the throat should be used during draining");
    }

    @Test
    void lateralTiltPushesTheWaterSideways() {
        HourglassSimulation simulation = new HourglassSimulation(192, 7L);
        double initialCenterX = simulation.centerOfMass().x();

        for (int i = 0; i < 360; i++) {
            simulation.step(1.0 / 60.0,
                    new HourglassSimulation.Vector2(0.72, 0.69),
                    0.12,
                    new HourglassSimulation.Vector2(0.72, 0.69));
        }

        assertTrue(simulation.centerOfMass().x() > initialCenterX + 0.03, "tilting right should shift the mass center rightward");
        assertTrue(simulation.hasLitCellNear(0.18, -0.25, 0.24), "water should accumulate near the tilted side");
    }

    @Test
    void simulationLoopCompletesWithinRealtimeBudget() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            HourglassSimulation simulation = new HourglassSimulation(192, 99L);
            for (int i = 0; i < 720; i++) {
                double phase = i / 120.0;
                HourglassSimulation.Vector2 gravity = new HourglassSimulation.Vector2(Math.sin(phase) * 0.45, 0.90);
                simulation.step(1.0 / 60.0, gravity, 0.18, gravity);
            }
            assertTrue(Double.isFinite(simulation.averageParticleSpeed()));
            assertTrue(simulation.getLightCount() > 200);
        });
    }
}

