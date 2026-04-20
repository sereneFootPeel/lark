package com.philosophy.lark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class HourglassSimulation {
    public static final double WORLD_TOP = -1.08;
    public static final double WORLD_BOTTOM = 1.08;
    public static final double MAX_HALF_WIDTH = 0.54;
    public static final double DIAMOND_RADIUS = 0.54;
    public static final double THROAT_HALF_WIDTH = 0.018;

    private static final double LIGHT_SCALE = 0.86;
    private static final double SMOOTHING_RADIUS_SCALE = 1.48;
    private static final double BASE_GRAVITY = 1.46;
    private static final double AGITATION_GRAVITY = 0.0;
    private static final double INERTIA_ACCELERATION = 0.72;
    private static final double SELF_DENSITY = 1.0;
    private static final double SELF_NEAR_DENSITY = 1.0;
    private static final double REST_DENSITY = 3.6;
    private static final double STIFFNESS = 0.76;
    private static final double NEAR_STIFFNESS = 2.30;
    private static final double PRESSURE_RESPONSE = 0.040;
    private static final double VISCOSITY = 0.42;
    private static final double BASE_DAMPING = 1.0;
    private static final double AGITATED_DAMPING = 1.0;
    private static final double BOUNDARY_BOUNCE = 0.24;
    private static final double BOUNDARY_TANGENT_DAMPING = 0.98;
    private static final double REST_SPEED = 0.075;
    private static final double MAX_SPEED = 2.10;
    private static final double MIN_PRESSURE = 0.0;

    private final int particleCount;
    private final double fixedTick;
    private final int relaxationIterations;
    private final double lightSpacing;
    private final double lightSize;
    private final double particleRadius;
    private final double boundaryPadding;
    private final double smoothingRadius;
    private final double smoothingRadiusSquared;
    private final double cellSize;
    private final double[] positionX;
    private final double[] positionY;
    private final double[] predictedX;
    private final double[] predictedY;
    private final double[] velocityX;
    private final double[] velocityY;
    private final double[] density;
    private final double[] nearDensity;
    private final double[] pressure;
    private final double[] nearPressure;
    private final double[] flowActivity;
    private final int[] nextInCell;
    private final int[] cellX;
    private final int[] cellY;
    private final int[] gridHead;
    private final double gridMinX;
    private final double gridMinY;
    private final int gridWidth;
    private final int gridHeight;

    private double accumulator;

    private double paddingAt(double y) {
        // 白边在靠近颈口（y=0）处平滑过渡变小，防止颈口被完全堵死
        double distToCenter = Math.abs(y);
        double taperDist = 0.12;
        if (distToCenter < taperDist) {
            return boundaryPadding * (0.2 + 0.8 * (distToCenter / taperDist));
        }
        return boundaryPadding;
    }

    public HourglassSimulation(int particleCount) {
        this(particleCount, 42L);
    }

    public HourglassSimulation(int particleCount, long seed) {
        this(particleCount, seed, LarkController.QualityProfile.DESKTOP.simulationTuning());
    }

    HourglassSimulation(int particleCount, long seed, LarkController.SimulationTuning tuning) {
        this.particleCount = Math.max(1, particleCount);
        this.fixedTick = tuning.fixedTick();
        this.relaxationIterations = tuning.relaxationIterations();
        this.lightSpacing = deriveSpacing(this.particleCount);
        this.particleRadius = lightSpacing * 0.14;

        // 显式缩减物理边界，形成显著的白边
        this.boundaryPadding = 0.07;

        this.lightSize = lightSpacing * LIGHT_SCALE;
        this.smoothingRadius = lightSpacing * SMOOTHING_RADIUS_SCALE;
        this.smoothingRadiusSquared = smoothingRadius * smoothingRadius;
        this.cellSize = smoothingRadius;
        this.positionX = new double[this.particleCount];
        this.positionY = new double[this.particleCount];
        this.predictedX = new double[this.particleCount];
        this.predictedY = new double[this.particleCount];
        this.velocityX = new double[this.particleCount];
        this.velocityY = new double[this.particleCount];
        this.density = new double[this.particleCount];
        this.nearDensity = new double[this.particleCount];
        this.pressure = new double[this.particleCount];
        this.nearPressure = new double[this.particleCount];
        this.flowActivity = new double[this.particleCount];
        this.nextInCell = new int[this.particleCount];
        this.cellX = new int[this.particleCount];
        this.cellY = new int[this.particleCount];
        this.gridMinX = -MAX_HALF_WIDTH - smoothingRadius * 2.0;
        this.gridMinY = WORLD_TOP - smoothingRadius * 2.0;
        this.gridWidth = Math.max(4, (int) Math.ceil((MAX_HALF_WIDTH * 2.0 + smoothingRadius * 4.0) / cellSize));
        this.gridHeight = Math.max(4, (int) Math.ceil((WORLD_BOTTOM - WORLD_TOP + smoothingRadius * 4.0) / cellSize));
        this.gridHead = new int[gridWidth * gridHeight];
        seedTopReservoir(seed);
    }

    public record RadialForce(double centerX, double centerY, double radius, double bandWidth, double strength) {
        public boolean isActive() {
            return strength > 1.0E-8 && radius > 1.0E-8;
        }
    }

    public void step(double deltaSeconds, Vector2 gravity) {
        step(deltaSeconds, gravity.x(), gravity.y(), 0.0, gravity.x(), gravity.y());
    }

    public void step(double deltaSeconds, Vector2 gravity, double agitation, Vector2 inertiaDirection) {
        step(deltaSeconds, gravity.x(), gravity.y(), agitation, inertiaDirection.x(), inertiaDirection.y());
    }

    public void step(double deltaSeconds, double gravityX, double gravityY) {
        step(deltaSeconds, gravityX, gravityY, 0.0, gravityX, gravityY);
    }

    public void step(double deltaSeconds, double gravityX, double gravityY, double agitation, double inertiaX, double inertiaY) {
        step(deltaSeconds, gravityX, gravityY, agitation, inertiaX, inertiaY, null);
    }

    public void step(double deltaSeconds, double gravityX, double gravityY, double agitation,
                     double inertiaX, double inertiaY, RadialForce radialForce) {
        accumulator += Math.max(0.0, deltaSeconds);
        double gravityMagnitude = clamp(magnitude(gravityX, gravityY), 0.0, 1.0);
        double normalizedGravityX = 0.0;
        double normalizedGravityY = 0.0;
        if (gravityMagnitude >= 1.0E-8) {
            double inverseGravityMagnitude = 1.0 / gravityMagnitude;
            normalizedGravityX = gravityX * inverseGravityMagnitude;
            normalizedGravityY = gravityY * inverseGravityMagnitude;
        }
        double inertiaMagnitude = magnitude(inertiaX, inertiaY);
        double normalizedInertiaX = 0.0;
        double normalizedInertiaY = 0.0;
        if (inertiaMagnitude >= 1.0E-8) {
            double inverseInertiaMagnitude = 1.0 / inertiaMagnitude;
            normalizedInertiaX = inertiaX * inverseInertiaMagnitude;
            normalizedInertiaY = inertiaY * inverseInertiaMagnitude;
        }
        double boundedAgitation = clamp(agitation, 0.0, 1.0);
        while (accumulator >= fixedTick) {
            advanceFluidStep(normalizedGravityX, normalizedGravityY, gravityMagnitude,
                    boundedAgitation, normalizedInertiaX, normalizedInertiaY, radialForce);
            accumulator -= fixedTick;
        }
    }

    public int getParticleCount() {
        return particleCount;
    }

    public int getLightCount() {
        return particleCount;
    }

    public double getLightX(int index) {
        return positionX[index];
    }

    public double getLightY(int index) {
        return positionY[index];
    }

    public boolean isLit(int index) {
        return index >= 0 && index < particleCount;
    }

    public double getLightFlowIntensity(int index) {
        return flowActivity[index];
    }

    public double getLightSize() {
        return lightSize;
    }

    public double getLightSpacing() {
        return lightSpacing;
    }

    public double topFraction() {
        int top = 0;
        for (int i = 0; i < particleCount; i++) {
            if (positionY[i] < 0.0) {
                top++;
            }
        }
        return top / (double) particleCount;
    }

    public int throatOccupancy() {
        int occupied = 0;
        double band = lightSpacing * 0.92;
        double xLimit = Math.max(particleRadius * 0.85, THROAT_HALF_WIDTH - paddingAt(0.0));
        for (int i = 0; i < particleCount; i++) {
            if (Math.abs(positionY[i]) <= band && Math.abs(positionX[i]) <= xLimit) {
                occupied++;
            }
        }
        return occupied;
    }

    public Vector2 centerOfMass() {
        double sumX = 0.0;
        double sumY = 0.0;
        for (int i = 0; i < particleCount; i++) {
            sumX += positionX[i];
            sumY += positionY[i];
        }
        return new Vector2(sumX / particleCount, sumY / particleCount);
    }

    double averageParticleSpeed() {
        double totalSpeed = 0.0;
        for (int i = 0; i < particleCount; i++) {
            totalSpeed += magnitude(velocityX[i], velocityY[i]);
        }
        return totalSpeed / particleCount;
    }

    int isolatedFastParticleCount(double speedThreshold, double neighborRadius) {
        double neighborRadiusSquared = neighborRadius * neighborRadius;
        int isolated = 0;
        for (int i = 0; i < particleCount; i++) {
            double speed = magnitude(velocityX[i], velocityY[i]);
            if (speed < speedThreshold) {
                continue;
            }
            boolean hasFastNeighbor = false;
            for (int j = 0; j < particleCount; j++) {
                if (i == j || magnitude(velocityX[j], velocityY[j]) < speedThreshold) {
                    continue;
                }
                double dx = positionX[j] - positionX[i];
                double dy = positionY[j] - positionY[i];
                if (dx * dx + dy * dy <= neighborRadiusSquared) {
                    hasFastNeighbor = true;
                    break;
                }
            }
            if (!hasFastNeighbor) {
                isolated++;
            }
        }
        return isolated;
    }

    public double maxBoundaryViolation() {
        double maxViolation = 0.0;
        for (int i = 0; i < particleCount; i++) {
            maxViolation = Math.max(maxViolation, boundaryViolation(positionX[i], positionY[i]));
        }
        return maxViolation;
    }

    public boolean hasLitCellNear(double worldX, double worldY, double radius) {
        double radiusSquared = radius * radius;
        for (int i = 0; i < particleCount; i++) {
            double dx = positionX[i] - worldX;
            double dy = positionY[i] - worldY;
            if (dx * dx + dy * dy <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    int particleCountNear(double worldX, double worldY, double radius) {
        double radiusSquared = radius * radius;
        int count = 0;
        for (int i = 0; i < particleCount; i++) {
            double dx = positionX[i] - worldX;
            double dy = positionY[i] - worldY;
            if (dx * dx + dy * dy <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    public static boolean isInsideHourglass(double worldX, double worldY) {
        double halfWidth = halfWidthAt(worldY);
        return halfWidth >= 0.0 && Math.abs(worldX) <= halfWidth + 1.0E-9;
    }

    public static double halfWidthAt(double worldY) {
        if (worldY < WORLD_TOP || worldY > WORLD_BOTTOM) {
            return -1.0;
        }
        double absY = Math.abs(worldY);
        if (absY <= THROAT_HALF_WIDTH) {
            return THROAT_HALF_WIDTH;
        }
        if (worldY < 0.0) {
            return worldY <= -DIAMOND_RADIUS ? worldY - WORLD_TOP : -worldY;
        }
        return worldY <= DIAMOND_RADIUS ? worldY : WORLD_BOTTOM - worldY;
    }

    private void seedTopReservoir(long seed) {
        Random random = new Random(seed);
        List<SeedPoint> candidates = new ArrayList<>();
        double rowStep = lightSpacing * Math.sqrt(3.0) * 0.5;
        double startPad = paddingAt(WORLD_TOP);
        int rowIndex = 0;
        for (double y = WORLD_TOP + startPad * 1.8; y <= -boundaryPadding; y += rowStep, rowIndex++) {
            double currentPad = paddingAt(y);
            double baseHalfWidth = halfWidthAt(y) - currentPad;
            if (baseHalfWidth <= particleRadius) {
                continue;
            }
            double offset = ((rowIndex % 2 == 0) ? 0.0 : lightSpacing * 0.25);
            for (double x = -baseHalfWidth + offset; x <= baseHalfWidth; x += lightSpacing) {
                candidates.add(new SeedPoint(x, y));
            }
        }

        int attempts = 0;
        while (candidates.size() < particleCount && attempts < particleCount * 200) {
            attempts++;
            double y = lerp(WORLD_TOP + startPad * 1.8, -boundaryPadding, random.nextDouble());
            double currentPad = paddingAt(y);
            double halfWidth = halfWidthAt(y) - currentPad;
            if (halfWidth <= particleRadius) {
                continue;
            }
            double x = lerp(-halfWidth, halfWidth, random.nextDouble());
            if (isInsideHourglass(x, y)) {
                candidates.add(new SeedPoint(x, y));
            }
        }

        for (int i = 0; i < particleCount; i++) {
            SeedPoint point = candidates.get(i % candidates.size());
            positionX[i] = point.x();
            positionY[i] = point.y();
            predictedX[i] = point.x();
            predictedY[i] = point.y();
            flowActivity[i] = 0.04;
        }
    }

    private void advanceFluidStep(double gravityX, double gravityY, double gravityMagnitude,
                                  double agitation, double inertiaX, double inertiaY, RadialForce radialForce) {
        double effectiveGravityX = 0.0;
        double effectiveGravityY = 0.0;
        if (gravityMagnitude >= 1.0E-6) {
            double inertiaWeight = clamp(0.08 + agitation * 0.12, 0.0, 0.32);
            double gravityWeight = 1.0 - inertiaWeight;
            double blendedX = gravityX * gravityWeight + inertiaX * inertiaWeight;
            double blendedY = gravityY * gravityWeight + inertiaY * inertiaWeight;
            double blendedMagnitude = magnitude(blendedX, blendedY);
            if (blendedMagnitude < 1.0E-8) {
                effectiveGravityX = gravityX;
                effectiveGravityY = gravityY;
            } else {
                double inverseBlendedMagnitude = 1.0 / blendedMagnitude;
                effectiveGravityX = blendedX * inverseBlendedMagnitude;
                effectiveGravityY = blendedY * inverseBlendedMagnitude;
            }
        }
        double gravityStrength = BASE_GRAVITY * gravityMagnitude + agitation * AGITATION_GRAVITY;
        double inertiaStrength = agitation * INERTIA_ACCELERATION;
        double accelerationX = effectiveGravityX * gravityStrength + inertiaX * inertiaStrength;
        double accelerationY = effectiveGravityY * gravityStrength + inertiaY * inertiaStrength;

        buildSpatialIndex(positionX, positionY);
        applyViscosity(agitation);
        applyRadialForce(radialForce, agitation);

        // Modify damping to retain minimal oscillation when velocity is low
        double damping = lerp(BASE_DAMPING, AGITATED_DAMPING, agitation);
        for (int i = 0; i < particleCount; i++) {
            velocityX[i] = (velocityX[i] + accelerationX * fixedTick) * damping;
            velocityY[i] = (velocityY[i] + accelerationY * fixedTick) * damping;


            predictedX[i] = positionX[i] + velocityX[i] * fixedTick;
            predictedY[i] = positionY[i] + velocityY[i] * fixedTick;
            constrainToRadialObstacle(i, predictedX, predictedY, radialForce, true);
            constrainToBoundary(i, predictedX, predictedY, true, agitation);
        }

        for (int iteration = 0; iteration < relaxationIterations; iteration++) {
            buildSpatialIndex(predictedX, predictedY);
            computeDensities(predictedX, predictedY);
            relaxPressure(agitation);
            for (int i = 0; i < particleCount; i++) {
                constrainToRadialObstacle(i, predictedX, predictedY, radialForce, false);
                constrainToBoundary(i, predictedX, predictedY, false, agitation);
            }
        }

        buildSpatialIndex(predictedX, predictedY);
        for (int i = 0; i < particleCount; i++) {
            double xPrev = positionX[i];
            double yPrev = positionY[i];

            velocityX[i] = (predictedX[i] - xPrev) / fixedTick;
            velocityY[i] = (predictedY[i] - yPrev) / fixedTick;
            dampenMotion(i, gravityY, agitation);
            limitSpeed(i, agitation);
            positionX[i] = predictedX[i];
            positionY[i] = predictedY[i];
            constrainVelocityAgainstBoundary(i, agitation);


            double speed = magnitude(velocityX[i], velocityY[i]);
            double normalizedFlow = clamp(speed / (MAX_SPEED * 0.82), 0.0, 1.0);
            flowActivity[i] = clamp(flowActivity[i] * 0.84 + normalizedFlow * 0.16, 0.0, 1.0);
        }
    }

    private void applyRadialForce(RadialForce radialForce, double agitation) {
        if (radialForce == null || !radialForce.isActive()) {
            return;
        }

        double centerX = clamp(radialForce.centerX(), -MAX_HALF_WIDTH, MAX_HALF_WIDTH);
        double centerY = clamp(radialForce.centerY(), WORLD_TOP, WORLD_BOTTOM);
        double radius = Math.max(lightSpacing * 1.8, radialForce.radius());
        double bandWidth = Math.max(lightSpacing * 1.1, radialForce.bandWidth());
        double outerRadius = radius + bandWidth;
        double outerRadiusSquared = outerRadius * outerRadius;
        double strength = radialForce.strength() * (0.92 + agitation * 0.18);

        for (int i = 0; i < particleCount; i++) {
            double dx = positionX[i] - centerX;
            double dy = positionY[i] - centerY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared > outerRadiusSquared) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double falloff;
            if (distance <= radius) {
                double normalizedInside = radius <= 1.0E-6 ? 1.0 : distance / radius;
                falloff = 0.30 + 0.70 * normalizedInside * normalizedInside;
            } else {
                double normalizedEdgeOffset = (distance - radius) / Math.max(bandWidth, 1.0E-6);
                if (normalizedEdgeOffset >= 1.0) {
                    continue;
                }
                double edgeFade = 1.0 - normalizedEdgeOffset;
                falloff = edgeFade * edgeFade;
            }
            double impulse = strength * falloff * falloff * fixedTick;

            double directionX;
            double directionY;
            if (distance < 1.0E-6) {
                double angle = separationAngle(i, i + 17);
                directionX = Math.cos(angle);
                directionY = Math.sin(angle);
            } else {
                double inverseDistance = 1.0 / distance;
                directionX = dx * inverseDistance;
                directionY = dy * inverseDistance;
            }

            velocityX[i] += directionX * impulse;
            velocityY[i] += directionY * impulse;
        }
    }

    private void constrainToRadialObstacle(int index, double[] x, double[] y, RadialForce radialForce, boolean dampVelocity) {
        if (radialForce == null || !radialForce.isActive()) {
            return;
        }

        double centerX = clamp(radialForce.centerX(), -MAX_HALF_WIDTH, MAX_HALF_WIDTH);
        double centerY = clamp(radialForce.centerY(), WORLD_TOP, WORLD_BOTTOM);
        double radius = Math.max(lightSpacing * 1.8, radialForce.radius());
        double hardRadius = radius + particleRadius * 0.65;
        double px = x[index];
        double py = y[index];
        double dx = px - centerX;
        double dy = py - centerY;
        double distanceSquared = dx * dx + dy * dy;
        double hardRadiusSquared = hardRadius * hardRadius;
        if (distanceSquared >= hardRadiusSquared) {
            return;
        }

        double distance = Math.sqrt(distanceSquared);
        double normalX;
        double normalY;
        if (distance < 1.0E-6) {
            double angle = separationAngle(index, index + 29);
            normalX = Math.cos(angle);
            normalY = Math.sin(angle);
        } else {
            double inverseDistance = 1.0 / distance;
            normalX = dx * inverseDistance;
            normalY = dy * inverseDistance;
        }

        x[index] = centerX + normalX * hardRadius;
        y[index] = centerY + normalY * hardRadius;

        if (dampVelocity) {
            double inwardSpeed = velocityX[index] * normalX + velocityY[index] * normalY;
            if (inwardSpeed < 0.0) {
                velocityX[index] -= inwardSpeed * normalX;
                velocityY[index] -= inwardSpeed * normalY;
            }
            velocityX[index] *= 0.94;
            velocityY[index] *= 0.94;
        }
    }

    private void applyViscosity(double agitation) {
        double response = VISCOSITY * fixedTick * (0.86 + agitation * 0.24);
        double radiusSquared = smoothingRadiusSquared;
        double radius = smoothingRadius;
        for (int i = 0; i < particleCount; i++) {
            double xi = positionX[i];
            double yi = positionY[i];
            int minCellX = Math.max(0, cellX[i] - 1);
            int maxCellX = Math.min(gridWidth - 1, cellX[i] + 1);
            int minCellY = Math.max(0, cellY[i] - 1);
            int maxCellY = Math.min(gridHeight - 1, cellY[i] + 1);
            for (int gridY = minCellY; gridY <= maxCellY; gridY++) {
                int rowOffset = gridY * gridWidth;
                for (int gridX = minCellX; gridX <= maxCellX; gridX++) {
                    int other = gridHead[rowOffset + gridX];
                    while (other >= 0) {
                        if (other > i) {
                            double dx = positionX[other] - xi;
                            double dy = positionY[other] - yi;
                            double distanceSquared = dx * dx + dy * dy;
                            if (distanceSquared < radiusSquared) {
                                double distance;
                                if (distanceSquared < 1.0E-12) {
                                    double angle = separationAngle(i, other);
                                    dx = Math.cos(angle) * particleRadius * 0.15;
                                    dy = Math.sin(angle) * particleRadius * 0.15;
                                    distance = magnitude(dx, dy);
                                } else {
                                    distance = Math.sqrt(distanceSquared);
                                }
                                double q = 1.0 - distance / radius;
                                double influence = q * q * response;
                                double deltaVX = velocityX[other] - velocityX[i];
                                double deltaVY = velocityY[other] - velocityY[i];
                                double impulseX = deltaVX * influence * 0.5;
                                double impulseY = deltaVY * influence * 0.5;
                                velocityX[i] += impulseX;
                                velocityY[i] += impulseY;
                                velocityX[other] -= impulseX;
                                velocityY[other] -= impulseY;
                            }
                        }
                        other = nextInCell[other];
                    }
                }
            }
        }
    }

    private void buildSpatialIndex(double[] x, double[] y) {
        Arrays.fill(gridHead, -1);
        for (int i = 0; i < particleCount; i++) {
            int gridX = clamp((int) Math.floor((x[i] - gridMinX) / cellSize), 0, gridWidth - 1);
            int gridY = clamp((int) Math.floor((y[i] - gridMinY) / cellSize), 0, gridHeight - 1);
            cellX[i] = gridX;
            cellY[i] = gridY;
            int cell = gridY * gridWidth + gridX;
            nextInCell[i] = gridHead[cell];
            gridHead[cell] = i;
        }
    }

    private void computeDensities(double[] x, double[] y) {
        Arrays.fill(density, SELF_DENSITY);
        Arrays.fill(nearDensity, SELF_NEAR_DENSITY);
        double radiusSquared = smoothingRadiusSquared;
        double radius = smoothingRadius;
        for (int i = 0; i < particleCount; i++) {
            double xi = x[i];
            double yi = y[i];
            int minCellX = Math.max(0, cellX[i] - 1);
            int maxCellX = Math.min(gridWidth - 1, cellX[i] + 1);
            int minCellY = Math.max(0, cellY[i] - 1);
            int maxCellY = Math.min(gridHeight - 1, cellY[i] + 1);
            for (int gridY = minCellY; gridY <= maxCellY; gridY++) {
                int rowOffset = gridY * gridWidth;
                for (int gridX = minCellX; gridX <= maxCellX; gridX++) {
                    int other = gridHead[rowOffset + gridX];
                    while (other >= 0) {
                        if (other > i) {
                            double dx = x[other] - xi;
                            double dy = y[other] - yi;
                            double distanceSquared = dx * dx + dy * dy;
                            if (distanceSquared < radiusSquared) {
                                double distance;
                                if (distanceSquared < 1.0E-12) {
                                    double angle = separationAngle(i, other);
                                    dx = Math.cos(angle) * particleRadius * 0.15;
                                    dy = Math.sin(angle) * particleRadius * 0.15;
                                    distance = magnitude(dx, dy);
                                } else {
                                    distance = Math.sqrt(distanceSquared);
                                }
                                double q = 1.0 - distance / radius;
                                double q2 = q * q;
                                double q3 = q2 * q;
                                density[i] += q2;
                                density[other] += q2;
                                nearDensity[i] += q3;
                                nearDensity[other] += q3;
                            }
                        }
                        other = nextInCell[other];
                    }
                }
            }
        }
        for (int i = 0; i < particleCount; i++) {
            pressure[i] = Math.max(MIN_PRESSURE, STIFFNESS * (density[i] - REST_DENSITY));
            nearPressure[i] = NEAR_STIFFNESS * nearDensity[i];
        }
    }

    private void relaxPressure(double agitation) {
        double response = PRESSURE_RESPONSE * lightSpacing * (0.92 + agitation * 0.18);
        double radiusSquared = smoothingRadiusSquared;
        double radius = smoothingRadius;
        for (int i = 0; i < particleCount; i++) {
            double xi = predictedX[i];
            double yi = predictedY[i];
            int minCellX = Math.max(0, cellX[i] - 1);
            int maxCellX = Math.min(gridWidth - 1, cellX[i] + 1);
            int minCellY = Math.max(0, cellY[i] - 1);
            int maxCellY = Math.min(gridHeight - 1, cellY[i] + 1);
            for (int gridY = minCellY; gridY <= maxCellY; gridY++) {
                int rowOffset = gridY * gridWidth;
                for (int gridX = minCellX; gridX <= maxCellX; gridX++) {
                    int other = gridHead[rowOffset + gridX];
                    while (other >= 0) {
                        if (other > i) {
                            double dx = predictedX[other] - xi;
                            double dy = predictedY[other] - yi;
                            double distanceSquared = dx * dx + dy * dy;
                            if (distanceSquared < radiusSquared) {
                                double distance;
                                if (distanceSquared < 1.0E-12) {
                                    double angle = separationAngle(i, other);
                                    dx = Math.cos(angle) * particleRadius * 0.15;
                                    dy = Math.sin(angle) * particleRadius * 0.15;
                                    distance = magnitude(dx, dy);
                                } else {
                                    distance = Math.sqrt(distanceSquared);
                                }
                                double q = 1.0 - distance / radius;
                                double pairMagnitude = ((pressure[i] + pressure[other]) * q
                                        + (nearPressure[i] + nearPressure[other]) * q * q) * response;
                                double inverseDistance = 1.0 / Math.max(distance, 1.0E-6);
                                double offsetX = dx * inverseDistance * pairMagnitude;
                                double offsetY = dy * inverseDistance * pairMagnitude;
                                double halfOffsetX = offsetX * 0.5;
                                double halfOffsetY = offsetY * 0.5;
                                predictedX[i] -= halfOffsetX;
                                predictedY[i] -= halfOffsetY;
                                predictedX[other] += halfOffsetX;
                                predictedY[other] += halfOffsetY;
                                xi = predictedX[i];
                                yi = predictedY[i];
                            }
                        }
                        other = nextInCell[other];
                    }
                }
            }
        }
    }

    private void dampenMotion(int index, double gravityY, double agitation) {
        ContactInfo contactInfo = analyzeContacts(index, predictedX, predictedY);
        double speed = magnitude(velocityX[index], velocityY[index]);
        if (contactInfo.contactCount() > 0) {
            double contactDamping = lerp(0.86, 0.93, agitation);
            velocityX[index] *= contactDamping;
            velocityY[index] *= contactDamping;
        }

        if (gravityY > 0.20 && contactInfo.supportCount() >= 2 && speed < REST_SPEED + agitation * 0.06) {
            velocityX[index] *= 0.18;
            velocityY[index] *= 0.05;
            if (Math.abs(velocityX[index]) < 0.004) {
                velocityX[index] = 0.0;
            }
            if (Math.abs(velocityY[index]) < 0.004) {
                velocityY[index] = 0.0;
            }
        }
    }

    private ContactInfo analyzeContacts(int index, double[] x, double[] y) {
        int contacts = 0;
        int supportCount = 0;
        for (int gridY = Math.max(0, cellY[index] - 1); gridY <= Math.min(gridHeight - 1, cellY[index] + 1); gridY++) {
            for (int gridX = Math.max(0, cellX[index] - 1); gridX <= Math.min(gridWidth - 1, cellX[index] + 1); gridX++) {
                int other = gridHead[gridY * gridWidth + gridX];
                while (other >= 0) {
                    if (other != index) {
                        double dx = x[other] - x[index];
                        double dy = y[other] - y[index];
                        double distanceSquared = dx * dx + dy * dy;
                        if (distanceSquared <= smoothingRadiusSquared * 0.34) {
                            contacts++;
                            if (dy > particleRadius * 0.20) {
                                supportCount++;
                            }
                        }
                    }
                    other = nextInCell[other];
                }
            }
        }
        double px = x[index];
        double py = y[index];

        double pad = paddingAt(py);
        double halfWidth = Math.max(0.0, halfWidthAt(py) - pad);
        boolean wallContact = Math.abs(px) >= halfWidth - particleRadius * 0.20
                || py <= WORLD_TOP + pad
                || py >= WORLD_BOTTOM - pad;
        return new ContactInfo(contacts, supportCount, wallContact);
    }

    // 用于测试：返回每步实际使用的relaxation迭代次数（当前实现为固定值）
    public int lastRelaxationIterationsUsed() {
        return relaxationIterations;
    }

    private void constrainToBoundary(int index, double[] x, double[] y, boolean dampVelocity, double agitation) {
        double px = x[index];
        double py = y[index];
        double pad = paddingAt(py);


        // 上下对称边界约束
        double minY = WORLD_TOP + pad;
        double maxY = WORLD_BOTTOM - pad;
        boolean clampedY = false;
        if (py < minY) {
            py = minY;
            clampedY = true;
        } else if (py > maxY) {
            py = maxY;
            clampedY = true;
        }

        double halfWidth = Math.max(particleRadius * 1.05, halfWidthAt(py) - pad);
        boolean clampedX = false;
        if (px < -halfWidth) {
            px = -halfWidth;
            clampedX = true;
        } else if (px > halfWidth) {
            px = halfWidth;
            clampedX = true;
        }

        x[index] = px;
        y[index] = py;

        if (dampVelocity) {
            double currentBounce = Math.min(0.95, BOUNDARY_BOUNCE + agitation * 0.7);
            if (clampedX) {
                velocityX[index] = -velocityX[index] * currentBounce;
                velocityY[index] *= BOUNDARY_TANGENT_DAMPING;
            }
            if (clampedY) {
                velocityY[index] = -velocityY[index] * currentBounce;
                velocityX[index] *= BOUNDARY_TANGENT_DAMPING;
            }
        }
    }

    private void constrainVelocityAgainstBoundary(int index, double agitation) {
        double x = positionX[index];
        double y = positionY[index];
        double pad = paddingAt(y);
        double halfWidth = Math.max(0.0, halfWidthAt(y) - pad);

        double currentBounce = Math.min(0.95, BOUNDARY_BOUNCE + agitation * 0.7);

        if (Math.abs(x) >= halfWidth - 1.0E-5 && Math.signum(x) == Math.signum(velocityX[index])) {
            velocityX[index] = -velocityX[index] * currentBounce;
            velocityY[index] *= BOUNDARY_TANGENT_DAMPING;
        }
        if (y <= WORLD_TOP + pad && velocityY[index] < 0.0) {
            velocityY[index] = -velocityY[index] * currentBounce;
            velocityX[index] *= BOUNDARY_TANGENT_DAMPING;
        }
        if (y >= WORLD_BOTTOM - pad && velocityY[index] > 0.0) {
            velocityY[index] = -velocityY[index] * currentBounce;
            velocityX[index] *= BOUNDARY_TANGENT_DAMPING;
        }
    }

    private void limitSpeed(int index, double agitation) {
        double maxSpeed = MAX_SPEED + agitation * 0.5;
        double speed = magnitude(velocityX[index], velocityY[index]);
        if (speed <= maxSpeed) {
            return;
        }
        double scale = maxSpeed / Math.max(speed, 1.0E-6);
        velocityX[index] *= scale;
        velocityY[index] *= scale;
    }

    private double boundaryViolation(double x, double y) {
        double violation = 0.0;
        if (y < WORLD_TOP) {
            violation = Math.max(violation, WORLD_TOP - y);
        }
        if (y > WORLD_BOTTOM) {
            violation = Math.max(violation, y - WORLD_BOTTOM);
        }
        double halfWidth = halfWidthAt(clamp(y, WORLD_TOP, WORLD_BOTTOM));
        if (halfWidth >= 0.0 && Math.abs(x) > halfWidth) {
            violation = Math.max(violation, Math.abs(x) - halfWidth);
        }
        return violation;
    }

    private static double separationAngle(int index, int other) {
        return (index * 12.9898 + other * 78.233) * 0.5;
    }

    private static double magnitude(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double deriveSpacing(int particleCount) {
        double topArea = 2.0 * DIAMOND_RADIUS * DIAMOND_RADIUS;
        double effectiveDensity = Math.max(1_180.0, particleCount * 1.35);
        double spacing = Math.sqrt(topArea / effectiveDensity);
        return clamp(spacing, 0.018, 0.026);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SeedPoint(double x, double y) {}
    private record ContactInfo(int contactCount, int supportCount, boolean wallContact) {}
    public record Vector2(double x, double y) {
        public double magnitude() {
            return HourglassSimulation.magnitude(x, y);
        }

        public Vector2 normalized(double fallbackX, double fallbackY) {
            double magnitude = magnitude();
            if (magnitude < 1.0E-8) {
                return new Vector2(fallbackX, fallbackY);
            }
            return new Vector2(x / magnitude, y / magnitude);
        }
    }
}

