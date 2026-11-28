package iuh.fit.postservice.application.service.insight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PCA chiếu vector 768 chiều xuống 2 chiều")
class PcaProjectorTest {

    private static final int D = 768;

    /** Hai nhóm bài quanh hai "chủ đề" khác nhau, cộng nhiễu nhỏ. */
    private static float[][] twoTopics(int perTopic, long seed) {
        Random rnd = new Random(seed);
        float[] a = new float[D], b = new float[D];
        for (int i = 0; i < D; i++) {
            a[i] = (float) rnd.nextGaussian();
            b[i] = (float) rnd.nextGaussian();
        }
        float[][] out = new float[perTopic * 2][D];
        for (int r = 0; r < out.length; r++) {
            float[] base = r < perTopic ? a : b;
            for (int i = 0; i < D; i++) out[r][i] = base[i] + 0.15f * (float) rnd.nextGaussian();
        }
        return out;
    }

    @Test
    @DisplayName("Hai trục chính có độ dài 1 và vuông góc với nhau")
    void axesAreOrthonormal() {
        PcaProjector.Basis b = PcaProjector.fit(twoTopics(20, 1));
        assertEquals(1.0, PcaProjector.dot(b.pc1(), b.pc1()), 1e-6);
        assertEquals(1.0, PcaProjector.dot(b.pc2(), b.pc2()), 1e-6);
        assertEquals(0.0, PcaProjector.dot(b.pc1(), b.pc2()), 1e-6);
        assertTrue(b.explained1() >= b.explained2());
        assertTrue(b.explained1() + b.explained2() <= 1.0 + 1e-9);
    }

    @Test
    @DisplayName("Hai chủ đề tách nhau trên trục thứ nhất; trục này giữ phần lớn phương sai")
    void separatesTopics() {
        float[][] data = twoTopics(20, 2);
        PcaProjector.Basis b = PcaProjector.fit(data);
        assertTrue(b.explained1() > 0.5, "trục 1 phải giữ phần lớn phương sai, thực tế " + b.explained1());
        for (int r = 0; r < 20; r++) {
            double xa = b.project(data[r])[0];
            double xb = b.project(data[r + 20])[0];
            assertTrue(Math.signum(xa) != Math.signum(xb), "hai chủ đề phải nằm hai phía trục 1");
        }
    }

    @Test
    @DisplayName("Cùng dữ liệu thì cùng bản đồ (không lật dấu giữa các lần tính)")
    void deterministic() {
        float[][] data = twoTopics(15, 3);
        PcaProjector.Basis b1 = PcaProjector.fit(data);
        PcaProjector.Basis b2 = PcaProjector.fit(data);
        assertArrayEquals(b1.project(data[0]), b2.project(data[0]), 1e-12);
    }

    @Test
    @DisplayName("Vector sở thích nằm giữa hai chủ đề khi người dùng quan tâm đều cả hai")
    void mixedProfileLiesBetween() {
        float[][] data = twoTopics(20, 4);
        PcaProjector.Basis b = PcaProjector.fit(data);
        float[] mix = new float[D];
        for (int i = 0; i < D; i++) mix[i] = data[0][i] / norm(data[0]) + data[20][i] / norm(data[20]);
        double xa = b.project(data[0])[0], xb = b.project(data[20])[0], xm = b.project(mix)[0];
        assertTrue(xm > Math.min(xa, xb) && xm < Math.max(xa, xb));
    }

    private static float norm(float[] v) {
        double s = 0;
        for (float f : v) s += f * f;
        return (float) Math.sqrt(s);
    }
}
