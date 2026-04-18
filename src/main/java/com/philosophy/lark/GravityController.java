package com.philosophy.lark;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class GravityController {
    // 2. 全部 double → float
    private static final float POINTER_DEAD_ZONE = 0.06f;
    private static final float AGITATION_EPSILON = 1.0E-3f;
    private static final float SENSOR_PLANAR_DEAD_ZONE = 0.18f;
    private static final float VECTOR_EPSILON = 1.0E-5f;
    private static final float VECTOR_EPSILON_SQ = VECTOR_EPSILON * VECTOR_EPSILON;
    private static final float ONE_MINUS_SENSOR_PLANAR_DEAD_ZONE = 1.0f - SENSOR_PLANAR_DEAD_ZONE;

    private boolean pointerActive;
    private boolean sensorActive;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean upPressed;
    private boolean downPressed;

    private float pointerX;
    private float pointerY = 1.0f;
    private float sensorX;
    private float sensorY = 1.0f;
    private float currentX;
    private float currentY = 1.0f;
    private float previousX;
    private float previousY = 1.0f;
    private float inertiaX;
    private float inertiaY = 1.0f;
    private float agitation = 2.5f;

    // 缓存当前帧模长和归一化分量
    private float magCurr;
    private float currentNormalizedX;
    private float currentNormalizedY;
    private float magPrev;
    private float previousNormalizedX;
    private float previousNormalizedY;

    public void updateFromPointer(float sceneX, float sceneY, float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        float dx = sceneX - width * 0.5f;
        float dy = sceneY - height * 0.5f;

        // 4. 优化 Math.sqrt：使用距离平方比较，避免开方
        float radiusSq = dx * dx + dy * dy;
        float deadZone = Math.min(width, height) * POINTER_DEAD_ZONE;
        float deadZoneSq = deadZone * deadZone;

        if (radiusSq < deadZoneSq) {
            pointerActive = false;
            return;
        }

        pointerActive = true;
        // 只在真正需要单位向量时才开方
        float invRadius = 1.0f / (float) Math.sqrt(radiusSq);
        pointerX = dx * invRadius;
        pointerY = dy * invRadius;
    }

    public void releasePointer() {
        pointerActive = false;
    }

    public void updateSensorGravity(float sensorX, float sensorY) {
        this.sensorX = sensorX;
        this.sensorY = sensorY;
        this.sensorActive = true;
    }

    public void updateSensorAcceleration(float sensorX, float sensorY, float sensorZ) {
        float planarX = -sensorX;
        float planarY = sensorY;
        float planarMagnitudeSq = planarX * planarX + planarY * planarY;

        // 4. 平方比较代替开方
        if (planarMagnitudeSq < VECTOR_EPSILON * VECTOR_EPSILON) {
            updateSensorGravity(0.0f, 0.0f);
            return;
        }

        float totalMagnitudeSq = sensorX * sensorX + sensorY * sensorY + sensorZ * sensorZ;
        float planarRatio = (totalMagnitudeSq < VECTOR_EPSILON * VECTOR_EPSILON)
                ? 0.0f
                : (float) Math.sqrt(planarMagnitudeSq / totalMagnitudeSq);

        float strength = clamp((planarRatio - SENSOR_PLANAR_DEAD_ZONE) / (1.0f - SENSOR_PLANAR_DEAD_ZONE), 0.0f, 1.0f);

        if (strength <= 0.0f) {
            updateSensorGravity(0.0f, 0.0f);
            return;
        }

        float planarMagnitude = (float) Math.sqrt(planarMagnitudeSq);
        float scale = strength / planarMagnitude;
        updateSensorGravity(planarX * scale, planarY * scale);
    }

    public void clearSensorGravity() {
        sensorActive = false;
    }

    public void step(float deltaSeconds) {
        float safeDelta = Math.max(1.0E-4f, deltaSeconds);
        resolveTargetGravity();
        float response = Math.min(1.0f, safeDelta * 10.0f);
        // 缓存上帧模长和归一化分量
        magPrev = magCurr;
        previousNormalizedX = currentNormalizedX;
        previousNormalizedY = currentNormalizedY;
        previousX = currentX;
        previousY = currentY;
        currentX += (targetX - currentX) * response;
        currentY += (targetY - currentY) * response;
        magCurr = (float) Math.sqrt(Math.fma(currentX, currentX, currentY * currentY));
        if (magCurr >= VECTOR_EPSILON) {
            float invMagCurr = 1.0f / magCurr;
            currentNormalizedX = currentX * invMagCurr;
            currentNormalizedY = currentY * invMagCurr;
        } else {
            currentNormalizedX = 0.0f;
            currentNormalizedY = 0.0f;
        }
        float dx = currentNormalizedX - previousNormalizedX;
        float dy = currentNormalizedY - previousNormalizedY;
        float directionDeltaSq = Math.fma(dx, dx, dy * dy);
        float directionDelta = (float) Math.sqrt(directionDeltaSq);
        float turnAmount = (magPrev < VECTOR_EPSILON || magCurr < VECTOR_EPSILON)
                ? 0.0f
                : Math.max(0.0f, 1.0f - (previousNormalizedX * currentNormalizedX + previousNormalizedY * currentNormalizedY));
        float magnitudeDelta = Math.abs(magCurr - magPrev);
        agitation = Math.max(agitation * (1.0f - 0.45f * safeDelta),
                Math.min(1.0f, directionDelta * 4.5f + turnAmount * 4.2f + magnitudeDelta * 1.8f));
        if (agitation < AGITATION_EPSILON) {
            agitation = 0.0f;
        }
        inertiaX = previousNormalizedX;
        inertiaY = previousNormalizedY;
    }

    // 1. 均匀网格、3. SoA 结构、6. 并行化：
    // 这些优化作用于 HourglassSimulation，本控制器已做好兼容（全部使用 float/原始数组）
    // 5. 渲染层伪全屏：作用于 Lark 绘制，本控制器无影响

    public float currentX() { return currentX; }
    public float currentY() { return currentY; }
    public float inertiaX() { return inertiaX; }
    public float inertiaY() { return inertiaY; }
    public float agitation() { return agitation; }

    public void handleKeyPressed(KeyEvent event) {
        updateKeyState(event.getCode(), true);
    }

    public void handleKeyReleased(KeyEvent event) {
        updateKeyState(event.getCode(), false);
    }

    // 用两个字段代替 targetScratch
    private float targetX;
    private float targetY;

    private void resolveTargetGravity() {
        float keyX = (rightPressed ? 1.0f : 0.0f) - (leftPressed ? 1.0f : 0.0f);
        float keyY = (downPressed ? 1.0f : 0.0f) - (upPressed ? 1.0f : 0.0f);
        if (sensorActive) {
            targetX = sensorX + keyX * 0.2f;
            targetY = sensorY + keyY * 0.2f;
            clampMagnitudeFields();
        } else if (pointerActive) {
            // pointerX/Y 已经归一化，只需加偏移后限制长度
            targetX = pointerX + keyX * 0.4f;
            targetY = pointerY + keyY * 0.4f;
            clampMagnitudeFields();
        } else {
            targetX = keyX;
            targetY = 1.0f + keyY;
            normalizeFields(0.0f, 1.0f);
        }
    }
    private void clampMagnitudeFields() {
        float magSq = Math.fma(targetX, targetX, targetY * targetY);
        if (magSq <= 1.0f || magSq < VECTOR_EPSILON_SQ) return;
        float mag = (float) Math.sqrt(magSq);
        float scale = 1.0f / mag;
        targetX *= scale;
        targetY *= scale;
    }
    private void normalizeFields(float fallbackX, float fallbackY) {
        float magSq = Math.fma(targetX, targetX, targetY * targetY);
        if (magSq < VECTOR_EPSILON_SQ) {
            targetX = fallbackX;
            targetY = fallbackY;
            return;
        }
        float invMag = 1.0f / (float) Math.sqrt(magSq);
        targetX *= invMag;
        targetY *= invMag;
    }

    private static void normalizeOrZero(float x, float y, float[] output) {
        float magSq = Math.fma(x, x, y * y);
        if (magSq < VECTOR_EPSILON * VECTOR_EPSILON) {
            output[0] = 0.0f;
            output[1] = 0.0f;
            return;
        }
        float invMag = 1.0f / (float) Math.sqrt(magSq);
        output[0] = x * invMag;
        output[1] = y * invMag;
    }
    private static void clampMagnitude(float[] vector, float maxMagnitude) {
        float magSq = Math.fma(vector[0], vector[0], vector[1] * vector[1]);
        if (magSq <= maxMagnitude * maxMagnitude || magSq < VECTOR_EPSILON * VECTOR_EPSILON) {
            return;
        }
        float mag = (float) Math.sqrt(magSq);
        float scale = maxMagnitude / mag;
        vector[0] *= scale;
        vector[1] *= scale;
    }
    private static void normalize(float[] vector, float fallbackX, float fallbackY) {
        float magSq = Math.fma(vector[0], vector[0], vector[1] * vector[1]);
        if (magSq < VECTOR_EPSILON * VECTOR_EPSILON) {
            vector[0] = fallbackX;
            vector[1] = fallbackY;
            return;
        }
        float invMag = 1.0f / (float) Math.sqrt(magSq);
        vector[0] *= invMag;
        vector[1] *= invMag;
    }

    // 4. 删除了旧的 magnitude() 方法，全程使用平方运算
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateKeyState(KeyCode keyCode, boolean pressed) {
        switch (keyCode) {
            case LEFT, A -> leftPressed = pressed;
            case RIGHT, D -> rightPressed = pressed;
            case UP, W -> upPressed = pressed;
            case DOWN, S -> downPressed = pressed;
            default -> { }
        }
    }

    private boolean isNearZero(float mag) {
        return mag < VECTOR_EPSILON;
    }
}