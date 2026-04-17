package com.philosophy.lark;

import java.util.ArrayList;
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

public class Lark extends Application {
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color FOREGROUND = Color.web("#475A55");
    private static final Color LIQUID_SOFT = Color.web("#3F504B", 0.30);
    private static final Color LIQUID_SOLID = Color.web("#3E504B", 0.98);
    private static final Color LIQUID_FLOW = Color.web("#587069", 0.98);
    private static final Color LIQUID_SHADOW = Color.web("#2B3834", 0.99);
    private static final double REVEAL_START = 0.11;
    private static final double REVEAL_END = 0.62;

    @Override
    public void start(Stage stage) {
        HourglassSimulation simulation = new HourglassSimulation(LarkController.DEFAULT_LIT_COUNT, 42L);
        GravityController gravityController = new GravityController();
        HourglassDisplayField displayField = HourglassDisplayField.create(simulation);

        Canvas canvas = new Canvas(LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT);
        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: white;");

        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, LarkController.DEFAULT_WIDTH, LarkController.DEFAULT_HEIGHT, BACKGROUND);
        scene.setOnMouseMoved(event -> gravityController.updateFromPointer(
                event.getSceneX(), event.getSceneY(), scene.getWidth(), scene.getHeight()));
        scene.setOnMouseDragged(event -> gravityController.updateFromPointer(
                event.getSceneX(), event.getSceneY(), scene.getWidth(), scene.getHeight()));
        scene.setOnMouseExited(event -> gravityController.releasePointer());
        scene.setOnTouchPressed(event -> gravityController.updateFromPointer(
                event.getTouchPoint().getSceneX(), event.getTouchPoint().getSceneY(), scene.getWidth(), scene.getHeight()));
        scene.setOnTouchMoved(event -> gravityController.updateFromPointer(
                event.getTouchPoint().getSceneX(), event.getTouchPoint().getSceneY(), scene.getWidth(), scene.getHeight()));
        scene.setOnTouchReleased(event -> gravityController.releasePointer());
        scene.setOnKeyPressed(gravityController::handleKeyPressed);
        scene.setOnKeyReleased(gravityController::handleKeyReleased);

        stage.setTitle("Lark");
        stage.setScene(scene);
        stage.setMinWidth(340);
        stage.setMinHeight(560);
        stage.show();

        root.requestFocus();

        AccelerometerService.create().ifPresent(service -> {
            service.accelerationProperty().addListener((obs, ov, nv) -> {
                if (nv == null) {
                    gravityController.clearSensorGravity();
                    return;
                }
                gravityController.updateSensorAcceleration(nv.getX(), nv.getY(), nv.getZ());
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
                    render(canvas.getGraphicsContext2D(), simulation, gravityController, displayField);
                    return;
                }

                double deltaSeconds = Math.min((now - lastTick) / 1_000_000_000.0, 1.0 / 25.0);
                lastTick = now;

                gravityController.step(deltaSeconds);
                simulation.step(deltaSeconds,
                        gravityController.current(),
                        gravityController.agitation(),
                        gravityController.inertiaDirection());
                render(canvas.getGraphicsContext2D(), simulation, gravityController, displayField);
            }
        };
        timer.start();
    }

    public static void main(String[] args) {
        launch();
    }

    private void render(GraphicsContext gc, HourglassSimulation simulation,
                        GravityController gravityController, HourglassDisplayField displayField) {
        double width = gc.getCanvas().getWidth();
        double height = gc.getCanvas().getHeight();
        double scale = Math.min((width - 56.0) / (HourglassSimulation.MAX_HALF_WIDTH * 2.15),
                (height - 56.0) / (HourglassSimulation.WORLD_BOTTOM - HourglassSimulation.WORLD_TOP));
        double centerX = width * 0.5;
        double centerY = height * 0.5;
        double baseLightSize = Math.max(1.8, simulation.getLightSize() * scale);
        double lightSize = baseLightSize * (1.0 + gravityController.agitation() * 0.16);

        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);

        gc.save();
        clipHourglassInterior(gc, centerX, centerY, scale);
        drawLights(gc, simulation, displayField, centerX, centerY, lightSize, scale);
        gc.restore();
        drawHourglassFrame(gc, centerX, centerY, scale);
    }

    private void clipHourglassInterior(GraphicsContext gc, double centerX, double centerY, double scale) {
        double radius = HourglassSimulation.DIAMOND_RADIUS * scale;
        double topCenterY = centerY - HourglassSimulation.DIAMOND_RADIUS * scale;
        double bottomCenterY = centerY + HourglassSimulation.DIAMOND_RADIUS * scale;

        gc.beginPath();
        gc.moveTo(centerX, topCenterY - radius);
        gc.lineTo(centerX + radius, topCenterY);
        gc.lineTo(centerX, topCenterY + radius);
        gc.lineTo(centerX - radius, topCenterY);
        closePath(gc);
        gc.moveTo(centerX, bottomCenterY - radius);
        gc.lineTo(centerX + radius, bottomCenterY);
        gc.lineTo(centerX, bottomCenterY + radius);
        gc.lineTo(centerX - radius, bottomCenterY);
        closePath(gc);
        gc.clip();
    }

    private void drawHourglassFrame(GraphicsContext gc, double centerX, double centerY, double scale) {
        double radius = HourglassSimulation.DIAMOND_RADIUS * scale;
        double topCenterY = centerY - HourglassSimulation.DIAMOND_RADIUS * scale;
        double bottomCenterY = centerY + HourglassSimulation.DIAMOND_RADIUS * scale;
        
        gc.setStroke(FOREGROUND);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth(Math.max(2.0, scale * 0.012));

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
                            HourglassDisplayField displayField,
                            double centerX, double centerY, double lightSize, double scale) {
        double cellPitch = simulation.getLightSpacing() * scale;
        double particleSize = Math.max(2.0, Math.min(lightSize * 0.72, cellPitch * 0.74));
        double haloSize = particleSize * 1.16;
        double influenceRadius = displayField.influenceRadius();
        double influenceRadiusSquared = influenceRadius * influenceRadius;

        for (DisplayPoint point : displayField.points()) {
            double occupancy = 0.0;
            double flowEnergy = 0.0;
            for (int i = 0; i < simulation.getLightCount(); i++) {
                if (!simulation.isLit(i)) {
                    continue;
                }
                double dx = simulation.getLightX(i) - point.x();
                double dy = simulation.getLightY(i) - point.y();
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= influenceRadiusSquared) {
                    continue;
                }

                double distance = Math.sqrt(distanceSquared);
                double weight = 1.0 - distance / influenceRadius;
                double contribution = weight * weight;
                occupancy += contribution;
                flowEnergy += contribution * simulation.getLightFlowIntensity(i);
            }

            double filled = clamp(occupancy * 0.82, 0.0, 1.0);
            double reveal = smoothstep(REVEAL_START, REVEAL_END, filled);
            if (reveal <= 0.01) {
                continue;
            }

            double x = centerX + point.x() * scale;
            double y = centerY + point.y() * scale;

            gc.setFill(withOpacity(LIQUID_SOLID, reveal));
            gc.fillRect(x - particleSize * 0.5, y - particleSize * 0.5, particleSize, particleSize);
        }
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge0 == edge1) {
            return value >= edge1 ? 1.0 : 0.0;
        }
        double normalized = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return normalized * normalized * (3.0 - 2.0 * normalized);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Color withOpacity(Color color, double opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(opacity, 0.0, 1.0));
    }

    private record DisplayPoint(double x, double y) {
    }

    private record HourglassDisplayField(DisplayPoint[] points, double influenceRadius) {
        private static HourglassDisplayField create(HourglassSimulation simulation) {
            double spacing = simulation.getLightSpacing();
            double rowStep = spacing * Math.sqrt(3.0) * 0.5;
            List<DisplayPoint> points = new ArrayList<>();
            int rowIndex = 0;

            for (double y = HourglassSimulation.WORLD_TOP + spacing * 1.25;
                 y <= HourglassSimulation.WORLD_BOTTOM - spacing * 1.25;
                 y += rowStep, rowIndex++) {
                double halfWidth = HourglassSimulation.halfWidthAt(y) - spacing * 0.38;
                if (halfWidth <= spacing * 0.28) {
                    continue;
                }

                double offset = ((rowIndex & 1) == 0) ? 0.0 : spacing * 0.5;
                for (double x = -halfWidth + offset; x <= halfWidth + 1.0E-9; x += spacing) {
                    if (HourglassSimulation.isInsideHourglass(x, y)) {
                        points.add(new DisplayPoint(x, y));
                    }
                }
            }

            return new HourglassDisplayField(points.toArray(new DisplayPoint[0]), spacing * 1.55);
        }
    }
}
