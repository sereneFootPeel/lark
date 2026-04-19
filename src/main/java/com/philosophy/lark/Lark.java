package com.philosophy.lark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import com.gluonhq.attach.accelerometer.AccelerometerService;

public final class Lark extends Application {
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color FOREGROUND = Color.web("#54626F");      // 暮色蓝
    private static final Color LIQUID_SOFT = Color.web("#7A868F", 0.30); // 稀释蓝灰
    private static final Color LIQUID_SOLID = Color.web("#64737E", 0.98); // 固体钢蓝
    private static final Color LIQUID_FLOW = Color.web("#8BA0B0", 0.98);
    private static final float REVEAL_START = 0.11f;
    private static final float REVEAL_END = 0.62f;
    private static final float CONTOUR_GRID_SCALE = 0.5f;
    private static final float CONTOUR_BASE_STRENGTH = 0.18f;

    @Override
    public void start(Stage stage) {
        LarkController.SimulationTuning simulationTuning = LarkController.simulationTuning();
        LarkController.DisplayTuning displayTuning = LarkController.displayTuning();
        HourglassSimulation simulation = new HourglassSimulation(LarkController.DEFAULT_LIT_COUNT, 42L, simulationTuning);
        GravityController gravityController = new GravityController();
        HourglassDisplayField displayField = HourglassDisplayField.create(simulation, displayTuning);

        Canvas frameCanvas = new Canvas(LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT);
        Canvas fluidCanvas = new Canvas(LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT);
        StackPane root = new StackPane(frameCanvas, fluidCanvas);
        root.setStyle("-fx-background-color: white;");

        frameCanvas.widthProperty().bind(root.widthProperty());
        frameCanvas.heightProperty().bind(root.heightProperty());
        fluidCanvas.widthProperty().bind(root.widthProperty());
        fluidCanvas.heightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT, BACKGROUND);
        scene.setOnMouseMoved(event -> gravityController.updateFromPointer(
                (float)event.getSceneX(), (float)event.getSceneY(), (float)scene.getWidth(), (float)scene.getHeight()));
        scene.setOnMouseDragged(event -> gravityController.updateFromPointer(
                (float)event.getSceneX(), (float)event.getSceneY(), (float)scene.getWidth(), (float)scene.getHeight()));
        scene.setOnMouseExited(event -> gravityController.releasePointer());
        scene.setOnTouchPressed(event -> gravityController.updateFromPointer(
                (float)event.getTouchPoint().getSceneX(), (float)event.getTouchPoint().getSceneY(), (float)scene.getWidth(), (float)scene.getHeight()));
        scene.setOnTouchMoved(event -> gravityController.updateFromPointer(
                (float)event.getTouchPoint().getSceneX(), (float)event.getTouchPoint().getSceneY(), (float)scene.getWidth(), (float)scene.getHeight()));
        scene.setOnTouchReleased(event -> gravityController.releasePointer());
        scene.setOnKeyPressed(gravityController::handleKeyPressed);
        scene.setOnKeyReleased(gravityController::handleKeyReleased);

        stage.setTitle("Lark");
        stage.setScene(scene);
        stage.setMinWidth(340);
        stage.setMinHeight(560);
        stage.show();

        root.requestFocus();
        redrawStaticLayer(frameCanvas.getGraphicsContext2D());
        frameCanvas.widthProperty().addListener((obs, oldValue, newValue) -> redrawStaticLayer(frameCanvas.getGraphicsContext2D()));
        frameCanvas.heightProperty().addListener((obs, oldValue, newValue) -> redrawStaticLayer(frameCanvas.getGraphicsContext2D()));

        AccelerometerService.create().ifPresent(service -> {
            service.accelerationProperty().addListener((obs, ov, nv) -> {
                if (nv == null) {
                    gravityController.clearSensorGravity();
                    return;
                }
                gravityController.updateSensorAcceleration((float)nv.getX(), (float)nv.getY(), (float)nv.getZ());
            });
            service.start();
            stage.setOnHidden(event -> service.stop());
        });

        AnimationTimer timer = new AnimationTimer() {
            private long lastTick;

            @Override
            public void handle(long now) {
                if (lastTick == 0L) {
                    lastTick = now;
                    render(fluidCanvas.getGraphicsContext2D(), simulation, gravityController, displayField);
                    return;
                }

                float rawDeltaSeconds = (float)Math.min((now - lastTick) / 1_000_000_000.0, 1.0 / 20.0);
                lastTick = now;
                float deltaSeconds = Math.min(rawDeltaSeconds,
                        (float)simulationTuning.fixedTick() * (float)simulationTuning.maxPhysicsStepsPerFrame());

                gravityController.step(deltaSeconds);
                simulation.step(deltaSeconds,
                        gravityController.currentX(),
                        gravityController.currentY(),
                        gravityController.agitation(),
                        gravityController.inertiaX(),
                        gravityController.inertiaY());
                render(fluidCanvas.getGraphicsContext2D(), simulation, gravityController, displayField);
            }
        };
        timer.start();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void render(GraphicsContext gc, HourglassSimulation simulation,
                        GravityController gravityController, HourglassDisplayField displayField) {
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
        drawLights(gc, simulation, displayField, gravityController.agitation());
        // 沿沙漏边界外部画一圈背景色遮罩，遮住溢出边界的粒子
        float width = (float)gc.getCanvas().getWidth();
        float height = (float)gc.getCanvas().getHeight();
        float scale = (float)Math.min((width - 56.0f) / (HourglassSimulation.MAX_HALF_WIDTH * 2.15f),
                (height - 56.0f) / (HourglassSimulation.WORLD_BOTTOM - HourglassSimulation.WORLD_TOP));
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        drawHourglassMask(gc, centerX, centerY, scale);
        drawHourglassFrame(gc, centerX, centerY, scale);
    }

    // 沿沙漏边界外部画一圈背景色遮罩，遮住溢出边界的粒子
    private void drawHourglassMask(GraphicsContext gc, float centerX, float centerY, float scale) {
        float radius = (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float topCenterY = centerY - (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float bottomCenterY = centerY + (float)(HourglassSimulation.DIAMOND_RADIUS * scale);

        gc.setStroke(BACKGROUND);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth((float)Math.max(8.0f, scale * 0.048f));

        gc.beginPath();
        gc.moveTo(centerX, topCenterY - radius);
        gc.lineTo(centerX + radius, topCenterY);
        gc.lineTo(centerX, topCenterY + radius);
        gc.lineTo(centerX - radius, topCenterY);
        closePath(gc);
        gc.stroke();

        gc.beginPath();
        gc.moveTo(centerX, bottomCenterY - radius);
        gc.lineTo(centerX + radius, bottomCenterY);
        gc.lineTo(centerX, bottomCenterY + radius);
        gc.lineTo(centerX - radius, bottomCenterY);
        closePath(gc);
        gc.stroke();
    }

    private void redrawStaticLayer(GraphicsContext gc) {
        float width = (float)gc.getCanvas().getWidth();
        float height = (float)gc.getCanvas().getHeight();
        float scale = (float)Math.min((width - 56.0f) / (HourglassSimulation.MAX_HALF_WIDTH * 2.15f),
                (height - 56.0f) / (HourglassSimulation.WORLD_BOTTOM - HourglassSimulation.WORLD_TOP));
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);
        drawHourglassFrame(gc, centerX, centerY, scale);
    }

    private void drawHourglassFrame(GraphicsContext gc, float centerX, float centerY, float scale) {
        float radius = (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float topCenterY = centerY - (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float bottomCenterY = centerY + (float)(HourglassSimulation.DIAMOND_RADIUS * scale);

        gc.setStroke(FOREGROUND);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth((float)Math.max(2.0f, scale * 0.012f));

        gc.beginPath();
        gc.moveTo(centerX, topCenterY - radius);
        gc.lineTo(centerX + radius, topCenterY);
        gc.lineTo(centerX, topCenterY + radius);
        gc.lineTo(centerX - radius, topCenterY);
        closePath(gc);
        gc.stroke();

        gc.beginPath();
        gc.moveTo(centerX, bottomCenterY - radius);
        gc.lineTo(centerX + radius, bottomCenterY);
        gc.lineTo(centerX, bottomCenterY + radius);
        gc.lineTo(centerX - radius, bottomCenterY);
        closePath(gc);
        gc.stroke();
    }

    private void closePath(GraphicsContext gc) {
        gc.closePath();
    }

    private void drawLights(GraphicsContext gc, HourglassSimulation simulation,
                            HourglassDisplayField displayField, float agitation) {
        float width = (float)gc.getCanvas().getWidth();
        float height = (float)gc.getCanvas().getHeight();
        float scale = (float)Math.min((width - 56.0f) / (HourglassSimulation.MAX_HALF_WIDTH * 2.15f),
                (height - 56.0f) / (HourglassSimulation.WORLD_BOTTOM - HourglassSimulation.WORLD_TOP));
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        float lightSize = (float)Math.max(1.8f, (float)simulation.getLightSize() * scale) * (1.0f + agitation * 0.16f);
        float cellPitch = (float)(displayField.pointSpacing * scale);
        float particleSize = (float)Math.max(2.0f, Math.min(lightSize * 0.72f, cellPitch * 0.74f));
        float softParticleSize = particleSize * 0.96f;
        float flowParticleSize = particleSize * 1.02f;

        displayField.accumulate(simulation);
        Arrays.fill(displayField.reveal, 0.0f);
        int softCount = 0;
        int solidCount = 0;
        int flowCount = 0;
        for (int i = 0; i < displayField.pointX.length; i++) {
            float occupancy = displayField.occupancy[i];
            float filled = clamp(occupancy * 0.82f, 0.0f, 1.0f);
            float reveal = smoothstep(REVEAL_START, REVEAL_END, filled);
            displayField.reveal[i] = reveal;
            if (reveal <= 0.01f) {
                continue;
            }
            float flowRatio = occupancy <= 1.0E-9f ? 0.0f : displayField.flowEnergy[i] / occupancy;
            if (flowRatio >= 0.56f && reveal >= 0.24f) {
                displayField.flowIndices[flowCount++] = i;
            } else if (reveal >= 0.52f) {
                displayField.solidIndices[solidCount++] = i;
            } else {
                displayField.softIndices[softCount++] = i;
            }
        }

        drawBucket(gc, displayField, displayField.softIndices, softCount,
                centerX, centerY, scale, softParticleSize, LIQUID_SOFT, 0.18f, 1.22f);
        drawBucket(gc, displayField, displayField.solidIndices, solidCount,
                centerX, centerY, scale, particleSize, LIQUID_SOLID, 0.22f, 1.08f);
        drawBucket(gc, displayField, displayField.flowIndices, flowCount,
                centerX, centerY, scale, flowParticleSize, LIQUID_FLOW, 0.20f, 1.12f);
    }

    private void drawBucket(GraphicsContext gc, HourglassDisplayField displayField, int[] indices, int count,
                            float centerX, float centerY, float scale, float particleSize, Color color,
                            float contourThreshold, float contourRadiusScale) {
        if (count <= 0) {
            return;
        }

        float worldRadius = Math.max(displayField.pointSpacing * contourRadiusScale,
                particleSize / Math.max(scale, 1.0E-4f) * 0.82f);
        displayField.populateContourField(indices, count, worldRadius);

        gc.setFill(color);
        gc.beginPath();
        boolean hasArea = false;
        float cellSize = displayField.contourSpacing * scale;
        for (int y = 0; y < displayField.contourHeight - 1; y++) {
            float top = centerY + (displayField.contourMinY + y * displayField.contourSpacing) * scale;
            for (int x = 0; x < displayField.contourWidth - 1; x++) {
                int row = y * displayField.contourWidth + x;
                float topLeft = displayField.contourField[row];
                float topRight = displayField.contourField[row + 1];
                float bottomLeft = displayField.contourField[row + displayField.contourWidth];
                float bottomRight = displayField.contourField[row + displayField.contourWidth + 1];

                int caseIndex = 0;
                if (topLeft >= contourThreshold) {
                    caseIndex |= 8;
                }
                if (topRight >= contourThreshold) {
                    caseIndex |= 4;
                }
                if (bottomRight >= contourThreshold) {
                    caseIndex |= 2;
                }
                if (bottomLeft >= contourThreshold) {
                    caseIndex |= 1;
                }
                if (caseIndex == 0) {
                    continue;
                }

                float left = centerX + (displayField.contourMinX + x * displayField.contourSpacing) * scale;
                hasArea |= appendContourCell(gc, caseIndex, left, top, cellSize);
            }
        }
        if (hasArea) {
            gc.fill();
        }
    }

    private boolean appendContourCell(GraphicsContext gc, int caseIndex, float left, float top, float size) {
        float right = left + size;
        float bottom = top + size;
        float midX = left + size * 0.5f;
        float midY = top + size * 0.5f;

        switch (caseIndex) {
            case 1 -> {
                gc.moveTo(left, bottom);
                gc.lineTo(left, midY);
                gc.quadraticCurveTo(left, bottom, midX, bottom);
                closePath(gc);
                return true;
            }
            case 2 -> {
                gc.moveTo(right, bottom);
                gc.lineTo(midX, bottom);
                gc.quadraticCurveTo(right, bottom, right, midY);
                closePath(gc);
                return true;
            }
            case 3 -> {
                appendPolygon(gc, left, bottom, left, midY, right, midY, right, bottom);
                return true;
            }
            case 4 -> {
                gc.moveTo(right, top);
                gc.lineTo(right, midY);
                gc.quadraticCurveTo(right, top, midX, top);
                closePath(gc);
                return true;
            }
            case 5 -> {
                gc.moveTo(left, bottom);
                gc.lineTo(left, midY);
                gc.quadraticCurveTo(left, bottom, midX, bottom);
                closePath(gc);
                gc.moveTo(right, top);
                gc.lineTo(right, midY);
                gc.quadraticCurveTo(right, top, midX, top);
                closePath(gc);
                return true;
            }
            case 6 -> {
                appendPolygon(gc, midX, top, right, top, right, bottom, midX, bottom);
                return true;
            }
            case 7 -> {
                gc.moveTo(left, top);
                gc.lineTo(midX, top);
                gc.quadraticCurveTo(right, top, right, midY);
                gc.lineTo(right, bottom);
                gc.lineTo(left, bottom);
                closePath(gc);
                return true;
            }
            case 8 -> {
                gc.moveTo(left, top);
                gc.lineTo(midX, top);
                gc.quadraticCurveTo(left, top, left, midY);
                closePath(gc);
                return true;
            }
            case 9 -> {
                appendPolygon(gc, left, top, midX, top, midX, bottom, left, bottom);
                return true;
            }
            case 10 -> {
                gc.moveTo(left, top);
                gc.lineTo(midX, top);
                gc.quadraticCurveTo(left, top, left, midY);
                closePath(gc);
                gc.moveTo(right, bottom);
                gc.lineTo(midX, bottom);
                gc.quadraticCurveTo(right, bottom, right, midY);
                closePath(gc);
                return true;
            }
            case 11 -> {
                gc.moveTo(left, top);
                gc.lineTo(right, top);
                gc.lineTo(right, midY);
                gc.quadraticCurveTo(right, bottom, midX, bottom);
                gc.lineTo(left, bottom);
                closePath(gc);
                return true;
            }
            case 12 -> {
                appendPolygon(gc, left, top, right, top, right, midY, left, midY);
                return true;
            }
            case 13 -> {
                gc.moveTo(left, top);
                gc.lineTo(right, top);
                gc.lineTo(right, bottom);
                gc.lineTo(midX, bottom);
                gc.quadraticCurveTo(left, bottom, left, midY);
                closePath(gc);
                return true;
            }
            case 14 -> {
                gc.moveTo(midX, top);
                gc.lineTo(right, top);
                gc.lineTo(right, bottom);
                gc.lineTo(left, bottom);
                gc.lineTo(left, midY);
                gc.quadraticCurveTo(left, top, midX, top);
                closePath(gc);
                return true;
            }
            case 15 -> {
                appendPolygon(gc, left, top, right, top, right, bottom, left, bottom);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void appendPolygon(GraphicsContext gc, float... coordinates) {
        if (coordinates.length < 6 || (coordinates.length & 1) != 0) {
            return;
        }
        gc.moveTo(coordinates[0], coordinates[1]);
        for (int i = 2; i < coordinates.length; i += 2) {
            gc.lineTo(coordinates[i], coordinates[i + 1]);
        }
        closePath(gc);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value >= edge1 ? 1.0f : 0.0f;
        }
        float normalized = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return normalized * normalized * (3.0f - 2.0f * normalized);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class HourglassDisplayField {
        final float[] pointX;
        final float[] pointY;
        final float[] occupancy;
        final float[] flowEnergy;
        final float[] reveal;
        final int[] nextInCell;
        final int[] gridHead;
        final int[] softIndices;
        final int[] solidIndices;
        final int[] flowIndices;
        final float pointSpacing;
        final float influenceRadius;
        final float influenceRadiusSquared;
        final float cellSize;
        final float gridMinX;
        final float gridMinY;
        final int gridWidth;
        final int gridHeight;
        final float contourMinX;
        final float contourMinY;
        final float contourSpacing;
        final int contourWidth;
        final int contourHeight;
        final float[] contourField;

        HourglassDisplayField(float[] pointX, float[] pointY, float pointSpacing, float influenceRadius) {
            this.pointX = pointX;
            this.pointY = pointY;
            this.occupancy = new float[pointX.length];
            this.flowEnergy = new float[pointX.length];
            this.reveal = new float[pointX.length];
            this.nextInCell = new int[pointX.length];
            this.softIndices = new int[pointX.length];
            this.solidIndices = new int[pointX.length];
            this.flowIndices = new int[pointX.length];
            this.pointSpacing = pointSpacing;
            this.influenceRadius = influenceRadius;
            this.influenceRadiusSquared = influenceRadius * influenceRadius;
            this.cellSize = influenceRadius;
            this.gridMinX = (float)(-HourglassSimulation.MAX_HALF_WIDTH - influenceRadius * 2.0f);
            this.gridMinY = (float) HourglassSimulation.WORLD_TOP - influenceRadius * 2.0f;
            this.gridWidth = Math.max(4,
                    (int) Math.ceil((HourglassSimulation.MAX_HALF_WIDTH * 2.0f + influenceRadius * 4.0f) / cellSize));
            this.gridHeight = Math.max(4,
                    (int) Math.ceil(((float) HourglassSimulation.WORLD_BOTTOM - (float) HourglassSimulation.WORLD_TOP + influenceRadius * 4.0f) / cellSize));
            this.gridHead = new int[gridWidth * gridHeight];
            this.contourSpacing = Math.max(pointSpacing * CONTOUR_GRID_SCALE, 1.0E-4f);
            this.contourMinX = (float)(-HourglassSimulation.MAX_HALF_WIDTH - pointSpacing * 1.5f);
            this.contourMinY = (float) HourglassSimulation.WORLD_TOP - pointSpacing * 1.5f;
            this.contourWidth = Math.max(4,
                    (int) Math.ceil((HourglassSimulation.MAX_HALF_WIDTH * 2.0f + pointSpacing * 3.0f) / contourSpacing) + 1);
            this.contourHeight = Math.max(4,
                    (int) Math.ceil(((float) HourglassSimulation.WORLD_BOTTOM - (float) HourglassSimulation.WORLD_TOP + pointSpacing * 3.0f) / contourSpacing) + 1);
            this.contourField = new float[contourWidth * contourHeight];
            rebuildSpatialIndex();
        }

        static HourglassDisplayField create(HourglassSimulation simulation, LarkController.DisplayTuning tuning) {
            float spacing = (float)(simulation.getLightSpacing() * tuning.pointSpacingScale());
            float rowStep = spacing * (float)Math.sqrt(3.0f) * 0.5f;
            List<float[]> points = new ArrayList<>();
            int rowIndex = 0;

            for (float y = (float) HourglassSimulation.WORLD_TOP + spacing * 1.25f;
                 y <= (float) HourglassSimulation.WORLD_BOTTOM - spacing * 1.25f;
                 y += rowStep, rowIndex++) {
                float halfWidth = (float)(HourglassSimulation.halfWidthAt(y) - spacing * 0.42f);
                if (halfWidth <= spacing * 0.28f) {
                    continue;
                }

                float offset = ((rowIndex & 1) == 0) ? 0.0f : spacing * 0.5f;
                for (float x = -halfWidth + offset; x <= halfWidth + 1.0E-9f; x += spacing) {
                    if (HourglassSimulation.isInsideHourglass(x, y)) {
                        points.add(new float[]{x, y});
                    }
                }
            }

            float[] pointX = new float[points.size()];
            float[] pointY = new float[points.size()];
            for (int i = 0; i < points.size(); i++) {
                float[] point = points.get(i);
                pointX[i] = point[0];
                pointY[i] = point[1];
            }
            return new HourglassDisplayField(pointX, pointY, spacing, (float)(spacing * tuning.influenceRadiusScale()));
        }

        void rebuildSpatialIndex() {
            Arrays.fill(gridHead, -1);
            for (int i = 0; i < pointX.length; i++) {
                int gridX = clamp((int) Math.floor((pointX[i] - gridMinX) / cellSize), 0, gridWidth - 1);
                int gridY = clamp((int) Math.floor((pointY[i] - gridMinY) / cellSize), 0, gridHeight - 1);
                int cell = gridY * gridWidth + gridX;
                nextInCell[i] = gridHead[cell];
                gridHead[cell] = i;
            }
        }

        void accumulate(HourglassSimulation simulation) {
            Arrays.fill(occupancy, 0.0f);
            Arrays.fill(flowEnergy, 0.0f);
            for (int i = 0; i < simulation.getLightCount(); i++) {
                if (!simulation.isLit(i)) {
                    continue;
                }
                float lightX = (float) simulation.getLightX(i);
                float lightY = (float) simulation.getLightY(i);
                float lightFlow = (float) simulation.getLightFlowIntensity(i);
                int minCellX = clamp((int) Math.floor((lightX - influenceRadius - gridMinX) / cellSize), 0, gridWidth - 1);
                int maxCellX = clamp((int) Math.floor((lightX + influenceRadius - gridMinX) / cellSize), 0, gridWidth - 1);
                int minCellY = clamp((int) Math.floor((lightY - influenceRadius - gridMinY) / cellSize), 0, gridHeight - 1);
                int maxCellY = clamp((int) Math.floor((lightY + influenceRadius - gridMinY) / cellSize), 0, gridHeight - 1);
                for (int gy = minCellY; gy <= maxCellY; gy++) {
                    int row = gy * gridWidth;
                    for (int gx = minCellX; gx <= maxCellX; gx++) {
                        int idx = gridHead[row + gx];
                        while (idx >= 0) {
                            float dx = pointX[idx] - lightX;
                            float dy = pointY[idx] - lightY;
                            float distSq = dx * dx + dy * dy;
                            if (distSq < influenceRadiusSquared) {
                                float dist = (float)Math.sqrt(distSq);
                                float w = 1.0f - dist / influenceRadius;
                                float c = w * w;
                                occupancy[idx] += c;
                                flowEnergy[idx] += c * lightFlow;
                            }
                            idx = nextInCell[idx];
                        }
                    }
                }
            }
        }

        void populateContourField(int[] indices, int count, float radius) {
            Arrays.fill(contourField, 0.0f);
            if (count <= 0 || radius <= 1.0E-6f) {
                return;
            }

            float radiusSquared = radius * radius;
            for (int i = 0; i < count; i++) {
                int index = indices[i];
                float strength = CONTOUR_BASE_STRENGTH + reveal[index] * 0.82f;
                accumulateContourPoint(index, radius, radiusSquared, strength);
            }
        }

        private void accumulateContourPoint(int index, float radius, float radiusSquared, float strength) {
            float px = pointX[index];
            float py = pointY[index];
            int minX = clamp((int) Math.floor((px - radius - contourMinX) / contourSpacing), 0, contourWidth - 1);
            int maxX = clamp((int) Math.floor((px + radius - contourMinX) / contourSpacing), 0, contourWidth - 1);
            int minY = clamp((int) Math.floor((py - radius - contourMinY) / contourSpacing), 0, contourHeight - 1);
            int maxY = clamp((int) Math.floor((py + radius - contourMinY) / contourSpacing), 0, contourHeight - 1);

            for (int gy = minY; gy <= maxY; gy++) {
                float sampleY = contourMinY + gy * contourSpacing;
                int row = gy * contourWidth;
                for (int gx = minX; gx <= maxX; gx++) {
                    float sampleX = contourMinX + gx * contourSpacing;
                    float dx = sampleX - px;
                    float dy = sampleY - py;
                    float distanceSquared = dx * dx + dy * dy;
                    if (distanceSquared > radiusSquared) {
                        continue;
                    }

                    float normalized = 1.0f - distanceSquared / radiusSquared;
                    contourField[row + gx] += strength * normalized * normalized;
                }
            }
        }
    }
}

