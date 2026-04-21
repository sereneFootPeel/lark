package com.philosophy.lark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class HourglassSimulationSettlingTest {

    @Test
    void settlingDampingAlsoAppliesForHorizontalGravity() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(3, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double particleRadius = getDoubleField(simulation, "particleRadius");
        double yShift = 0.08;
        double[][] points = {
                {0.0, yShift},
                {particleRadius * 0.45, yShift + particleRadius * 0.25},
                {particleRadius * 0.45, yShift - particleRadius * 0.25}
        };
        arrangeParticles(simulation, points);
        setVelocity(simulation, 0, 0.03, 0.02);

        invokeBuildSpatialIndex(simulation);
        invokeDampenMotion(simulation, 0, 1.0, 0.0, 1.0, 0.0);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        assertTrue(velocityX[0] > 0.0 && velocityX[0] <= 0.005, "horizontal settling should strongly reduce vx");
        assertEquals(0.0, velocityY[0], 1.0E-12);
    }

    @Test
    void settlingSupportFollowsDiagonalGravityDirection() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(3, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double particleRadius = getDoubleField(simulation, "particleRadius");
        double offset = particleRadius * 0.45;
        double yShift = 0.08;
        double[][] points = {
                {0.0, yShift},
                {offset, yShift},
                {0.0, yShift + offset}
        };
        arrangeParticles(simulation, points);
        setVelocity(simulation, 0, 0.03, 0.02);

        invokeBuildSpatialIndex(simulation);
        double diagonal = Math.sqrt(0.5);
        invokeDampenMotion(simulation, 0, diagonal, diagonal, 1.0, 0.0);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        assertTrue(velocityX[0] > 0.0 && velocityX[0] <= 0.005, "diagonal settling should reduce vx");
        assertEquals(0.0, velocityY[0], 1.0E-12);
    }

    @Test
    void weakGravityKeepsOnlyContactDamping() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(3, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double particleRadius = getDoubleField(simulation, "particleRadius");
        double yShift = 0.08;
        double[][] points = {
                {0.0, yShift},
                {particleRadius * 0.45, yShift + particleRadius * 0.25},
                {particleRadius * 0.45, yShift - particleRadius * 0.25}
        };
        arrangeParticles(simulation, points);
        setVelocity(simulation, 0, 0.03, 0.02);

        invokeBuildSpatialIndex(simulation);
        invokeDampenMotion(simulation, 0, 0.1, 0.0, 0.1, 0.0);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        assertEquals(0.03 * 0.86, velocityX[0], 1.0E-12);
        assertEquals(0.02 * 0.86, velocityY[0], 1.0E-12);
    }

    @Test
    void throatSlowdownAppliesRegardlessOfPassageDirection() throws Exception {
        double[][] directions = {
                {0.05, 0.0},
                {-0.05, 0.0},
                {0.0, 0.05},
                {0.0, -0.05},
                {0.04, 0.03},
                {-0.04, -0.03}
        };

        for (double[] direction : directions) {
            HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
            arrangeParticles(simulation, new double[][] {{0.0, 0.0}});
            setVelocity(simulation, 0, direction[0], direction[1]);

            invokeBuildSpatialIndex(simulation);
            invokeDampenMotion(simulation, 0, 0.0, 0.0, 0.0, 0.0);

            double[] velocityX = getDoubleArrayField(simulation, "velocityX");
            double[] velocityY = getDoubleArrayField(simulation, "velocityY");
            double beforeSpeed = Math.hypot(direction[0], direction[1]);
            double afterSpeed = Math.hypot(velocityX[0], velocityY[0]);
            assertTrue(afterSpeed < beforeSpeed, "throat slowdown should reduce speed for direction " + direction[0] + "," + direction[1]);
            assertSameDirection(direction[0], velocityX[0]);
            assertSameDirection(direction[1], velocityY[0]);
        }
    }

    @Test
    void throatSlowdownStaysLocalToNeckRegion() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        arrangeParticles(simulation, new double[][] {{0.0, 0.12}});
        setVelocity(simulation, 0, 0.04, -0.03);

        invokeBuildSpatialIndex(simulation);
        invokeDampenMotion(simulation, 0, 0.0, 0.0, 0.0, 0.0);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        assertEquals(0.04, velocityX[0], 1.0E-12);
        assertEquals(-0.03, velocityY[0], 1.0E-12);
    }

    @Test
    void sideWallFrictionOnlyDampsUpwardClimbingAcrossBoundaryPaths() throws Exception {
        double[] ySamples = {-0.80, -0.40, 0.40, 0.80};
        double[] sides = {-1.0, 1.0};

        for (boolean viaClamp : new boolean[] {true, false}) {
            for (double y : ySamples) {
                for (double side : sides) {
                    assertSideWallResponse(viaClamp, y, side, 0.0, 1.0, -0.08, true,
                            "upward wall motion should be damped");
                }
            }
        }
    }

    @Test
    void verticalBoundaryBouncePreservesVerticalSpeedAcrossBoundaryPaths() throws Exception {
        for (boolean viaClamp : new boolean[] {true, false}) {
            assertVerticalBoundaryResponse(viaClamp, true, -0.07,
                    "top boundary bounce should preserve |vy|");
            assertVerticalBoundaryResponse(viaClamp, false, 0.07,
                    "bottom boundary bounce should preserve |vy|");
        }
    }

    @Test
    void sideWallFrictionLeavesDownwardSlidingUntouchedAcrossBoundaryPaths() throws Exception {
        double[] ySamples = {-0.80, -0.40, 0.40, 0.80};
        double[] sides = {-1.0, 1.0};

        for (boolean viaClamp : new boolean[] {true, false}) {
            for (double y : ySamples) {
                for (double side : sides) {
                    assertSideWallResponse(viaClamp, y, side, 0.0, 1.0, 0.08, false,
                            "downward wall motion should stay unchanged");
                }
            }
        }
    }

    @Test
    void sideWallFrictionUsesCurrentGravityDirectionInsteadOfWorldUp() throws Exception {
        double[] ySamples = {-0.80, 0.40};
        double[] sides = {-1.0, 1.0};

        for (boolean viaClamp : new boolean[] {true, false}) {
            for (double y : ySamples) {
                for (double side : sides) {
                    assertSideWallResponse(viaClamp, y, side, 0.0, -1.0, -0.08, true,
                            "reversed gravity should damp the opposite wall direction");
                    assertSideWallResponse(viaClamp, y, side, 0.0, -1.0, 0.08, false,
                            "reversed gravity should leave downhill wall motion unchanged");
                }
            }
        }
    }

    @Test
    void sideWallFrictionAlsoFollowsDiagonalGravityProjection() throws Exception {
        double diagonalX = 1.0 / Math.sqrt(5.0);
        double diagonalY = 2.0 / Math.sqrt(5.0);

        for (boolean viaClamp : new boolean[] {true, false}) {
            assertSideWallResponse(viaClamp, -0.40, -1.0, diagonalX, diagonalY, -0.08, true,
                    "diagonal gravity should damp climbing against its wall projection");
            assertSideWallResponse(viaClamp, 0.80, 1.0, diagonalX, diagonalY, 0.08, false,
                    "diagonal gravity should keep downhill wall motion unchanged");
        }
    }

    @Test
    void sideWallFrictionTurnsOffWhenGravityProvidesNoWallDirection() throws Exception {
        for (boolean viaClamp : new boolean[] {true, false}) {
            assertSideWallResponse(viaClamp, -0.40, 1.0, 0.0, 0.0, -0.08, false,
                    "zero gravity should not define uphill along the wall");
        }
    }

    @Test
    void sideWallFrictionAlsoBlocksUphillMotionDuringPersistentWallContact() throws Exception {
        double[] sides = {-1.0, 1.0};

        for (double side : sides) {
            assertPersistentSideWallResponse(0.40, side, 0.0, 1.0, -0.08, true,
                    "persistent wall contact should still block uphill motion");
            assertPersistentSideWallResponse(0.80, side, 0.0, 1.0, 0.08, false,
                    "persistent wall contact should keep downhill motion unchanged");
        }
    }

    @Test
    void uphillPredictedWallDisplacementStaysInitiallyUnchangedRightAfterGravityShift() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double gravityX = 0.0;
        double gravityY = -1.0;
        double y = 0.80;
        double side = 1.0;
        double wallX = sideWallXAt(simulation, y, side);
        arrangeParticles(simulation, new double[][] {{wallX, y}});

        invokeUpdateSideWallUphillTransition(simulation, 0.0, 1.0);
        invokeUpdateSideWallUphillTransition(simulation, gravityX, gravityY);

        double[] downhillTangent = gravityAlignedWallTangent(y, side, gravityX, gravityY);
        double uphillDisplacement = -0.03;
        setPredictedPosition(simulation, 0,
                wallX + downhillTangent[0] * uphillDisplacement,
                y + downhillTangent[1] * uphillDisplacement);

        invokeConstrainToBoundary(simulation, 0, 0.0, gravityX, gravityY);

        double[] predictedX = getDoubleArrayField(simulation, "predictedX");
        double[] predictedY = getDoubleArrayField(simulation, "predictedY");
        double actualTangentialDisplacement = dot(predictedX[0] - wallX, predictedY[0] - y,
                downhillTangent[0], downhillTangent[1]);
        assertEquals(uphillDisplacement, actualTangentialDisplacement, 1.0E-12,
                "predicted uphill wall displacement should remain initially unchanged right after gravity changes");
    }

    @Test
    void uphillPredictedWallDisplacementIsRemovedAfterTransitionSettles() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double gravityX = 0.0;
        double gravityY = -1.0;
        double y = 0.80;
        double side = 1.0;
        double wallX = sideWallXAt(simulation, y, side);
        arrangeParticles(simulation, new double[][] {{wallX, y}});

        invokeUpdateSideWallUphillTransition(simulation, 0.0, 1.0);
        invokeUpdateSideWallUphillTransition(simulation, gravityX, gravityY);
        double fixedTick = getDoubleField(simulation, "fixedTick");
        int transitionSteps = (int) Math.ceil(getStaticDoubleField("SIDE_WALL_UPHILL_GRACE_SECONDS") / fixedTick);
        for (int i = 0; i < transitionSteps; i++) {
            invokeAdvanceSideWallUphillTransition(simulation);
        }

        double[] downhillTangent = gravityAlignedWallTangent(y, side, gravityX, gravityY);
        setPredictedPosition(simulation, 0,
                wallX + downhillTangent[0] * -0.03,
                y + downhillTangent[1] * -0.03);

        invokeConstrainToBoundary(simulation, 0, 0.0, gravityX, gravityY);

        double[] predictedX = getDoubleArrayField(simulation, "predictedX");
        double[] predictedY = getDoubleArrayField(simulation, "predictedY");
        double actualTangentialDisplacement = dot(predictedX[0] - wallX, predictedY[0] - y,
                downhillTangent[0], downhillTangent[1]);
        assertEquals(0.0, actualTangentialDisplacement, 1.0E-12,
                "predicted uphill wall displacement should be removed after the gravity-change transition has fully settled");
    }

    @Test
    void sideWallFrictionDoesNotInstantlyZeroWhenGravityJustChanged() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double y = 0.80;
        double side = 1.0;
        double gravityX = 0.0;
        double gravityY = -1.0;
        double tangentialSpeed = -0.08;
        double wallX = sideWallXAt(simulation, y, side);
        arrangeParticles(simulation, new double[][] {{wallX, y}});

        invokeUpdateSideWallUphillTransition(simulation, 0.0, 1.0);
        invokeUpdateSideWallUphillTransition(simulation, gravityX, gravityY);

        double[] downhillTangent = gravityAlignedWallTangent(y, side, gravityX, gravityY);
        setVelocity(simulation, 0,
                downhillTangent[0] * tangentialSpeed,
                downhillTangent[1] * tangentialSpeed);

        invokeConstrainVelocityAgainstBoundary(simulation, 0.0, gravityX, gravityY);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        double actualTangential = dot(velocityX[0], velocityY[0], downhillTangent[0], downhillTangent[1]);
        assertEquals(tangentialSpeed, actualTangential, 1.0E-12,
                "uphill wall motion should remain initially unchanged right after gravity changes");
    }

    @Test
    void sideWallFrictionDecaysToZeroAfterGravityHasBeenStableForTransitionWindow() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double y = 0.80;
        double side = 1.0;
        double gravityX = 0.0;
        double gravityY = -1.0;
        double tangentialSpeed = -0.08;
        double wallX = sideWallXAt(simulation, y, side);
        arrangeParticles(simulation, new double[][] {{wallX, y}});

        invokeUpdateSideWallUphillTransition(simulation, 0.0, 1.0);
        invokeUpdateSideWallUphillTransition(simulation, gravityX, gravityY);

        double fixedTick = getDoubleField(simulation, "fixedTick");
        int transitionSteps = (int) Math.ceil(getStaticDoubleField("SIDE_WALL_UPHILL_GRACE_SECONDS") / fixedTick);
        for (int i = 0; i < transitionSteps; i++) {
            invokeAdvanceSideWallUphillTransition(simulation);
        }

        double[] downhillTangent = gravityAlignedWallTangent(y, side, gravityX, gravityY);
        setVelocity(simulation, 0,
                downhillTangent[0] * tangentialSpeed,
                downhillTangent[1] * tangentialSpeed);

        invokeConstrainVelocityAgainstBoundary(simulation, 0.0, gravityX, gravityY);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        double actualTangential = dot(velocityX[0], velocityY[0], downhillTangent[0], downhillTangent[1]);
        assertEquals(0.0, actualTangential, 1.0E-12,
                "uphill wall motion should decay fully to zero after gravity has stayed changed for the full transition window");
    }

    @Test
    void tinyGravityDirectionChangeAlsoResetsSideWallGraceWindow() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());

        invokeUpdateSideWallUphillTransition(simulation, 0.0, -1.0);
        invokeAdvanceSideWallUphillTransition(simulation);
        invokeAdvanceSideWallUphillTransition(simulation);
        assertTrue(getDoubleField(simulation, "sideWallUphillTransitionElapsed") > 0.0,
                "test setup should move the grace window partway forward before the tiny tilt");

        invokeUpdateSideWallUphillTransition(simulation, 1.0E-4, -1.0);

        assertEquals(0.0, getDoubleField(simulation, "sideWallUphillTransitionElapsed"), 1.0E-12,
                "even a tiny gravity-direction change should reset the side-wall grace window");
    }

    @Test
    void sameDirectionGravityStrengthChangeDoesNotResetSideWallGraceWindow() throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());

        invokeUpdateSideWallUphillTransition(simulation, 0.0, -1.0);
        invokeAdvanceSideWallUphillTransition(simulation);
        invokeAdvanceSideWallUphillTransition(simulation);
        double elapsedBefore = getDoubleField(simulation, "sideWallUphillTransitionElapsed");

        invokeUpdateSideWallUphillTransition(simulation, 0.0, -0.25);

        assertEquals(elapsedBefore, getDoubleField(simulation, "sideWallUphillTransitionElapsed"), 1.0E-12,
                "changing gravity strength without changing direction should not reset the side-wall grace window");
    }

    private static void arrangeParticles(HourglassSimulation simulation, double[][] points) throws Exception {
        double[] positionX = getDoubleArrayField(simulation, "positionX");
        double[] positionY = getDoubleArrayField(simulation, "positionY");
        double[] predictedX = getDoubleArrayField(simulation, "predictedX");
        double[] predictedY = getDoubleArrayField(simulation, "predictedY");
        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");

        for (int i = 0; i < points.length; i++) {
            positionX[i] = points[i][0];
            positionY[i] = points[i][1];
            predictedX[i] = points[i][0];
            predictedY[i] = points[i][1];
            velocityX[i] = 0.0;
            velocityY[i] = 0.0;
        }
    }

    private static void setVelocity(HourglassSimulation simulation, int index, double vx, double vy) throws Exception {
        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        velocityX[index] = vx;
        velocityY[index] = vy;
    }

    private static void setPredictedPosition(HourglassSimulation simulation, int index, double x, double y) throws Exception {
        double[] predictedX = getDoubleArrayField(simulation, "predictedX");
        double[] predictedY = getDoubleArrayField(simulation, "predictedY");
        predictedX[index] = x;
        predictedY[index] = y;
    }

    private static void invokeBuildSpatialIndex(HourglassSimulation simulation) throws Exception {
        Method method = HourglassSimulation.class.getDeclaredMethod("buildSpatialIndex", double[].class, double[].class);
        method.setAccessible(true);
        method.invoke(simulation,
                getDoubleArrayField(simulation, "predictedX"),
                getDoubleArrayField(simulation, "predictedY"));
    }

    private static void invokeDampenMotion(HourglassSimulation simulation, int index,
                                           double gravityX, double gravityY,
                                           double gravityMagnitude, double agitation) throws Exception {
        Method method = HourglassSimulation.class.getDeclaredMethod(
                "dampenMotion", int.class, double.class, double.class, double.class, double.class);
        method.setAccessible(true);
        method.invoke(simulation, index, gravityX, gravityY, gravityMagnitude, agitation);
    }

    private static void invokeConstrainToBoundary(HourglassSimulation simulation, int index, double agitation,
                                                  double gravityX, double gravityY) throws Exception {
        Method method = HourglassSimulation.class.getDeclaredMethod(
                "constrainToBoundary", int.class, double[].class, double[].class, boolean.class,
                double.class, double.class, double.class);
        method.setAccessible(true);
        method.invoke(simulation, index,
                getDoubleArrayField(simulation, "predictedX"),
                getDoubleArrayField(simulation, "predictedY"),
                true,
                agitation,
                gravityX,
                gravityY);
    }

    private static void invokeConstrainVelocityAgainstBoundary(HourglassSimulation simulation, double agitation,
                                                               double gravityX, double gravityY) throws Exception {
        Method method = HourglassSimulation.class.getDeclaredMethod(
                "constrainVelocityAgainstBoundary", int.class, double.class, double.class, double.class);
        method.setAccessible(true);
        method.invoke(simulation, 0, agitation, gravityX, gravityY);
    }

    private static void invokeUpdateSideWallUphillTransition(HourglassSimulation simulation,
                                                             double gravityX, double gravityY) throws Exception {
        Method method = HourglassSimulation.class.getDeclaredMethod(
                "updateSideWallUphillTransition", double.class, double.class);
        method.setAccessible(true);
        method.invoke(simulation, gravityX, gravityY);
    }

    private static void invokeAdvanceSideWallUphillTransition(HourglassSimulation simulation) throws Exception {
        Method method = HourglassSimulation.class.getDeclaredMethod("advanceSideWallUphillTransition");
        method.setAccessible(true);
        method.invoke(simulation);
    }


    private static double[] getDoubleArrayField(HourglassSimulation simulation, String fieldName) throws Exception {
        Field field = HourglassSimulation.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (double[]) field.get(simulation);
    }

    private static double getDoubleField(HourglassSimulation simulation, String fieldName) throws Exception {
        Field field = HourglassSimulation.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(simulation);
    }

    private static double getStaticDoubleField(String fieldName) throws Exception {
        Field field = HourglassSimulation.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(null);
    }

    private static double sideWallXAt(HourglassSimulation simulation, double y, double side) throws Exception {
        return side * (HourglassSimulation.halfWidthAt(y) - getDoubleField(simulation, "boundaryPadding"));
    }

    private static double[] gravityAlignedWallTangent(double y, double side, double gravityX, double gravityY) {
        double slope = halfWidthSlopeAt(y);
        double tangentX = side * slope;
        double tangentY = 1.0;
        double length = Math.hypot(tangentX, tangentY);
        tangentX /= length;
        tangentY /= length;

        double gravityProjection = dot(gravityX, gravityY, tangentX, tangentY);
        if (Math.abs(gravityProjection) < 1.0E-12) {
            return null;
        }
        if (gravityProjection < 0.0) {
            tangentX = -tangentX;
            tangentY = -tangentY;
        }
        return new double[] {tangentX, tangentY};
    }

    private static double halfWidthSlopeAt(double y) {
        double absY = Math.abs(y);
        if (absY <= HourglassSimulation.THROAT_HALF_WIDTH) {
            return 0.0;
        }
        if (y < 0.0) {
            return y <= -HourglassSimulation.DIAMOND_RADIUS ? 1.0 : -1.0;
        }
        return y <= HourglassSimulation.DIAMOND_RADIUS ? 1.0 : -1.0;
    }

    private static double dot(double ax, double ay, double bx, double by) {
        return ax * bx + ay * by;
    }

    private static void assertSideWallResponse(boolean viaClamp, double y, double side,
                                               double gravityX, double gravityY,
                                               double tangentialSpeed, boolean expectDamped,
                                               String message) throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double wallX = sideWallXAt(simulation, y, side);
        arrangeParticles(simulation, new double[][] {{wallX, y}});

        double[] downhillTangent = gravityAlignedWallTangent(y, side, gravityX, gravityY);
        double bouncedVX;
        double bouncedVY;
        if (downhillTangent == null) {
            bouncedVX = -side * 0.04;
            bouncedVY = tangentialSpeed;
        } else {
            bouncedVX = downhillTangent[0] * tangentialSpeed - side * 0.06;
            bouncedVY = downhillTangent[1] * tangentialSpeed;
        }
        double initialVX = -bouncedVX / 0.24;
        double initialVY = bouncedVY;
        setVelocity(simulation, 0, initialVX, initialVY);

        if (viaClamp) {
            setPredictedPosition(simulation, 0, wallX + side * 0.01, y);
            invokeConstrainToBoundary(simulation, 0, 0.0, gravityX, gravityY);
        } else {
            invokeConstrainVelocityAgainstBoundary(simulation, 0.0, gravityX, gravityY);
        }

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        if (downhillTangent == null) {
            assertEquals(bouncedVX, velocityX[0], 1.0E-12,
                    caseLabel(message, viaClamp, side, y, gravityX, gravityY));
            assertEquals(bouncedVY, velocityY[0], 1.0E-12,
                    caseLabel(message, viaClamp, side, y, gravityX, gravityY));
            return;
        }

        double[] normal = new double[] {downhillTangent[1], -downhillTangent[0]};
        double bouncedTangential = dot(bouncedVX, bouncedVY, downhillTangent[0], downhillTangent[1]);
        double actualTangential = dot(velocityX[0], velocityY[0], downhillTangent[0], downhillTangent[1]);
        double bouncedNormal = dot(bouncedVX, bouncedVY, normal[0], normal[1]);
        double actualNormal = dot(velocityX[0], velocityY[0], normal[0], normal[1]);

        assertEquals(bouncedNormal, actualNormal, 1.0E-12,
                caseLabel("wall friction should preserve the non-tangential component", viaClamp, side, y, gravityX, gravityY));
        if (expectDamped) {
            assertTrue(bouncedTangential < 0.0,
                    caseLabel("test setup should move opposite gravity along the wall", viaClamp, side, y, gravityX, gravityY));
            assertEquals(0.0, actualTangential, 1.0E-12,
                    caseLabel(message, viaClamp, side, y, gravityX, gravityY));
        } else {
            assertTrue(bouncedTangential >= 0.0,
                    caseLabel("test setup should move with gravity along the wall", viaClamp, side, y, gravityX, gravityY));
            assertEquals(bouncedTangential, actualTangential, 1.0E-12,
                    caseLabel(message, viaClamp, side, y, gravityX, gravityY));
        }
    }

    private static void assertPersistentSideWallResponse(double y, double side,
                                                         double gravityX, double gravityY,
                                                         double tangentialSpeed, boolean expectDamped,
                                                         String message) throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double wallX = sideWallXAt(simulation, y, side);
        arrangeParticles(simulation, new double[][] {{wallX, y}});

        double[] downhillTangent = gravityAlignedWallTangent(y, side, gravityX, gravityY);
        double initialVX = downhillTangent[0] * tangentialSpeed;
        double initialVY = downhillTangent[1] * tangentialSpeed;
        setVelocity(simulation, 0, initialVX, initialVY);

        invokeConstrainVelocityAgainstBoundary(simulation, 0.0, gravityX, gravityY);

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");
        double actualTangential = dot(velocityX[0], velocityY[0], downhillTangent[0], downhillTangent[1]);
        double actualNormal = dot(velocityX[0], velocityY[0], downhillTangent[1], -downhillTangent[0]);

        assertEquals(0.0, actualNormal, 1.0E-12,
                caseLabel("persistent wall friction should stay tangential", false, side, y, gravityX, gravityY));
        if (expectDamped) {
            assertEquals(0.0, actualTangential, 1.0E-12,
                    caseLabel(message, false, side, y, gravityX, gravityY));
        } else {
            assertEquals(tangentialSpeed, actualTangential, 1.0E-12,
                    caseLabel(message, false, side, y, gravityX, gravityY));
        }
    }

    private static void assertVerticalBoundaryResponse(boolean viaClamp, boolean topBoundary,
                                                       double initialVY, String message) throws Exception {
        HourglassSimulation simulation = new HourglassSimulation(1, 42L, LarkController.QualityProfile.DESKTOP.simulationTuning());
        double pad = getDoubleField(simulation, "boundaryPadding");
        double boundaryY = topBoundary ? HourglassSimulation.WORLD_TOP + pad : HourglassSimulation.WORLD_BOTTOM - pad;
        arrangeParticles(simulation, new double[][] {{0.0, boundaryY}});

        double initialVX = 0.05;
        setVelocity(simulation, 0, initialVX, initialVY);

        if (viaClamp) {
            double overshootY = boundaryY + (topBoundary ? -0.01 : 0.01);
            setPredictedPosition(simulation, 0, 0.0, overshootY);
            invokeConstrainToBoundary(simulation, 0, 0.0, 0.0, 0.0);
        } else {
            invokeConstrainVelocityAgainstBoundary(simulation, 0.0, 0.0, 0.0);
        }

        double[] velocityX = getDoubleArrayField(simulation, "velocityX");
        double[] velocityY = getDoubleArrayField(simulation, "velocityY");

        assertEquals(initialVX, velocityX[0], 1.0E-12,
                verticalBoundaryCaseLabel("top/bottom contact should preserve tangential velocity", viaClamp, topBoundary));
        assertEquals(-initialVY, velocityY[0], 1.0E-12,
                verticalBoundaryCaseLabel(message, viaClamp, topBoundary));
    }

    private static String caseLabel(String message, boolean viaClamp, double side, double y,
                                    double gravityX, double gravityY) {
        return message + " [path=" + (viaClamp ? "constrainToBoundary" : "constrainVelocityAgainstBoundary")
                + ", side=" + side + ", y=" + y + ", gravityX=" + gravityX + ", gravityY=" + gravityY + "]";
    }

    private static String verticalBoundaryCaseLabel(String message, boolean viaClamp, boolean topBoundary) {
        return message + " [path=" + (viaClamp ? "constrainToBoundary" : "constrainVelocityAgainstBoundary")
                + ", boundary=" + (topBoundary ? "top" : "bottom") + "]";
    }

    private static void assertSameDirection(double before, double after) {
        if (Math.abs(before) < 1.0E-12) {
            assertEquals(0.0, after, 1.0E-12);
            return;
        }
        assertTrue(Math.signum(before) == Math.signum(after), "velocity direction should stay the same");
    }
}

