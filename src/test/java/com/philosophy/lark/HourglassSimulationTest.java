package com.philosophy.lark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class HourglassSimulationTest {
    private static final double FIXED_DT = 1.0 / 150.0;
    private static final int PARTICLE_COUNT = 350;
    private static final long SEED = 42L;

    @Test
    void horizontalGravityDoesNotPullParticlesIntoThroatOnRightTilt() {
        HourglassSimulation simulation = new HourglassSimulation(
                PARTICLE_COUNT,
                SEED,
                LarkController.QualityProfile.DESKTOP.simulationTuning());

        runSteps(simulation, 1.0, 0.0, 2_200);

        assertTrue(simulation.throatOccupancy() <= 10,
                () -> "throat occupancy too high: " + simulation.throatOccupancy());
        assertTrue(simulation.topFraction() >= 0.45,
                () -> "top fraction dropped unexpectedly: " + simulation.topFraction());
        assertTrue(simulation.maxBoundaryViolation() <= 1.0E-6,
                () -> "boundary violation too high: " + simulation.maxBoundaryViolation());
    }

    @Test
    void horizontalGravityDoesNotPullParticlesIntoThroatOnLeftTilt() {
        HourglassSimulation simulation = new HourglassSimulation(
                PARTICLE_COUNT,
                SEED,
                LarkController.QualityProfile.DESKTOP.simulationTuning());

        runSteps(simulation, -1.0, 0.0, 2_200);

        assertTrue(simulation.throatOccupancy() <= 10,
                () -> "throat occupancy too high: " + simulation.throatOccupancy());
        assertTrue(simulation.topFraction() >= 0.45,
                () -> "top fraction dropped unexpectedly: " + simulation.topFraction());
        assertTrue(simulation.maxBoundaryViolation() <= 1.0E-6,
                () -> "boundary violation too high: " + simulation.maxBoundaryViolation());
    }

    @Test
    void localVacuumZonePushesParticlesAwayFromTouchPoint() {
        HourglassSimulation probe = new HourglassSimulation(
                PARTICLE_COUNT,
                SEED,
                LarkController.QualityProfile.DESKTOP.simulationTuning());
        HourglassSimulation control = new HourglassSimulation(
                PARTICLE_COUNT,
                SEED,
                LarkController.QualityProfile.DESKTOP.simulationTuning());
        HourglassSimulation forced = new HourglassSimulation(
                PARTICLE_COUNT,
                SEED,
                LarkController.QualityProfile.DESKTOP.simulationTuning());

        DenseRegion region = findDenseUpperRegion(probe, 0.11);
        double vacuumX = region.centerX();
        double vacuumY = region.centerY();
        double vacuumRadius = 0.11;
        double vacuumEdgeWidth = 0.08;
        HourglassSimulation.RadialForce vacuumForce = new HourglassSimulation.RadialForce(
                vacuumX,
                vacuumY,
                vacuumRadius,
                vacuumEdgeWidth,
                3.4);

        int initialCount = region.count();

        runSteps(control, 0.0, 0.0, 180, null);
        runSteps(forced, 0.0, 0.0, 180, vacuumForce);

        int controlCount = control.particleCountNear(vacuumX, vacuumY, vacuumRadius);
        int forcedCount = forced.particleCountNear(vacuumX, vacuumY, vacuumRadius);

        assertTrue(initialCount >= 4,
                () -> "initial local density too low for vacuum test: " + initialCount);
        assertTrue(forcedCount + 1 <= controlCount,
                () -> "vacuum zone did not clear enough particles, control=" + controlCount + ", forced=" + forcedCount);
        assertTrue(forced.maxBoundaryViolation() <= 1.0E-6,
                () -> "boundary violation too high under vacuum force: " + forced.maxBoundaryViolation());
    }

    private static DenseRegion findDenseUpperRegion(HourglassSimulation simulation, double radius) {
        DenseRegion best = new DenseRegion(0.0, -0.55, -1);
        for (double y = -0.92; y <= -0.18; y += 0.05) {
            double halfWidth = HourglassSimulation.halfWidthAt(y);
            for (double x = -halfWidth * 0.78; x <= halfWidth * 0.78; x += 0.05) {
                int count = simulation.particleCountNear(x, y, radius);
                if (count > best.count()) {
                    best = new DenseRegion(x, y, count);
                }
            }
        }
        return best;
    }

    private static void runSteps(HourglassSimulation simulation, double gravityX, double gravityY, int steps) {
        runSteps(simulation, gravityX, gravityY, steps, null);
    }

    private static void runSteps(HourglassSimulation simulation, double gravityX, double gravityY,
                                 int steps, HourglassSimulation.RadialForce radialForce) {
        for (int i = 0; i < steps; i++) {
            simulation.step(FIXED_DT, gravityX, gravityY, 0.0, gravityX, gravityY, radialForce);
        }
    }

    private record DenseRegion(double centerX, double centerY, int count) {}
}

