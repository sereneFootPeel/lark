package com.philosophy.lark;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javafx.scene.paint.Color;

public record ColorPalette(String name, Color background, Color foreground,
                           Color liquidSoft, Color liquidSolid, Color liquidFlow) {
    private static final List<ColorPalette> PRESETS = List.of(

            new ColorPalette(
                    "Dusk Gold",
                    Color.web("#FFFFFF"),       // 背景：白色
                    Color.web("#1B263B"),       // 主色：深靛蓝（深邃且专业）
                    Color.web("#E0C097", 0.28), // 辅助色：半透明麦穗黄
                    Color.web("#D4B996", 0.98), // 强调色：香槟金（带有奶油感的低饱和黄）
                    Color.web("#778DA9", 0.98)),
            new ColorPalette(
                    "Nocturnal Silk",
                    Color.web("#FFFFFF"),       // 背景：白色
                    Color.web("#2C3E50"),       // 主色：单宁深蓝（经典的低饱和深蓝）
                    Color.web("#C2B280", 0.28), // 辅助色：半透明沙砾金
                    Color.web("#BDB76B", 0.98), // 强调色：卡其金（带有一丝橄榄调的暗黄）
                    Color.web("#8E9775", 0.98)),
            new ColorPalette(
                    "Dusk Crimson",
                    Color.web("#FFFFFF"),       // 背景：白色
                    Color.web("#5D3A3A"),       // 主色：灰调深绯红（低饱和度，深沉且高级）
                    Color.web("#E0C097", 0.28), // 辅助色：半透明麦穗黄（与红调非常搭）
                    Color.web("#D4B996", 0.98), // 强调色：香槟金（与红调组合出轻奢感）
                    Color.web("#A08585", 0.98)),
            new ColorPalette(
                    "Minimalist Slate", // 现代极简：灰冷色系的专业感
                    Color.web("#FFFFFF"),         // 背景：白色
                    Color.web("#5B6771"),         // 前景：深烟蓝
                    Color.web("#A9B4BC", 0.35),   // Soft：轻盈雾灰
                    Color.web("#808E99", 0.98),   // Solid：灰湖蓝
                    Color.web("#99A7B2", 0.98)),  // Flow：冷月色

            new ColorPalette(
                    "Muted Woodland", // 静谧森野：灰绿与苔藓的治愈感
                    Color.web("#FFFFFF"),         // 背景：白色
                    Color.web("#5F6962"),         // 前景：墨黛绿
                    Color.web("#B1BAB3", 0.30),   // Soft：浅苔绿
                    Color.web("#848F87", 0.98),   // Solid：鼠尾草绿
                    Color.web("#9BA69D", 0.98)),  // Flow：青瓷灰

            new ColorPalette(
                    "Twilight Manor",
                    Color.web("#FFFFFF"),         // 背景：白色
                    Color.web("#777E8B"),         // 前景：烟灰蓝
                    Color.web("#B79492", 0.28),   // 柔化：暮色粉
                    Color.web("#9A8C98", 0.98),   // 固体：丁香灰
                    Color.web("#C9ADA7", 0.98)),
            new ColorPalette(
                    "Nordic Meadow",
                    Color.web("#FFFFFF"),
                    Color.web("#4A5D4E"),
                    Color.web("#C6AC8F", 0.28),
                    Color.web("#829399", 0.98),
                    Color.web("#A3B18A", 0.98)),
            new ColorPalette(
                    "Sunset Mist",
                    Color.web("#FFFFFF"),
                    Color.web("#605D66"),
                    Color.web("#B8C0CC", 0.28),
                    Color.web("#D4A373", 0.98),
                    Color.web("#998D9E", 0.98)),
            new ColorPalette(
                    "Urban Slate",
                    Color.web("#FFFFFF"),
                    Color.web("#3D4A5E"),
                    Color.web("#E0B194", 0.28),
                    Color.web("#8DA9C4", 0.98),
                    Color.web("#94A684", 0.98)),
            new ColorPalette(
                    "Desert Sage", // 荒漠鼠尾草：沙杏色、鼠尾草绿与灰紫的碰撞
                    Color.web("#FFFFFF"),         // 背景：白色
                    Color.web("#4E5B52"),         // 前景：深植绿
                    Color.web("#B8A599", 0.28),   // Soft：淡粉陶土
                    Color.web("#8DA399", 0.98),   // Solid：鼠尾草绿
                    Color.web("#9A9ABC", 0.98)),  // Flow：灰紫色

            new ColorPalette(
                    "Midnight Pottery", // 午夜陶艺：深蓝灰、暖陶红与豆沙绿
                    Color.web("#FFFFFF"),         // 背景：白色
                    Color.web("#434C5E"),         // 前景：石板蓝
                    Color.web("#B17769", 0.28),   // Soft：暖陶色
                    Color.web("#9EB39E", 0.98),   // Solid：豆沙绿
                    Color.web("#D4A373", 0.98)),  // Flow：琥珀金

            new ColorPalette(
                    "Tundra Twilight", // 苔原极光：冷矿蓝、浅灰金与烟粉
                    Color.web("#FFFFFF"),         // 背景：白色
                    Color.web("#54626F"),         // 前景：矿物灰
                    Color.web("#A3B18A", 0.28),   // Soft：苔藓绿
                    Color.web("#B1A7A6", 0.98),   // Solid：烟粉色
                    Color.web("#829399", 0.98))// 补色：雾霾蓝



    );


    public static ColorPalette random() {
        return PRESETS.get(ThreadLocalRandom.current().nextInt(PRESETS.size()));
    }

    public static ColorPalette fromNameOrRandom(String name) {
        if (name != null) {
            for (ColorPalette preset : PRESETS) {
                if (preset.name().equals(name)) {
                    return preset;
                }
            }
        }
        return random();
    }

    public static ColorPalette nextAfter(ColorPalette current) {
        if (current == null) {
            return PRESETS.get(0);
        }

        for (int i = 0; i < PRESETS.size(); i++) {
            if (PRESETS.get(i).name().equals(current.name())) {
                return PRESETS.get((i + 1) % PRESETS.size());
            }
        }
        return PRESETS.get(0);
    }
}
