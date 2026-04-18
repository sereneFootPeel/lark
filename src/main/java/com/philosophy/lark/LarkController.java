package com.philosophy.lark;

public final class LarkController {
    static final double DEFAULT_WIDTH = 420.0;
    static final double DEFAULT_HEIGHT = 760.0;
        static final int DEFAULT_LIT_COUNT = 350;
    static final QualityProfile DEFAULT_QUALITY_PROFILE = QualityProfile.MOBILE_BALANCED;

    private LarkController() {
    }

    static SimulationTuning simulationTuning() {
        return DEFAULT_QUALITY_PROFILE.simulationTuning();
    }

    static DisplayTuning displayTuning() {
        return DEFAULT_QUALITY_PROFILE.displayTuning();
    }

    enum QualityProfile {
        DESKTOP(new SimulationTuning(1.0 / 150.0, 4, 6), new DisplayTuning(1.00, 1.55)),
        MOBILE_BALANCED(new SimulationTuning(1.0 / 150.0, 4, 4), new DisplayTuning(1.08, 1.55)),
        MOBILE_BATTERY_SAVER(new SimulationTuning(1.0 / 120.0, 2, 3), new DisplayTuning(1.14, 1.48));

        private final SimulationTuning simulationTuning;
        private final DisplayTuning displayTuning;

        QualityProfile(SimulationTuning simulationTuning, DisplayTuning displayTuning) {
            this.simulationTuning = simulationTuning;
            this.displayTuning = displayTuning;
        }

        SimulationTuning simulationTuning() {
            return simulationTuning;
        }

        DisplayTuning displayTuning() {
            return displayTuning;
        }
    }

    record SimulationTuning(double fixedTick, int relaxationIterations, int maxPhysicsStepsPerFrame) {
        SimulationTuning {
            if (!(fixedTick > 0.0)) {
                throw new IllegalArgumentException("fixedTick must be positive");
            }
            if (relaxationIterations < 1) {
                throw new IllegalArgumentException("relaxationIterations must be at least 1");
            }
            if (maxPhysicsStepsPerFrame < 1) {
                throw new IllegalArgumentException("maxPhysicsStepsPerFrame must be at least 1");
            }
        }
    }

    record DisplayTuning(double pointSpacingScale, double influenceRadiusScale) {
        DisplayTuning {
            if (!(pointSpacingScale > 0.0)) {
                throw new IllegalArgumentException("pointSpacingScale must be positive");
            }
            if (!(influenceRadiusScale > 0.0)) {
                throw new IllegalArgumentException("influenceRadiusScale must be positive");
            }
        }
    }
}