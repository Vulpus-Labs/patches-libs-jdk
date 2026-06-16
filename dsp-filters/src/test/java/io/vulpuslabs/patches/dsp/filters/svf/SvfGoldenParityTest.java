package io.vulpuslabs.patches.dsp.filters.svf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vulpuslabs.patches.dsp.core.DspMath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Golden-vector parity against the real Rust {@code patches_dsp::svf::SvfKernel}.
 *
 * <p>The vectors in {@code golden/svf_golden.json} were dumped from the Rust
 * kernel itself (see ADR 0001's golden-vector methodology). This is the
 * backbone parity check: each scenario replays the same input through the Java
 * port and asserts the {@code lp}/{@code hp}/{@code bp} outputs match.
 */
class SvfGoldenParityTest {

    private static final float SR = 48_000.0f;
    // Output tolerance. The only platform-dependent step is the one-off
    // sin/pow coefficient evaluation; the recurrence itself is identical f32
    // arithmetic, so divergence stays far below this bound.
    private static final float TOL = 1e-4f;
    private static final float COEFF_TOL = 1e-6f;

    @Test
    void impulseScenarioMatchesRust() {
        Scenario s = load("impulse_fc1000_q0.5");
        // Coefficient port parity: Java's svf_f / q_to_damp reproduce Rust's.
        assertTrue(Math.abs(Svf.svfF(1_000.0f, SR) - s.f) < COEFF_TOL, "svf_f mismatch");
        assertTrue(Math.abs(Svf.qToDamp(0.5f) - s.d) < COEFF_TOL, "q_to_damp mismatch");
        replayStatic(s);
    }

    @Test
    void sineScenarioMatchesRust() {
        Scenario s = load("sine220_fc800_q0.7");
        assertTrue(Math.abs(Svf.svfF(800.0f, SR) - s.f) < COEFF_TOL, "svf_f mismatch");
        assertTrue(Math.abs(Svf.qToDamp(0.7f) - s.d) < COEFF_TOL, "q_to_damp mismatch");
        replayStatic(s);
    }

    @Test
    void rampedSweepMatchesRust() {
        Scenario s = load("ramped_sweep_q0.6");
        // Reproduce the exact driving schedule used to dump the golden vector.
        float d = s.d;
        float c0 = 16.351_599f;
        float baseVoct = 5.0f;
        int interval = 32;
        float f0 = Svf.svfF(DspMath.clamp((float) (c0 * Math.pow(2.0, baseVoct)), 1.0f, SR * 0.499f), SR);
        SvfKernel k = new SvfKernel(f0, d, interval);
        int total = s.x.length;
        for (int n = 0; n < total; n++) {
            if (n % interval == 0) {
                float env = ((float) n / total) * 3.0f;
                float fc = DspMath.clamp((float) (c0 * Math.pow(2.0, baseVoct + env)), 1.0f, SR * 0.499f);
                k.beginRamp(Svf.svfF(fc, SR), d);
            }
            k.tick(s.x[n]);
            assertClose(s, n, k);
        }
    }

    private void replayStatic(Scenario s) {
        SvfKernel k = new SvfKernel(s.f, s.d);
        for (int n = 0; n < s.x.length; n++) {
            k.tick(s.x[n]);
            assertClose(s, n, k);
        }
    }

    private void assertClose(Scenario s, int n, SvfKernel k) {
        assertTrue(Math.abs(k.lpOut - s.lp[n]) < TOL,
                s.name + " lp[" + n + "]: " + k.lpOut + " vs " + s.lp[n]);
        assertTrue(Math.abs(k.hpOut - s.hp[n]) < TOL,
                s.name + " hp[" + n + "]: " + k.hpOut + " vs " + s.hp[n]);
        assertTrue(Math.abs(k.bpOut - s.bp[n]) < TOL,
                s.name + " bp[" + n + "]: " + k.bpOut + " vs " + s.bp[n]);
    }

    // ── Minimal golden-JSON loader ────────────────────────────────────────

    private static final Map<String, Scenario> CACHE = new LinkedHashMap<>();

    private static Scenario load(String name) {
        if (CACHE.isEmpty()) {
            parseAll();
        }
        Scenario s = CACHE.get(name);
        assertNotNull(s, "golden scenario not found: " + name);
        return s;
    }

    private static void parseAll() {
        String json = readResource("/golden/svf_golden.json");
        // Scenario objects contain no nested braces, so this matches each one.
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(json);
        while (m.find()) {
            Scenario s = Scenario.parse(m.group());
            CACHE.put(s.name, s);
        }
    }

    private static String readResource(String path) {
        try (InputStream in = SvfGoldenParityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Scenario {
        String name;
        float f;
        float d;
        float[] x;
        float[] lp;
        float[] hp;
        float[] bp;

        static Scenario parse(String obj) {
            Scenario s = new Scenario();
            s.name = group(obj, "\"name\":\"([^\"]*)\"");
            s.f = floatField(obj, "f");
            s.d = floatField(obj, "d");
            s.x = floatArray(obj, "x");
            s.lp = floatArray(obj, "lp");
            s.hp = floatArray(obj, "hp");
            s.bp = floatArray(obj, "bp");
            return s;
        }

        private static String group(String obj, String regex) {
            Matcher m = Pattern.compile(regex).matcher(obj);
            return m.find() ? m.group(1) : null;
        }

        private static float floatField(String obj, String key) {
            Matcher m = Pattern.compile("\"" + key + "\":(-?[0-9.eE+-]+)").matcher(obj);
            return m.find() ? Float.parseFloat(m.group(1)) : Float.NaN;
        }

        private static float[] floatArray(String obj, String key) {
            Matcher m = Pattern.compile("\"" + key + "\":\\[([^\\]]*)\\]").matcher(obj);
            if (!m.find()) {
                return new float[0];
            }
            String body = m.group(1);
            String[] parts = body.split(",");
            float[] out = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Float.parseFloat(parts[i]);
            }
            return out;
        }
    }
}
