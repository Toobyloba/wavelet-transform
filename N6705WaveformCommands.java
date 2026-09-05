import java.util.Arrays;
import java.util.Locale;

/**
 * Builds SCPI command strings for five VDD disturbance waveforms.
 *
 * <p>These methods do not open a VISA connection and do not send commands.
 * They only return a complete command block. The implementation uses the
 * N6705 constant-dwell ARB because one dwell value applies to every point and
 * long pre-event regions, such as 1000 nominal-voltage samples, are supported.
 */
public final class N6705WaveformCommands {

    private N6705WaveformCommands() {
        // Utility class.
    }

    /** Generates a negative droop with smooth edges and a configurable minimum voltage. */
    public static String negativeDroop(
            double nominalVoltage,
            int preSamples,
            int eventSamples,
            int postSamples,
            double dwellSeconds,
            double minimumVoltageFraction,
            int edgeSamples,
            int channel) {
        requireCommon(nominalVoltage, preSamples, eventSamples, postSamples, dwellSeconds, channel);
        if (!(minimumVoltageFraction > 0.0 && minimumVoltageFraction <= 1.0)) {
            throw new IllegalArgumentException("minimumVoltageFraction must be in (0, 1]");
        }
        double[] event = new double[eventSamples];
        double droop = nominalVoltage * (minimumVoltageFraction - 1.0);
        double[] window = smoothWindow(eventSamples, edgeSamples);
        for (int i = 0; i < event.length; i++) {
            event[i] = nominalVoltage + droop * window[i];
        }
        return buildCommandBlock(nominalVoltage, preSamples, event, postSamples, dwellSeconds, channel);
    }

    /** Generates a positive overshoot with smooth edges and a configurable maximum voltage. */
    public static String positiveOvershoot(
            double nominalVoltage,
            int preSamples,
            int eventSamples,
            int postSamples,
            double dwellSeconds,
            double maximumVoltageFraction,
            int edgeSamples,
            int channel) {
        requireCommon(nominalVoltage, preSamples, eventSamples, postSamples, dwellSeconds, channel);
        if (!(maximumVoltageFraction >= 1.0)) {
            throw new IllegalArgumentException("maximumVoltageFraction must be >= 1");
        }
        double[] event = new double[eventSamples];
        double overshoot = nominalVoltage * (maximumVoltageFraction - 1.0);
        double[] window = smoothWindow(eventSamples, edgeSamples);
        for (int i = 0; i < event.length; i++) {
            event[i] = nominalVoltage + overshoot * window[i];
        }
        return buildCommandBlock(nominalVoltage, preSamples, event, postSamples, dwellSeconds, channel);
    }

    /**
     * Generates damped sinusoidal ringing. Positive and negative limits are
     * specified as absolute voltage fractions of nominal VDD.
     */
    public static String dampedRinging(
            double nominalVoltage,
            int preSamples,
            int ringSamples,
            int postSamples,
            double dwellSeconds,
            double maximumVoltageFraction,
            double minimumVoltageFraction,
            double ringFrequencyHz,
            double decayPerSecond,
            int channel) {
        requireCommon(nominalVoltage, preSamples, ringSamples, postSamples, dwellSeconds, channel);
        if (!(maximumVoltageFraction >= 1.0)) {
            throw new IllegalArgumentException("maximumVoltageFraction must be >= 1");
        }
        if (!(minimumVoltageFraction > 0.0 && minimumVoltageFraction <= 1.0)) {
            throw new IllegalArgumentException("minimumVoltageFraction must be in (0, 1]");
        }
        if (!(ringFrequencyHz > 0.0 && decayPerSecond >= 0.0)) {
            throw new IllegalArgumentException("ringFrequencyHz must be > 0 and decayPerSecond must be >= 0");
        }

        double[] raw = new double[ringSamples];
        double rawMaximum = 0.0;
        double rawMinimum = 0.0;
        for (int i = 0; i < raw.length; i++) {
            double time = i * dwellSeconds;
            raw[i] = Math.exp(-decayPerSecond * time)
                    * Math.sin(2.0 * Math.PI * ringFrequencyHz * time);
            rawMaximum = Math.max(rawMaximum, raw[i]);
            rawMinimum = Math.min(rawMinimum, raw[i]);
        }
        if (rawMaximum == 0.0 || rawMinimum == 0.0) {
            throw new IllegalArgumentException("ringSamples must contain both positive and negative ringing");
        }

        double positiveDelta = nominalVoltage * (maximumVoltageFraction - 1.0);
        double negativeDelta = nominalVoltage * (minimumVoltageFraction - 1.0);
        double[] event = new double[ringSamples];
        for (int i = 0; i < raw.length; i++) {
            double perturbation = raw[i] >= 0.0
                    ? raw[i] / rawMaximum * positiveDelta
                    : raw[i] / Math.abs(rawMinimum) * Math.abs(negativeDelta);
            event[i] = nominalVoltage + perturbation;
        }
        return buildCommandBlock(nominalVoltage, preSamples, event, postSamples, dwellSeconds, channel);
    }

    /** Generates a Gaussian monocycle, scaled to the requested voltage limits. */
    public static String gaussianMonocycle(
            double nominalVoltage,
            int preSamples,
            int pulseSamples,
            int postSamples,
            double dwellSeconds,
            double maximumVoltageFraction,
            double minimumVoltageFraction,
            double sigmaSamples,
            int channel) {
        return gaussianPulse(
                nominalVoltage, preSamples, pulseSamples, postSamples, dwellSeconds,
                maximumVoltageFraction, minimumVoltageFraction, sigmaSamples,
                false, channel);
    }

    /** Generates a Gaussian doublet, scaled to the requested voltage limits. */
    public static String gaussianDoublet(
            double nominalVoltage,
            int preSamples,
            int pulseSamples,
            int postSamples,
            double dwellSeconds,
            double maximumVoltageFraction,
            double minimumVoltageFraction,
            double sigmaSamples,
            int channel) {
        return gaussianPulse(
                nominalVoltage, preSamples, pulseSamples, postSamples, dwellSeconds,
                maximumVoltageFraction, minimumVoltageFraction, sigmaSamples,
                true, channel);
    }

    private static String gaussianPulse(
            double nominalVoltage,
            int preSamples,
            int pulseSamples,
            int postSamples,
            double dwellSeconds,
            double maximumVoltageFraction,
            double minimumVoltageFraction,
            double sigmaSamples,
            boolean doublet,
            int channel) {
        requireCommon(nominalVoltage, preSamples, pulseSamples, postSamples, dwellSeconds, channel);
        if (!(maximumVoltageFraction >= 1.0)) {
            throw new IllegalArgumentException("maximumVoltageFraction must be >= 1");
        }
        if (!(minimumVoltageFraction > 0.0 && minimumVoltageFraction <= 1.0)) {
            throw new IllegalArgumentException("minimumVoltageFraction must be in (0, 1]");
        }
        if (!(sigmaSamples > 0.0)) {
            throw new IllegalArgumentException("sigmaSamples must be > 0");
        }

        double[] raw = new double[pulseSamples];
        double rawMaximum = 0.0;
        double rawMinimum = 0.0;
        double center = 0.5 * (pulseSamples - 1);
        for (int i = 0; i < raw.length; i++) {
            double u = (i - center) / sigmaSamples;
            double gaussian = Math.exp(-0.5 * u * u);
            raw[i] = doublet ? (u * u - 1.0) * gaussian : -u * gaussian;
            rawMaximum = Math.max(rawMaximum, raw[i]);
            rawMinimum = Math.min(rawMinimum, raw[i]);
        }
        raw[0] = 0.0;
        raw[raw.length - 1] = 0.0;
        if (rawMaximum == 0.0 || rawMinimum == 0.0) {
            throw new IllegalArgumentException("pulseSamples must contain positive and negative lobes");
        }

        double positiveDelta = nominalVoltage * (maximumVoltageFraction - 1.0);
        double negativeDelta = nominalVoltage * (minimumVoltageFraction - 1.0);
        double[] event = new double[pulseSamples];
        for (int i = 0; i < raw.length; i++) {
            double perturbation = raw[i] >= 0.0
                    ? raw[i] / rawMaximum * positiveDelta
                    : raw[i] / Math.abs(rawMinimum) * Math.abs(negativeDelta);
            event[i] = nominalVoltage + perturbation;
        }
        return buildCommandBlock(nominalVoltage, preSamples, event, postSamples, dwellSeconds, channel);
    }

    private static String buildCommandBlock(
            double nominalVoltage,
            int preSamples,
            double[] event,
            int postSamples,
            double dwellSeconds,
            int channel) {
        double[] values = new double[preSamples + event.length + postSamples];
        Arrays.fill(values, 0, preSamples, nominalVoltage);
        System.arraycopy(event, 0, values, preSamples, event.length);
        Arrays.fill(values, preSamples + event.length, values.length, nominalVoltage);

        String channelList = "(@" + channel + ")";
        String voltagePoints = join(values);
        return String.join("\n",
                "*CLS",
                "VOLT " + number(nominalVoltage) + "," + channelList,
                "ARB:FUNC:TYPE VOLT," + channelList,
                "ARB:FUNC:SHAP CDW," + channelList,
                "ARB:VOLT:CDW:DWEL " + number(dwellSeconds) + "," + channelList,
                "ARB:VOLT:CDW " + voltagePoints + "," + channelList,
                "ARB:TERM:LAST OFF," + channelList,
                "ARB:COUN 1," + channelList,
                "VOLT:MODE ARB," + channelList,
                "TRIG:ARB:SOUR BUS",
                "OUTP ON," + channelList,
                "INIT:TRAN " + channelList,
                "*TRG");
    }

    private static double[] smoothWindow(int samples, int edgeSamples) {
        if (edgeSamples < 2 || edgeSamples * 2 > samples) {
            throw new IllegalArgumentException("edgeSamples must be >= 2 and no more than half of eventSamples");
        }
        double[] window = new double[samples];
        for (int i = 0; i < samples; i++) {
            if (i < edgeSamples) {
                window[i] = 0.5 * (1.0 - Math.cos(Math.PI * i / (edgeSamples - 1.0)));
            } else if (i >= samples - edgeSamples) {
                double j = samples - 1.0 - i;
                window[i] = 0.5 * (1.0 - Math.cos(Math.PI * j / (edgeSamples - 1.0)));
            } else {
                window[i] = 1.0;
            }
        }
        return window;
    }

    private static void requireCommon(
            double nominalVoltage,
            int preSamples,
            int eventSamples,
            int postSamples,
            double dwellSeconds,
            int channel) {
        if (!(nominalVoltage > 0.0)) {
            throw new IllegalArgumentException("nominalVoltage must be > 0");
        }
        if (preSamples < 0 || eventSamples < 2 || postSamples < 0) {
            throw new IllegalArgumentException("sample counts are invalid");
        }
        if (!(dwellSeconds > 0.0)) {
            throw new IllegalArgumentException("dwellSeconds must be > 0");
        }
        if (channel < 1) {
            throw new IllegalArgumentException("channel must be >= 1");
        }
    }

    private static String join(double[] values) {
        StringBuilder result = new StringBuilder(values.length * 12);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(number(values[i]));
        }
        return result.toString();
    }

    private static String number(double value) {
        if (Math.abs(value) < 1e-15) {
            value = 0.0;
        }
        return String.format(Locale.US, "%.12g", value);
    }

    /**
     * Example use. This only prints the generated SCPI command block. It does
     * not connect to the N6705C or send anything to the instrument.
     */
    public static void main(String[] args) {
        double nominalVoltage = 3.3;
        int preSamples = 20;
        int postSamples = 20;
        double dwellSeconds = 20e-6;
        int channel = 1;

        System.out.println("===== NEGATIVE DROOP =====");
        System.out.println(negativeDroop(
                nominalVoltage,        // Nominal VDD voltage, V0
                preSamples,            // Samples at V0 before the droop
                40,                    // Samples used for the droop event
                postSamples,           // Samples at V0 after the droop
                dwellSeconds,          // Time per voltage point, seconds
                0.90,                  // Minimum voltage as a fraction of V0
                6,                     // Smooth edge samples
                channel));             // N6705C output channel

        System.out.println("===== POSITIVE OVERSHOOT =====");
        System.out.println(positiveOvershoot(
                nominalVoltage,        // Nominal VDD voltage, V0
                preSamples,            // Samples at V0 before the overshoot
                40,                    // Samples used for the overshoot event
                postSamples,           // Samples at V0 after the overshoot
                dwellSeconds,          // Time per voltage point, seconds
                1.10,                  // Maximum voltage as a fraction of V0
                6,                     // Smooth edge samples
                channel));             // N6705C output channel

        System.out.println("===== DAMPED RINGING =====");
        System.out.println(dampedRinging(
                nominalVoltage,        // Nominal VDD voltage, V0
                preSamples,            // Samples at V0 before ringing
                100,                   // Samples used for ringing
                postSamples,           // Samples at V0 after ringing
                dwellSeconds,          // Time per voltage point, seconds
                1.10,                  // Positive peak as a fraction of V0
                0.90,                  // Negative trough as a fraction of V0
                2_000.0,               // Ringing frequency, Hz
                700.0,                 // Exponential decay constant, 1/second
                channel));             // N6705C output channel

        System.out.println("===== GAUSSIAN MONOCYCLE =====");
        System.out.println(gaussianMonocycle(
                nominalVoltage,        // Nominal VDD voltage, V0
                preSamples,            // Samples at V0 before the pulse
                61,                    // Samples used for the pulse
                postSamples,           // Samples at V0 after the pulse
                dwellSeconds,          // Time per voltage point, seconds
                1.10,                  // Positive peak as a fraction of V0
                0.90,                  // Negative trough as a fraction of V0
                8.0,                   // Gaussian width, in samples
                channel));             // N6705C output channel

        System.out.println("===== GAUSSIAN DOUBLET =====");
        System.out.println(gaussianDoublet(
                nominalVoltage,        // Nominal VDD voltage, V0
                preSamples,            // Samples at V0 before the pulse
                61,                    // Samples used for the pulse
                postSamples,           // Samples at V0 after the pulse
                dwellSeconds,          // Time per voltage point, seconds
                1.10,                  // Positive peak as a fraction of V0
                0.90,                  // Negative trough as a fraction of V0
                8.0,                   // Gaussian width, in samples
                channel));             // N6705C output channel
    }
}
