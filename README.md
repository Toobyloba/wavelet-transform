# Wavelet Transform and N6705C VDD Waveforms

This repository contains:

- Java SCPI waveform generators for the Keysight N6705C.
- Python scripts for visualizing UWB-like pulse shapes and VDD disturbances.

## Java

Compile and run the example:

```powershell
javac N6705WaveformCommands.java
java N6705WaveformCommands
```

The Java program prints command strings only. It does not connect to or control an instrument.

## Python

Generate the pulse figures with:

```powershell
python visualize_uwb_pulses.py
```

