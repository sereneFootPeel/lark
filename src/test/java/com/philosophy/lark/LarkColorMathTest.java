package com.philosophy.lark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javafx.scene.paint.Color;

class LarkColorMathTest {
    @Test
    void wrapHueNormalizesAcrossTheCircle() {
        assertEquals(10.0, Lark.wrapHue(370.0), 1.0E-9);
        assertEquals(330.0, Lark.wrapHue(-30.0), 1.0E-9);
        assertEquals(0.0, Lark.wrapHue(720.0), 1.0E-9);
    }

    @Test
    void shiftHueAppliesHueOffsetAndOpacityScaling() {
        Color base = Color.hsb(350.0, 0.60, 0.55, 0.80);

        Color shifted = Lark.shiftHue(base, 25.0, 1.10, 1.15, 0.50);

        assertEquals(15.0, shifted.getHue(), 1.0E-5);
        assertEquals(0.66, shifted.getSaturation(), 1.0E-6);
        assertEquals(0.6325, shifted.getBrightness(), 1.0E-6);
        assertEquals(0.40, shifted.getOpacity(), 1.0E-6);
    }

    @Test
    void shiftHueClampsChannelsIntoValidRange() {
        Color shifted = Lark.shiftHue(Color.hsb(20.0, 0.95, 0.95, 0.90), -80.0, 2.0, 2.0, 2.0);

        assertTrue(shifted.getHue() >= 0.0 && shifted.getHue() <= 360.0);
        assertEquals(1.0, shifted.getSaturation(), 1.0E-9);
        assertEquals(1.0, shifted.getBrightness(), 1.0E-9);
        assertEquals(1.0, shifted.getOpacity(), 1.0E-9);
    }
}

