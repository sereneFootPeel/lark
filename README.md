# Lark Hourglass

一个极简沙漏原型：白色背景、深灰绿色线条、上下两个角对角正方形、斜向小方块网格随重力逐步点亮。

## 当前实现

- `JavaFX Canvas` 自绘 UI
- 固定网格点亮式沙流模拟
- 小方块与沙漏外框方向一致，统一旋转 45°
- 鼠标、触摸、方向键可模拟手机倾斜
- JVM 属性 `lark.smokeTest=true` 可自动跑一轮仿真并退出，便于验证运行环境
- 核心逻辑位于 `HourglassSimulation`

## 运行

```powershell
.\mvnw.cmd test
Remove-Item Env:JDK_JAVA_OPTIONS -ErrorAction SilentlyContinue
.\mvnw.cmd javafx:run
$env:JAVA_TOOL_OPTIONS='-Dlark.smokeTest=true'
.\mvnw.cmd javafx:run
```

如果你之前在 IDE 里遇到 `module-info.java: 找不到模块: javafx.controls`，现在这份工程已经改成非模块化运行方式，不再依赖 `module-info.java`。

## 交互

- 鼠标移动 / 拖拽：改变重力方向
- 触摸拖动：模拟手机倾斜
- 方向键 / WASD：辅助微调重力方向
- 手机加速度计：根据手机倾斜方向决定重力方向；接近平放时自动衰减到无重力

## 结构

- `src/main/java/com/philosophy/lark/Lark.java`：应用入口与渲染
- `src/main/java/com/philosophy/lark/HourglassSimulation.java`：固定网格沙流模拟
- `src/main/java/com/philosophy/lark/LarkController.java`：应用默认参数
- `src/main/java/com/philosophy/lark/GravityController.java`：桌面输入与传感器入口
- `src/test/java/com/philosophy/lark/HourglassSimulationTest.java`：边界、守恒、重力响应测试

## Android / iOS 兼容说明

当前工程已经接入 Gluon Attach 的加速度计能力，并配置了 `gluonfx-maven-plugin`：

- `GravityController#updateSensorAcceleration(...)` 会把手机倾斜转换成屏幕内的重力向量
- 手机接近平放时，屏幕内重力会衰减为 `0`，也就是“没有重力”
- Android 打包入口：`android` profile
- iOS 打包入口：`ios` profile

### 本地先验证共享逻辑

```powershell
.\mvnw.cmd clean test
```

### Android 打包

需要先准备：

- GraalVM（并设置 `GRAALVM_HOME`）
- Android SDK / NDK（通常由 Android Studio 安装）
- `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`

构建命令：

```powershell
.\mvnw.cmd -Pandroid -DskipTests gluonfx:build
.\mvnw.cmd -Pandroid -DskipTests gluonfx:package
```

### iOS 打包

iOS 需要 macOS + Xcode，Windows 下不能直接产出可安装的 iOS 包。

在 macOS 上可使用：

```powershell
.\mvnw.cmd -Pios -DskipTests gluonfx:build
.\mvnw.cmd -Pios -DskipTests gluonfx:package
```

