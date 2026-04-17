package com.philosophy.lark;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class GravityController {
    private static final double POINTER_DEAD_ZONE = 0.06;
    private static final double AGITATION_EPSILON = 1.0E-3;
    private static final double SENSOR_PLANAR_DEAD_ZONE = 0.18;
    private static final double VECTOR_EPSILON = 1.0E-5;

    private boolean pointerActive;
    private boolean sensorActive;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean upPressed;
    private boolean downPressed;

    private double pointerX;
    private double pointerY = 1.0;
    private double sensorX;
    private double sensorY = 1.0;
    private double currentX;
    private double currentY = 1.0;
    private double previousX;
    private double previousY = 1.0;
    private double inertiaX;
    private double inertiaY = 1.0;
    private double agitation = 2.5;

    public void updateFromPointer(double sceneX, double sceneY, double width, double height) {
        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        double dx = sceneX - width * 0.5;
        double dy = sceneY - height * 0.5;
        double radius = Math.hypot(dx, dy);
        double deadZone = Math.min(width, height) * POINTER_DEAD_ZONE;

        if (radius < deadZone) {
            pointerActive = false;
            return;
        }

        pointerActive = true;
        pointerX = dx / radius;
        pointerY = dy / radius;
    }

    public void releasePointer() {
        pointerActive = false;
    }

    public void updateSensorGravity(double sensorX, double sensorY) {
        this.sensorX = sensorX;
        this.sensorY = sensorY;
        this.sensorActive = true;
    }

    public void updateSensorAcceleration(double sensorX, double sensorY, double sensorZ) {
        double planarX = -sensorX;
        double planarY = sensorY;
        double planarMagnitude = Math.hypot(planarX, planarY);
        double totalMagnitude = Math.sqrt(sensorX * sensorX + sensorY * sensorY + sensorZ * sensorZ);
        double planarRatio = totalMagnitude < VECTOR_EPSILON ? 0.0 : planarMagnitude / totalMagnitude;
        double strength = clamp((planarRatio - SENSOR_PLANAR_DEAD_ZONE) / (1.0 - SENSOR_PLANAR_DEAD_ZONE), 0.0, 1.0);

        if (planarMagnitude < VECTOR_EPSILON || strength <= 0.0) {
            updateSensorGravity(0.0, 0.0);
            return;
        }

        double scale = strength / planarMagnitude;
        updateSensorGravity(planarX * scale, planarY * scale);
    }

    public void clearSensorGravity() {
        sensorActive = false;
    }

    public void step(double deltaSeconds) {
        double safeDelta = Math.max(1.0E-4, deltaSeconds);
        HourglassSimulation.Vector2 target = resolveTargetGravity();
        double response = Math.min(1.0, safeDelta * 10.0);

        previousX = currentX;
        previousY = currentY;
        currentX += (target.x() - currentX) * response;
        currentY += (target.y() - currentY) * response;

        HourglassSimulation.Vector2 current = clampMagnitude(new HourglassSimulation.Vector2(currentX, currentY), 1.0);
        currentX = current.x();
        currentY = current.y();

        HourglassSimulation.Vector2 previous = directionOrZero(previousX, previousY);
        HourglassSimulation.Vector2 currentDirection = directionOrZero(currentX, currentY);
        double directionDelta = Math.hypot(currentDirection.x() - previous.x(), currentDirection.y() - previous.y());
        double turnAmount = (isNearZero(previousX, previousY) || isNearZero(currentX, currentY))
                ? 0.0
                : Math.max(0.0, 1.0 - (previous.x() * currentDirection.x() + previous.y() * currentDirection.y()));
        double magnitudeDelta = Math.abs(Math.hypot(currentX, currentY) - Math.hypot(previousX, previousY));
        agitation = Math.max(agitation * Math.exp(-safeDelta * 0.45),
                Math.min(1.0, directionDelta * 4.5 + turnAmount * 4.2 + magnitudeDelta * 1.8));
        if (agitation < AGITATION_EPSILON) {
            agitation = 0.0;
        }

        inertiaX = previous.x();
        inertiaY = previous.y();
    }

    public HourglassSimulation.Vector2 current() {
        return new HourglassSimulation.Vector2(currentX, currentY);
    }

    public HourglassSimulation.Vector2 inertiaDirection() {
        return new HourglassSimulation.Vector2(inertiaX, inertiaY);
    }

    public double agitation() {
        return agitation;
    }

    public void handleKeyPressed(KeyEvent event) {
        updateKeyState(event.getCode(), true);
    }

    public void handleKeyReleased(KeyEvent event) {
        updateKeyState(event.getCode(), false);
    }

    private HourglassSimulation.Vector2 resolveTargetGravity() {
        double keyX = (rightPressed ? 1.0 : 0.0) - (leftPressed ? 1.0 : 0.0);
        double keyY = (downPressed ? 1.0 : 0.0) - (upPressed ? 1.0 : 0.0);

        if (sensorActive) {
            return clampMagnitude(new HourglassSimulation.Vector2(sensorX + keyX * 0.2, sensorY + keyY * 0.2), 1.0);
        }
        if (pointerActive) {
            return new HourglassSimulation.Vector2(pointerX + keyX * 0.4, pointerY + keyY * 0.4).normalized(0.0, 1.0);
        }
        return new HourglassSimulation.Vector2(keyX, 1.0 + keyY).normalized(0.0, 1.0);
    }

    private static HourglassSimulation.Vector2 directionOrZero(double x, double y) {
        if (isNearZero(x, y)) {
            return new HourglassSimulation.Vector2(0.0, 0.0);
        }
        return new HourglassSimulation.Vector2(x, y).normalized(0.0, 0.0);
    }

    private static HourglassSimulation.Vector2 clampMagnitude(HourglassSimulation.Vector2 vector, double maxMagnitude) {
        double magnitude = Math.hypot(vector.x(), vector.y());
        if (magnitude <= maxMagnitude || magnitude < VECTOR_EPSILON) {
            return vector;
        }
        double scale = maxMagnitude / magnitude;
        return new HourglassSimulation.Vector2(vector.x() * scale, vector.y() * scale);
    }

    private static boolean isNearZero(double x, double y) {
        return Math.hypot(x, y) < VECTOR_EPSILON;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateKeyState(KeyCode keyCode, boolean pressed) {
        switch (keyCode) {
            case LEFT, A -> leftPressed = pressed;
            case RIGHT, D -> rightPressed = pressed;
            case UP, W -> upPressed = pressed;
            case DOWN, S -> downPressed = pressed;
            default -> {
            }
        }
    }
}
