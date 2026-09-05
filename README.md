# VDD Glitch and Waveform Generation for the Keysight N6705C

This project provides waveform-generation tools for testing how embedded systems respond to power-supply disturbances on VDD.

It combines two related topics:

1. Java methods that generate SCPI command strings for a Keysight N6705C power analyzer and modular power supply.
2. Python scripts that visualize Gaussian, wavelet-like, UWB, and VDD transient waveforms.

The Java code does not open a VISA connection. It creates command text that your own test application can send through VISA, LAN, USB, GPIB, or another supported interface.

## Why VDD transient testing matters

Short disturbances on VDD can produce several types of embedded-system failure:

- Brownout or reset events caused by negative droop.
- Timing errors caused by supply-dependent clock or logic behavior.
- Memory corruption or peripheral faults caused by a temporary loss of supply margin.
- Overvoltage stress caused by positive overshoot.
- Repeated threshold crossings caused by damped ringing.
- Broadband sensitivity to fast impulse-like disturbances.

The waveform generators let you vary the disturbance amplitude, duration, timing, sample density, and return-to-nominal behavior in a repeatable way.

## Waveforms included

### Negative droop

Creates a temporary decrease from the nominal voltage. The minimum level is specified as a fraction of nominal VDD. Smooth cosine edges reduce an artificial discontinuity at the start and end of the event.

### Positive overshoot

Creates a temporary increase above nominal VDD. The maximum level is specified as a fraction of nominal VDD.

### Damped ringing

Creates a decaying sinusoidal disturbance:

```text
V(t) = V0 + A exp(-decayPerSecond * t)
             sin(2*pi*ringFrequencyHz*t)
```

The positive peak and negative trough are scaled independently. `decayPerSecond` is the exponential decay constant in inverse seconds. The amplitude time constant is:

```text
tau = 1 / decayPerSecond
```

### Gaussian monocycle

Creates a bipolar first-derivative Gaussian pulse. It is useful as a short, broadband impulse-like disturbance.

### Gaussian doublet

Creates a bipolar second-derivative Gaussian pulse. It has a central lobe and opposite-polarity side lobes, which makes it useful for testing fast transient sensitivity.

## Java API

The five public methods are in [N6705WaveformCommands.java](N6705WaveformCommands.java):

```java
negativeDroop(...)
positiveOvershoot(...)
dampedRinging(...)
gaussianMonocycle(...)
gaussianDoublet(...)
```

Each method returns one complete SCPI command block as a `String`. The method does not communicate with the instrument.

Common parameters include:

- `nominalVoltage`: the VDD level, such as 3.3 V or 1.65 V.
- `preSamples`: number of nominal-voltage points before the disturbance.
- `eventSamples`, `ringSamples`, or `pulseSamples`: number of points in the disturbance.
- `postSamples`: number of nominal-voltage points after the disturbance.
- `dwellSeconds`: time held at each voltage point.
- `channel`: N6705C output channel.

The output sequence has this structure:

```text
V0 samples + disturbance samples + V0 samples
```

The implementation uses the N6705C constant-dwell ARB mode. One dwell value applies to every voltage point. The effective sample rate is:

```text
sampleRate = 1 / dwellSeconds
```

For a ringing frequency of `f`, the number of samples per cycle is:

```text
samplesPerCycle = sampleRate / f
```

Use enough samples per cycle to represent the waveform accurately. The actual waveform at the DUT depends on the installed power module, cable inductance, decoupling, remote-sense connection, and load.

## Java example

Compile and run the demonstration:

```powershell
javac N6705WaveformCommands.java
java N6705WaveformCommands
```

The `main` method calls all five public waveform functions and prints the generated SCPI blocks. It does not connect to or control an instrument.

Example use from another Java class:

```java
String scpi = N6705WaveformCommands.dampedRinging(
        3.3,       // nominal voltage
        1000,      // pre-event samples
        500,       // ringing samples
        1000,      // post-event samples
        20e-6,     // dwell time, 20 microseconds
        1.10,      // positive peak, 110 percent of V0
        0.90,      // negative trough, 90 percent of V0
        2_000.0,   // ringing frequency, Hz
        700.0,     // decay constant, 1/second
        1          // output channel
);
```

The returned string contains commands equivalent to:

```text
ARB:FUNC:TYPE VOLT,(@1)
ARB:FUNC:SHAP CDW,(@1)
ARB:VOLT:CDW:DWEL <dwell>
ARB:VOLT:CDW <voltage samples>
VOLT:MODE ARB,(@1)
INIT:TRAN (@1)
*TRG
```

Your instrument-control layer is responsible for sending the returned commands.

## Python visualizations

The Python scripts create comparison figures for waveform selection and review.

Install the required packages if needed:

```powershell
python -m pip install numpy scipy matplotlib
```

Generate the figures:

```powershell
python visualize_uwb_pulses.py
```

The script generates:

- `uwb_pulse_shapes.png`
- `vdd_glitch_shapes.png`

The measured-like panel in the VDD figure is synthetic. Replace it with captured oscilloscope data for validation against a real board.

## Safety and validation

This project generates test commands. It does not replace instrument or DUT safety review.

Before enabling an output:

1. Confirm the installed power module voltage, current, bandwidth, and ARB limits.
2. Confirm the DUT absolute-maximum and minimum operating-voltage limits.
3. Start with a low-amplitude disturbance.
4. Measure VDD at the DUT pins with an oscilloscope.
5. Check the actual rise time, peak voltage, trough voltage, ringing frequency, and decay.
6. Confirm that remote sense and the power return path are connected correctly.

Do not send generated commands to hardware until the voltage values and timing have been reviewed.

## Instrument documentation

The SCPI structure follows the Keysight N6705 programming documentation:

- [N6705 Programmer's Reference Guide](https://www.keysight.com/us/en/assets/9018-03616/programming-guides/9018-03616.pdf)
- [N6705C support resources](https://www.keysight.com/us/en/support/N6705C/dc-power-analyzer-modular-600-w-4-slots.html)

## License

No license has been selected yet. Add a license before accepting external contributions or distributing this project for reuse.
