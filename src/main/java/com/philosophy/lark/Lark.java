package com.philosophy.lark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import com.gluonhq.attach.accelerometer.AccelerometerService;

public final class Lark extends Application {
    private static final String PALETTE_PREFERENCE_KEY = "display.palette";
    private static final float TAP_MAX_DISTANCE = 18.0f;
    private static final long TAP_MAX_DURATION_NANOS = 220_000_000L;
    private static final long LONG_PRESS_TRIGGER_NANOS = 260_000_000L;
    private static final long SYNTHETIC_MOUSE_SUPPRESSION_NANOS = 700_000_000L;
    private static final float VACUUM_RADIUS_WORLD = 0.110f;
    private static final float VACUUM_EDGE_WIDTH_WORLD = 0.080f;
    private static final float VACUUM_FORCE_STRENGTH = 3.4f;

    private final Preferences preferences = Preferences.userNodeForPackage(Lark.class);
    private ColorPalette palette;
    private boolean mouseHoverActive;
    private boolean mousePressed;
    private float mousePressX;
    private float mousePressY;
    private float mouseCurrentX;
    private float mouseCurrentY;
    private long mousePressNanos;
    private boolean mouseTapCandidate;
    private boolean mouseLongPressActive;
    private boolean touchPressed;
    private float touchPressX;
    private float touchPressY;
    private float touchCurrentX;
    private float touchCurrentY;
    private long touchPressNanos;
    private boolean touchTapCandidate;
    private boolean touchLongPressActive;
    private long suppressMouseTapUntilNanos;

    private static final float REVEAL_START = 0.11f;
    private static final float REVEAL_END = 0.62f;
    private static final float CONTOUR_GRID_SCALE = 0.5f;
    private static final float CONTOUR_BASE_STRENGTH = 0.18f;
    private static final float THROAT_MASK_TRIM_LINE_SCALE = 3.10f;
    private static final float THROAT_MASK_TRIM_WORLD_SCALE = 0.170f;

    @Override
    public void start(Stage stage) {
        palette = loadSavedPalette();
        savePalette();

        LarkController.SimulationTuning simulationTuning = LarkController.simulationTuning();
        LarkController.DisplayTuning displayTuning = LarkController.displayTuning();
        HourglassSimulation simulation = new HourglassSimulation(LarkController.DEFAULT_LIT_COUNT, 42L, simulationTuning);
        GravityController gravityController = new GravityController();
        HourglassDisplayField displayField = HourglassDisplayField.create(simulation, displayTuning);

        Canvas frameCanvas = new Canvas(LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT);
        Canvas fluidCanvas = new Canvas(LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT);
        StackPane root = new StackPane(frameCanvas, fluidCanvas);

        frameCanvas.widthProperty().bind(root.widthProperty());
        frameCanvas.heightProperty().bind(root.heightProperty());
        fluidCanvas.widthProperty().bind(root.widthProperty());
        fluidCanvas.heightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT, palette.background());
        applyPalette(root, scene);
        Runnable switchPalette = () -> {
            palette = ColorPalette.nextAfter(palette);
            savePalette();
            applyPalette(root, scene);
            redrawStaticLayer(frameCanvas.getGraphicsContext2D());
            render(fluidCanvas.getGraphicsContext2D(), simulation, gravityController, displayField);
            root.requestFocus();
        };

        scene.setOnMousePressed(event -> {
            if (isSyntheticMouseSuppressed()) {
                return;
            }
            float sceneX = (float)event.getSceneX();
            float sceneY = (float)event.getSceneY();
            beginMouseTap(sceneX, sceneY);
            mouseHoverActive = true;
            gravityController.updateFromPointer(sceneX, sceneY, (float)scene.getWidth(), (float)scene.getHeight());
        });
        scene.setOnMouseMoved(event -> {
            if (isSyntheticMouseSuppressed()) {
                return;
            }
            updateMouseHover((float)event.getSceneX(), (float)event.getSceneY());
        });
        scene.setOnMouseDragged(event -> {
            if (isSyntheticMouseSuppressed()) {
                return;
            }
            mouseCurrentX = (float)event.getSceneX();
            mouseCurrentY = (float)event.getSceneY();
            mouseHoverActive = true;
            if (mousePressed) {
                gravityController.updateFromPointer(
                        mouseCurrentX, mouseCurrentY, (float)scene.getWidth(), (float)scene.getHeight());
            }
            mouseTapCandidate &= isTapWithinThreshold(mousePressX, mousePressY, mousePressNanos,
                    mouseCurrentX, mouseCurrentY);
        });
        scene.setOnMouseReleased(event -> {
            if (isSyntheticMouseSuppressed()) {
                mousePressed = false;
                mouseLongPressActive = false;
                mouseTapCandidate = false;
                return;
            }
            mouseCurrentX = (float)event.getSceneX();
            mouseCurrentY = (float)event.getSceneY();
            mouseHoverActive = true;
            if (System.nanoTime() < suppressMouseTapUntilNanos) {
                mousePressed = false;
                mouseLongPressActive = false;
                mouseTapCandidate = false;
                gravityController.releasePointer();
                return;
            }
            mouseLongPressActive = isLongPress(mousePressNanos, System.nanoTime());
            if (mouseTapCandidate && !mouseLongPressActive
                    && isTapWithinThreshold(mousePressX, mousePressY, mousePressNanos, mouseCurrentX, mouseCurrentY)) {
                switchPalette.run();
            }
            mousePressed = false;
            mouseLongPressActive = false;
            mouseTapCandidate = false;
            gravityController.releasePointer();
        });
        scene.setOnMouseExited(event -> {
            mouseHoverActive = false;
            mousePressed = false;
            mouseLongPressActive = false;
            mouseTapCandidate = false;
            gravityController.releasePointer();
        });
        scene.setOnTouchPressed(event -> {
            float sceneX = (float)event.getTouchPoint().getSceneX();
            float sceneY = (float)event.getTouchPoint().getSceneY();
            beginTouchInteraction(sceneX, sceneY, System.nanoTime());
        });
        scene.setOnTouchMoved(event -> {
            float sceneX = (float)event.getTouchPoint().getSceneX();
            float sceneY = (float)event.getTouchPoint().getSceneY();
            touchCurrentX = sceneX;
            touchCurrentY = sceneY;
            touchTapCandidate &= isTapWithinThreshold(touchPressX, touchPressY, touchPressNanos, sceneX, sceneY);
        });
        scene.setOnTouchReleased(event -> {
            float sceneX = (float)event.getTouchPoint().getSceneX();
            float sceneY = (float)event.getTouchPoint().getSceneY();
            if (completeTouchInteraction(sceneX, sceneY, System.nanoTime())) {
                switchPalette.run();
            }
        });
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
                HourglassSimulation.RadialForce radialForce = resolveActiveRadialForce(
                        (float)scene.getWidth(), (float)scene.getHeight());

                gravityController.step(deltaSeconds);
                simulation.step(deltaSeconds,
                        gravityController.currentX(),
                        gravityController.currentY(),
                        gravityController.agitation(),
                        gravityController.inertiaX(),
                        gravityController.inertiaY(),
                        radialForce);
                render(fluidCanvas.getGraphicsContext2D(), simulation, gravityController, displayField);
            }
        };
        timer.start();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void applyPalette(StackPane root, Scene scene) {
        root.setBackground(new Background(new BackgroundFill(palette.background(), CornerRadii.EMPTY, Insets.EMPTY)));
        scene.setFill(palette.background());
    }

    private ColorPalette loadSavedPalette() {
        return ColorPalette.fromNameOrRandom(preferences.get(PALETTE_PREFERENCE_KEY, null));
    }

    private void savePalette() {
        if (palette == null) {
            return;
        }

        preferences.put(PALETTE_PREFERENCE_KEY, palette.name());
        try {
            preferences.flush();
        } catch (BackingStoreException ignored) {
            // 如果平台暂时无法立即刷盘，至少保留本次运行中的选择。
        }
    }

    private void beginMouseTap(float sceneX, float sceneY) {
        mousePressed = true;
        mousePressX = sceneX;
        mousePressY = sceneY;
        mouseCurrentX = sceneX;
        mouseCurrentY = sceneY;
        mousePressNanos = System.nanoTime();
        mouseLongPressActive = false;
        mouseTapCandidate = true;
    }

    void beginTouchInteraction(float sceneX, float sceneY, long nowNanos) {
        suppressSyntheticMouse(nowNanos);
        beginTouchTap(sceneX, sceneY, nowNanos);
    }

    boolean completeTouchInteraction(float sceneX, float sceneY, long nowNanos) {
        touchCurrentX = sceneX;
        touchCurrentY = sceneY;
        suppressSyntheticMouse(nowNanos);
        touchLongPressActive = isLongPress(touchPressNanos, nowNanos);
        boolean shouldSwitchPalette = touchTapCandidate && !touchLongPressActive
                && isTapWithinThreshold(touchPressX, touchPressY, touchPressNanos, sceneX, sceneY);
        touchPressed = false;
        touchLongPressActive = false;
        touchTapCandidate = false;
        return shouldSwitchPalette;
    }

    void updateMouseHover(float sceneX, float sceneY) {
        mouseHoverActive = true;
        mouseCurrentX = sceneX;
        mouseCurrentY = sceneY;
    }

    private void beginTouchTap(float sceneX, float sceneY, long nowNanos) {
        touchPressed = true;
        touchPressX = sceneX;
        touchPressY = sceneY;
        touchCurrentX = sceneX;
        touchCurrentY = sceneY;
        touchPressNanos = nowNanos;
        touchLongPressActive = false;
        touchTapCandidate = true;
    }

    private boolean isTapWithinThreshold(float pressX, float pressY, long pressNanos, float releaseX, float releaseY) {
        float dx = releaseX - pressX;
        float dy = releaseY - pressY;
        float maxDistanceSquared = TAP_MAX_DISTANCE * TAP_MAX_DISTANCE;
        long duration = System.nanoTime() - pressNanos;
        return dx * dx + dy * dy <= maxDistanceSquared && duration <= TAP_MAX_DURATION_NANOS;
    }

    HourglassSimulation.RadialForce resolveActiveRadialForce(float sceneWidth, float sceneHeight) {
        if (touchPressed) {
            HourglassSimulation.RadialForce touchForce = resolveTouchRadialForce(sceneWidth, sceneHeight);
            if (touchForce != null) {
                return touchForce;
            }
        }
        return resolveMouseRadialForce(sceneWidth, sceneHeight);
    }

    private HourglassSimulation.RadialForce resolveMouseRadialForce(float sceneWidth, float sceneHeight) {
        if (isSyntheticMouseSuppressed()) {
            return null;
        }
        if (!mouseHoverActive) {
            return null;
        }
        return buildRadialForce(mouseCurrentX, mouseCurrentY, sceneWidth, sceneHeight);
    }

    private HourglassSimulation.RadialForce resolveTouchRadialForce(float sceneWidth, float sceneHeight) {
        if (!touchPressed) {
            return null;
        }
        return buildRadialForce(touchCurrentX, touchCurrentY, sceneWidth, sceneHeight);
    }

    private boolean isLongPress(long pressNanos, long now) {
        return now - pressNanos >= LONG_PRESS_TRIGGER_NANOS;
    }

    private boolean isSyntheticMouseSuppressed() {
        return System.nanoTime() < suppressMouseTapUntilNanos;
    }

    private void suppressSyntheticMouse(long nowNanos) {
        suppressMouseTapUntilNanos = nowNanos + SYNTHETIC_MOUSE_SUPPRESSION_NANOS;
        clearMousePointerState();
    }

    private void clearMousePointerState() {
        mouseHoverActive = false;
        mousePressed = false;
        mouseLongPressActive = false;
        mouseTapCandidate = false;
    }

    private HourglassSimulation.RadialForce buildRadialForce(float sceneX, float sceneY,
                                                             float sceneWidth, float sceneHeight) {
        if (sceneWidth <= 0.0f || sceneHeight <= 0.0f) {
            return null;
        }

        HourglassSimulation.Vector2 worldCenter = scenePointToWorld(sceneX, sceneY, sceneWidth, sceneHeight);
        if (!HourglassSimulation.isInsideHourglass(worldCenter.x(), worldCenter.y())) {
            return null;
        }
        return new HourglassSimulation.RadialForce(
                worldCenter.x(),
                worldCenter.y(),
                VACUUM_RADIUS_WORLD,
                VACUUM_EDGE_WIDTH_WORLD,
                VACUUM_FORCE_STRENGTH);
    }

    private HourglassSimulation.Vector2 scenePointToWorld(float sceneX, float sceneY, float sceneWidth, float sceneHeight) {
        float scale = computeWorldScale(sceneWidth, sceneHeight);
        double worldX = (sceneX - sceneWidth * 0.5f) / Math.max(scale, 1.0E-4f);
        double worldY = (sceneY - sceneHeight * 0.5f) / Math.max(scale, 1.0E-4f);
        return new HourglassSimulation.Vector2(worldX, worldY);
    }

    private void render(GraphicsContext gc, HourglassSimulation simulation,
                        GravityController gravityController, HourglassDisplayField displayField) {
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
        drawLights(gc, simulation, displayField, gravityController.agitation());
        float width = (float)gc.getCanvas().getWidth();
        float height = (float)gc.getCanvas().getHeight();
        float scale = computeWorldScale(width, height);
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        drawHourglassMask(gc, centerX, centerY, scale);
        drawHourglassFrame(gc, centerX, centerY, scale);
    }


    private float computeWorldScale(float width, float height) {
        return (float)Math.min((width - 56.0f) / (HourglassSimulation.MAX_HALF_WIDTH * 2.15f),
                (height - 56.0f) / (HourglassSimulation.WORLD_BOTTOM - HourglassSimulation.WORLD_TOP));
    }

    // 沿沙漏边界外部画一圈背景色遮罩，遮住溢出边界的粒子
    private void drawHourglassMask(GraphicsContext gc, float centerX, float centerY, float scale) {
        float width = (float)gc.getCanvas().getWidth();
        float height = (float)gc.getCanvas().getHeight();
        float radius = (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float topCenterY = centerY - (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float bottomCenterY = centerY + (float)(HourglassSimulation.DIAMOND_RADIUS * scale);

        gc.save();
        gc.setFill(palette.background());
        gc.setFillRule(FillRule.EVEN_ODD);
        gc.beginPath();
        gc.rect(0.0, 0.0, width, height);
        appendDiamondPath(gc, centerX, topCenterY, radius);
        appendDiamondPath(gc, centerX, bottomCenterY, radius);
        gc.fill();
        gc.restore();

        gc.save();
        gc.setStroke(palette.background());
        gc.setLineCap(StrokeLineCap.ROUND);
        float lineWidth = (float)Math.max(8.0f, scale * 0.048f);
        float throatTrim = Math.max(lineWidth * THROAT_MASK_TRIM_LINE_SCALE, scale * THROAT_MASK_TRIM_WORLD_SCALE);
        gc.setLineWidth(lineWidth);

        float topX = centerX;
        float topY = topCenterY - radius;
        float throatX = centerX;
        float throatY = centerY;
        float bottomX = centerX;
        float bottomY = bottomCenterY + radius;
        float rightTopX = centerX + radius;
        float rightTopY = topCenterY;
        float leftTopX = centerX - radius;
        float leftTopY = topCenterY;
        float rightBottomX = centerX + radius;
        float rightBottomY = bottomCenterY;
        float leftBottomX = centerX - radius;
        float leftBottomY = bottomCenterY;

        strokeTrimmedSegment(gc, topX, topY, rightTopX, rightTopY, 0.0f, 0.0f);
        strokeTrimmedSegment(gc, topX, topY, leftTopX, leftTopY, 0.0f, 0.0f);
        strokeTrimmedSegment(gc, rightTopX, rightTopY, throatX, throatY, 0.0f, throatTrim);
        strokeTrimmedSegment(gc, leftTopX, leftTopY, throatX, throatY, 0.0f, throatTrim);
        strokeTrimmedSegment(gc, rightBottomX, rightBottomY, bottomX, bottomY, 0.0f, 0.0f);
        strokeTrimmedSegment(gc, leftBottomX, leftBottomY, bottomX, bottomY, 0.0f, 0.0f);
        strokeTrimmedSegment(gc, throatX, throatY, rightBottomX, rightBottomY, throatTrim, 0.0f);
        strokeTrimmedSegment(gc, throatX, throatY, leftBottomX, leftBottomY, throatTrim, 0.0f);
        gc.restore();
    }

    private void redrawStaticLayer(GraphicsContext gc) {
        float width = (float)gc.getCanvas().getWidth();
        float height = (float)gc.getCanvas().getHeight();
        float scale = (float)Math.min((width - 56.0f) / (HourglassSimulation.MAX_HALF_WIDTH * 2.15f),
                (height - 56.0f) / (HourglassSimulation.WORLD_BOTTOM - HourglassSimulation.WORLD_TOP));
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        gc.setFill(palette.background());
        gc.fillRect(0, 0, width, height);
        drawHourglassFrame(gc, centerX, centerY, scale);
    }

    private void drawHourglassFrame(GraphicsContext gc, float centerX, float centerY, float scale) {
        float radius = (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float topCenterY = centerY - (float)(HourglassSimulation.DIAMOND_RADIUS * scale);
        float bottomCenterY = centerY + (float)(HourglassSimulation.DIAMOND_RADIUS * scale);

        gc.setStroke(palette.foreground());
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth((float)Math.max(2.0f, scale * 0.012f));

        gc.beginPath();
        appendDiamondPath(gc, centerX, topCenterY, radius);
        gc.stroke();

        gc.beginPath();
        appendDiamondPath(gc, centerX, bottomCenterY, radius);
        gc.stroke();
    }

    private void appendDiamondPath(GraphicsContext gc, float centerX, float centerY, float radius) {
        gc.moveTo(centerX, centerY - radius);
        gc.lineTo(centerX + radius, centerY);
        gc.lineTo(centerX, centerY + radius);
        gc.lineTo(centerX - radius, centerY);
        closePath(gc);
    }

    private void strokeTrimmedSegment(GraphicsContext gc, float startX, float startY, float endX, float endY,
                                      float trimStart, float trimEnd) {
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float)Math.hypot(dx, dy);
        if (length <= 1.0E-4f || trimStart + trimEnd >= length - 1.0E-4f) {
            return;
        }

        float invLength = 1.0f / length;
        float fromX = startX + dx * (trimStart * invLength);
        float fromY = startY + dy * (trimStart * invLength);
        float toX = endX - dx * (trimEnd * invLength);
        float toY = endY - dy * (trimEnd * invLength);

        gc.beginPath();
        gc.moveTo(fromX, fromY);
        gc.lineTo(toX, toY);
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
                centerX, centerY, scale, softParticleSize, palette.liquidSoft(), 0.18f, 1.22f);
        drawBucket(gc, displayField, displayField.solidIndices, solidCount,
                centerX, centerY, scale, particleSize, palette.liquidSolid(), 0.22f, 1.08f);
        drawBucket(gc, displayField, displayField.flowIndices, flowCount,
                centerX, centerY, scale, flowParticleSize, palette.liquidFlow(), 0.20f, 1.12f);
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

