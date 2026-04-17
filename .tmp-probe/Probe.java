import com.philosophy.lark.HourglassSimulation;
public class Probe {
    public static void main(String[] args) {
        HourglassSimulation simulation = new HourglassSimulation(360, 5L);
        for (int step = 0; step < 4200; step++) {
            simulation.step(1.0 / 120.0, new HourglassSimulation.Vector2(0.0, 1.0));
        }
        System.out.println("topFraction=" + simulation.topFraction());
        double[] ys = {0.02, 0.06, 0.10, 0.14, 0.18, 0.24, 0.32};
        for (double y : ys) {
            System.out.println("y=" + y + " => " + simulation.hasLitCellNear(0.0, y, simulation.getLightSpacing() * 1.8));
        }
    }
}
