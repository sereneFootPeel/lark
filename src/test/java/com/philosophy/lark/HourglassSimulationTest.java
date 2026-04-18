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

        for (int i = 0; i < 360; i++) {
            simulation.step(1.0 / 60.0,
                    new HourglassSimulation.Vector2(0.0, 1.0),
                    0.08,
                    new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.maxBoundaryViolation() <= 1.0E-9, "fluid should stay inside the hourglass");
        assertTrue(simulation.topFraction() < initialTopFraction - 0.20, "water should visibly drain into the lower chamber");
        assertTrue(simulation.centerOfMass().y() > initialCenterY + 0.20, "center of mass should move downward");
        assertTrue(simulation.throatOccupancy() > 0 || simulation.hasLitCellNear(0.0, 0.05, 0.12),
                "the throat should be used during draining");
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
            assertTrue(simulation.getLightCount() == 192);
            assertTrue(simulation.maxBoundaryViolation() <= 1.0E-6);
        });
    }

    @Test
    void longRunningDrainKeepsFluidAliveAndMovesItIntoTheLowerChamber() {
        HourglassSimulation simulation = new HourglassSimulation(192, 42L);

        for (int i = 0; i < 4_200; i++) {
            simulation.step(1.0 / 60.0,
                    new HourglassSimulation.Vector2(0.0, 1.0),
                    0.10,
                    new HourglassSimulation.Vector2(0.0, 1.0));
        }

        assertTrue(simulation.topFraction() < 0.20, "most of the fluid should reach the lower chamber over a long run");
        assertTrue(simulation.centerOfMass().y() > 0.35, "the mass center should stay in the lower chamber instead of freezing at the throat");
        assertTrue(simulation.hasLitCellNear(0.0, 0.45, 0.28), "the lower chamber should still contain visible fluid");
        assertTrue(simulation.averageParticleSpeed() > 0.003, "the long-run flow should still retain some motion");
        assertTrue(simulation.maxBoundaryViolation() <= 1.0E-6, "particles should stay inside the narrowed physical boundary");
    }

    @Test
    void mobileBalancedTuningKeepsVisualDrainMetricsStable() {
        HourglassSimulation simulation = new HourglassSimulation(
                192,
                42L,
                LarkController.QualityProfile.MOBILE_BALANCED.simulationTuning());

        for (int i = 0; i < 360; i++) {
            simulation.step(1.0 / 60.0, 0.0, 1.0, 0.08, 0.0, 1.0);
        }

        assertTrue(simulation.topFraction() < 0.20, "balanced mobile tuning should still drain a visible amount of fluid");
        assertTrue(simulation.throatOccupancy() > 0 || simulation.hasLitCellNear(0.0, 0.04, 0.12),
                "balanced mobile tuning should keep the throat visually active");
        assertTrue(Math.abs(simulation.centerOfMass().x()) < 0.08,
                "symmetric downward gravity should keep the mass roughly centered horizontally");
        assertTrue(simulation.maxBoundaryViolation() <= 1.0E-6,
                "balanced mobile tuning should keep particles inside the boundary");
        assertTrue(simulation.isolatedFastParticleCount(0.40, simulation.getLightSpacing() * 2.3) <= 10,
                "balanced mobile tuning should avoid isolated spray artifacts");
    }

    @Test
    void defaultTuningHandlesDefaultParticleBudgetWithinRealtimeBudget() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            HourglassSimulation simulation = new HourglassSimulation(
                    LarkController.DEFAULT_LIT_COUNT,
                    99L,
                    LarkController.simulationTuning());
            for (int i = 0; i < 900; i++) {
                double phase = i / 90.0;
                double gravityX = Math.sin(phase) * 0.52;
                double gravityY = 0.78 + Math.cos(phase * 0.35) * 0.12;
                simulation.step(1.0 / 60.0, gravityX, gravityY, 0.20, gravityX, gravityY);
            }
            assertTrue(simulation.averageParticleSpeed() > 0.0);
            assertTrue(simulation.maxBoundaryViolation() <= 1.0E-6);
        });
    }

    @Test
    void mobileBatterySaverAdaptsRelaxationIterationsToMotionDemand() {
        HourglassSimulation simulation = new HourglassSimulation(
                192,
                42L,
                LarkController.QualityProfile.MOBILE_BATTERY_SAVER.simulationTuning());

        simulation.step(1.0 / 60.0, 0.0, 1.0, 0.0, 0.0, 1.0);
        int calmIterations = simulation.lastRelaxationIterationsUsed();

        for (int i = 0; i < 24; i++) {
            simulation.step(1.0 / 60.0, 0.7, 0.7, 0.7, 0.7, 0.7);
        }
        int activeIterations = simulation.lastRelaxationIterationsUsed();

        assertTrue(calmIterations >= 1, "battery saver should always keep at least one pressure-relaxation pass");
        assertTrue(activeIterations >= calmIterations,
                "battery saver should not reduce relaxation work when agitation and speed increase");
        assertTrue(activeIterations > calmIterations,
                "battery saver should scale iterations up under stronger motion demand");
    }
}

