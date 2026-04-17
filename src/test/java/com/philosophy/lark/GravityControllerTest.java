package com.philosophy.lark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityControllerTest {
    @Test
    void pointerControlsFullCircleGravity() {
        GravityController controller = new GravityController();

        controller.updateFromPointer(50, 0, 100, 100);
        controller.step(1.0);
        HourglassSimulation.Vector2 up = controller.current();
        assertTrue(up.y() < -0.9, "pointer above center should point gravity upward");

        controller.updateFromPointer(50, 100, 100, 100);
        controller.step(1.0);
        HourglassSimulation.Vector2 down = controller.current();
        assertTrue(down.y() > 0.9, "pointer below center should point gravity downward");

        controller.updateFromPointer(100, 50, 100, 100);
        controller.step(1.0);
        HourglassSimulation.Vector2 right = controller.current();
        assertTrue(right.x() > 0.9, "pointer right of center should point gravity rightward");

        controller.updateFromPointer(0, 50, 100, 100);
        controller.step(1.0);
        HourglassSimulation.Vector2 left = controller.current();
        assertTrue(left.x() < -0.9, "pointer left of center should point gravity leftward");
    }

    @Test
    void fastRedirectionBuildsAgitation() {
        GravityController controller = new GravityController();

        controller.updateFromPointer(100, 20, 100, 100);
        controller.step(1.0 / 60.0);
        controller.updateFromPointer(180, 100, 100, 100);
        controller.step(1.0 / 60.0);

        assertTrue(controller.agitation() > 0.15, "quick direction changes should increase agitation");
    }

    @Test
    void flatPhoneProducesNoGravity() {
        GravityController controller = new GravityController();

        controller.updateSensorAcceleration(0.0, 0.0, 9.81);
        controller.step(1.0);

        HourglassSimulation.Vector2 gravity = controller.current();
        assertEquals(0.0, gravity.x(), 1.0E-6, "flat phone should not create sideways gravity");
        assertEquals(0.0, gravity.y(), 1.0E-6, "flat phone should not create downward gravity");
    }

    @Test
    void phoneTiltProducesDirectionalSensorGravity() {
        GravityController controller = new GravityController();

        controller.updateSensorAcceleration(-9.81, 0.0, 0.0);
        controller.step(1.0);
        HourglassSimulation.Vector2 right = controller.current();
        assertTrue(right.x() > 0.9, "tilting the phone right should pull gravity rightward on screen");

        controller.updateSensorAcceleration(0.0, 9.81, 0.0);
        controller.step(1.0);
        HourglassSimulation.Vector2 down = controller.current();
        assertTrue(down.y() > 0.9, "tilting the phone downward should pull gravity toward the bottom of the screen");
    }
}

