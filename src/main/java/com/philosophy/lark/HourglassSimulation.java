package com.philosophy.lark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class HourglassSimulation {
    public static final double WORLD_TOP = -1.08;
    public static final double WORLD_BOTTOM = 1.08;
    public static final double MAX_HALF_WIDTH = 0.54;
    public static final double DIAMOND_RADIUS = 0.54;
    public static final double THROAT_HALF_WIDTH = 0.018;

    private static final double FIXED_TICK = 1.0 / 150.0;
    private static final double LIGHT_SCALE = 0.86;
    private static final double PARTICLE_RADIUS_SCALE = 0.32;
    private static final double SMOOTHING_RADIUS_SCALE = 1.48;
    private static final double BASE_GRAVITY = 1.46;
    private static final double AGITATION_GRAVITY = 0.0;
    private static final double INERTIA_ACCELERATION = 0.72;
    private static final int RELAXATION_ITERATIONS = 4;
    private static final double SELF_DENSITY = 1.0;
    private static final double SELF_NEAR_DENSITY = 1.0;
    private static final double REST_DENSITY = 3.6;
    private static final double STIFFNESS = 0.76;
    private static final double NEAR_STIFFNESS = 2.30;
    private static final double PRESSURE_RESPONSE = 0.040;
    private static final double VISCOSITY = 0.42;
    private static final double BASE_DAMPING = 0.992;
    private static final double AGITATED_DAMPING = 0.997;
    private static final double BOUNDARY_BOUNCE = 0.24;
    private static final double BOUNDARY_TANGENT_DAMPING = 0.98;
    private static final double REST_SPEED = 0.075;
    private static final double MAX_SPEED = 2.10;
    private static final double SEED_JITTER = 0.08;
    private static final double MIN_PRESSURE = 0.0;

    private final int particleCount;
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
        this.particleCount = Math.max(1, particleCount);
        this.lightSpacing = deriveSpacing(this.particleCount);
        this.particleRadius = lightSpacing * 0.14;

        // 显式缩减物理边界，形成显著的白边
        this.boundaryPadding = 0.06;

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

    public void step(double deltaSeconds, Vector2 gravity) {
        step(deltaSeconds, gravity, 0.0, gravity);
    }

    public void step(double deltaSeconds, Vector2 gravity, double agitation, Vector2 inertiaDirection) {
        accumulator += Math.max(0.0, deltaSeconds);
        double gravityMagnitude = clamp(gravity.magnitude(), 0.0, 1.0);
        Vector2 normalizedGravity = gravity.normalized(0.0, 0.0);
        Vector2 normalizedInertia = inertiaDirection.normalized(0.0, 0.0);
        double boundedAgitation = clamp(agitation, 0.0, 1.0);
        while (accumulator >= FIXED_TICK) {
            advanceFluidStep(normalizedGravity, gravityMagnitude, boundedAgitation, normalizedInertia);
            accumulator -= FIXED_TICK;
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
            totalSpeed += Math.hypot(velocityX[i], velocityY[i]);
        }
        return totalSpeed / particleCount;
    }

    int isolatedFastParticleCount(double speedThreshold, double neighborRadius) {
        double neighborRadiusSquared = neighborRadius * neighborRadius;
        int isolated = 0;
        for (int i = 0; i < particleCount; i++) {
            double speed = Math.hypot(velocityX[i], velocityY[i]);
            if (speed < speedThreshold) {
                continue;
            }
            boolean hasFastNeighbor = false;
            for (int j = 0; j < particleCount; j++) {
                if (i == j || Math.hypot(velocityX[j], velocityY[j]) < speedThreshold) {
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

    private void advanceFluidStep(Vector2 gravity, double gravityMagnitude, double agitation, Vector2 inertiaDirection) {
        Vector2 effectiveGravity = gravityMagnitude < 1.0E-6
                ? new Vector2(0.0, 0.0)
                : blend(gravity, inertiaDirection, 0.08 + agitation * 0.12).normalized(gravity.x(), gravity.y());
        double gravityStrength = BASE_GRAVITY * gravityMagnitude + agitation * AGITATION_GRAVITY;
        double inertiaStrength = agitation * INERTIA_ACCELERATION;


        buildSpatialIndex(positionX, positionY);
        applyViscosity(agitation);

        double damping = lerp(BASE_DAMPING, AGITATED_DAMPING, agitation);
        for (int i = 0; i < particleCount; i++) {
            double accelerationX = effectiveGravity.x() * gravityStrength + inertiaDirection.x() * inertiaStrength;
            double accelerationY = effectiveGravity.y() * gravityStrength + inertiaDirection.y() * inertiaStrength;
            velocityX[i] = (velocityX[i] + accelerationX * FIXED_TICK) * damping;
            velocityY[i] = (velocityY[i] + accelerationY * FIXED_TICK) * damping;
            predictedX[i] = positionX[i] + velocityX[i] * FIXED_TICK;
            predictedY[i] = positionY[i] + velocityY[i] * FIXED_TICK;
            constrainToBoundary(i, predictedX, predictedY, true, agitation);
        }

        for (int iteration = 0; iteration < RELAXATION_ITERATIONS; iteration++) {
            buildSpatialIndex(predictedX, predictedY);
            computeDensities(predictedX, predictedY);
            relaxPressure(agitation);
            for (int i = 0; i < particleCount; i++) {
                constrainToBoundary(i, predictedX, predictedY, false, agitation);
            }
        }

        buildSpatialIndex(predictedX, predictedY);
        for (int i = 0; i < particleCount; i++) {
            double xPrev = positionX[i];
            double yPrev = positionY[i];

            velocityX[i] = (predictedX[i] - xPrev) / FIXED_TICK;
            velocityY[i] = (predictedY[i] - yPrev) / FIXED_TICK;
            dampenMotion(i, gravity, agitation);
            limitSpeed(i, agitation);
            positionX[i] = predictedX[i];
            positionY[i] = predictedY[i];
            constrainVelocityAgainstBoundary(i, agitation);


            double speed = Math.hypot(velocityX[i], velocityY[i]);
            double normalizedFlow = clamp(speed / (MAX_SPEED * 0.82), 0.0, 1.0);
            flowActivity[i] = clamp(flowActivity[i] * 0.84 + normalizedFlow * 0.16, 0.0, 1.0);
        }
    }

    private void applyViscosity(double agitation) {
        double response = VISCOSITY * FIXED_TICK * (0.86 + agitation * 0.24);
        for (int i = 0; i < particleCount; i++) {
            final int index = i;
            forEachNeighbor(index, positionX, positionY, (j, dx, dy, distance, q) -> {
                double influence = q * q * response;
                double deltaVX = velocityX[j] - velocityX[index];
                double deltaVY = velocityY[j] - velocityY[index];
                velocityX[index] += deltaVX * influence * 0.5;
                velocityY[index] += deltaVY * influence * 0.5;
                velocityX[j] -= deltaVX * influence * 0.5;
                velocityY[j] -= deltaVY * influence * 0.5;
            });
        }
    }

    private void buildSpatialIndex(double[] x, double y[]) {
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
        for (int i = 0; i < particleCount; i++) {
            final int index = i;
            forEachNeighbor(index, x, y, (j, dx, dy, distance, q) -> {
                double q2 = q * q;
                double q3 = q2 * q;
                density[index] += q2;
                density[j] += q2;
                nearDensity[index] += q3;
                nearDensity[j] += q3;
            });
        }
        for (int i = 0; i < particleCount; i++) {
            pressure[i] = Math.max(MIN_PRESSURE, STIFFNESS * (density[i] - REST_DENSITY));
            nearPressure[i] = NEAR_STIFFNESS * nearDensity[i];
        }
    }

    private void relaxPressure(double agitation) {
        double response = PRESSURE_RESPONSE * lightSpacing * (0.92 + agitation * 0.18);
        for (int i = 0; i < particleCount; i++) {
            final int index = i;
            forEachNeighbor(index, predictedX, predictedY, (j, dx, dy, distance, q) -> {
                double magnitude = ((pressure[index] + pressure[j]) * q
                        + (nearPressure[index] + nearPressure[j]) * q * q) * response;
                double safeDistance = Math.max(distance, 1.0E-6);
                double offsetX = dx / safeDistance * magnitude;
                double offsetY = dy / safeDistance * magnitude;
                predictedX[index] -= offsetX * 0.5;
                predictedY[index] -= offsetY * 0.5;
                predictedX[j] += offsetX * 0.5;
                predictedY[j] += offsetY * 0.5;
            });
        }
    }

    private void dampenMotion(int index, Vector2 gravity, double agitation) {
        ContactInfo contactInfo = analyzeContacts(index, predictedX, predictedY);
        double speed = Math.hypot(velocityX[index], velocityY[index]);
        if (contactInfo.contactCount() > 0) {
            double contactDamping = lerp(0.86, 0.93, agitation);
            velocityX[index] *= contactDamping;
            velocityY[index] *= contactDamping;
        }

        // Apply friction to the velocity parallel to the gravity direction to stop clinging to steep walls.
        if (contactInfo.wallContact()) {
            // physics will naturally slide them down the 45-degree slanted walls now
        }

        if (gravity.y() > 0.20 && contactInfo.supportCount() >= 2 && speed < REST_SPEED + agitation * 0.06) {
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

    private void constrainToBoundary(int index, double[] x, double[] y, boolean dampVelocity, double agitation) {
        double px = x[index];
        double py = y[index];
        double currY = positionY[index];
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
        double speed = Math.hypot(velocityX[index], velocityY[index]);
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

    private void forEachNeighbor(int index, double[] x, double[] y, NeighborConsumer consumer) {
        int minCellX = Math.max(0, cellX[index] - 1);
        int maxCellX = Math.min(gridWidth - 1, cellX[index] + 1);
        int minCellY = Math.max(0, cellY[index] - 1);
        int maxCellY = Math.min(gridHeight - 1, cellY[index] + 1);
        for (int gridY = minCellY; gridY <= maxCellY; gridY++) {
            for (int gridX = minCellX; gridX <= maxCellX; gridX++) {
                int other = gridHead[gridY * gridWidth + gridX];
                while (other >= 0) {
                    if (other > index) {
                        double dx = x[other] - x[index];
                        double dy = y[other] - y[index];
                        double distanceSquared = dx * dx + dy * dy;
                        if (distanceSquared < smoothingRadiusSquared) {
                            double distance = Math.sqrt(distanceSquared);
                            if (distance < 1.0E-6) {
                                double angle = (index * 12.9898 + other * 78.233) * 0.5;
                                dx = Math.cos(angle) * particleRadius * 0.15;
                                dy = Math.sin(angle) * particleRadius * 0.15;
                                distance = Math.max(1.0E-6, Math.hypot(dx, dy));
                            }
                            double q = 1.0 - distance / smoothingRadius;
                            consumer.accept(other, dx, dy, distance, q);
                        }
                    }
                    other = nextInCell[other];
                }
            }
        }
    }

    private Vector2 blend(Vector2 gravity, Vector2 inertiaDirection, double inertiaWeight) {
        double clampedWeight = clamp(inertiaWeight, 0.0, 0.32);
        double gravityWeight = 1.0 - clampedWeight;
        return new Vector2(
                gravity.x() * gravityWeight + inertiaDirection.x() * clampedWeight,
                gravity.y() * gravityWeight + inertiaDirection.y() * clampedWeight
        );
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

    private interface NeighborConsumer {
        void accept(int otherIndex, double dx, double dy, double distance, double q);
    }

    private record SeedPoint(double x, double y) {}
    private record ContactInfo(int contactCount, int supportCount, boolean wallContact) {}
    public record Vector2(double x, double y) {
        public double magnitude() {
            return Math.hypot(x, y);
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