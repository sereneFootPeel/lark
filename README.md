# Lark Hourglass

A minimal hourglass simulation using JavaFX. The app features a simple UI with a white background, dark green lines, and a grid of small squares that light up to simulate sand flow under gravity.

## Features
- Custom UI with JavaFX Canvas
- Fixed grid sand flow simulation
- Rotatable grid (45°) matching hourglass frame
- Gravity direction controlled by mouse, touch, or keyboard
- Core logic in `HourglassSimulation`

## Usage
Run the app:
```powershell
Remove-Item Env:JDK_JAVA_OPTIONS -ErrorAction SilentlyContinue
.\mvnw.cmd javafx:run
```

## Controls
- Mouse move/drag: Change gravity direction
- Touch drag: Simulate device tilt
- Arrow keys/WASD: Fine-tune gravity
- Mobile accelerometer: Gravity follows device tilt

## Structure
- `Lark.java`: App entry and rendering
- `HourglassSimulation.java`: Sand flow simulation
- `LarkController.java`: Default parameters
- `GravityController.java`: Input and sensor handling

## Android/iOS Notes
- Uses Gluon Attach for accelerometer support
- Android/iOS builds via `gluonfx-maven-plugin` (`android`/`ios` profiles)
- See source for platform-specific build instructions
