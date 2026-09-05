"""Generate common UWB pulse shapes and save a comparison figure.

The plotted amplitudes are peak-normalized. The prolate spheroidal pulse is
the first discrete prolate spheroidal sequence (DPSS), which concentrates
energy in a chosen time-frequency region.
"""

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from scipy.signal.windows import dpss


def peak_normalize(x: np.ndarray) -> np.ndarray:
    return x / np.max(np.abs(x))


def gaussian(t: np.ndarray, sigma: float = 0.22) -> np.ndarray:
    return np.exp(-0.5 * (t / sigma) ** 2)


def gaussian_monocycle(t: np.ndarray, sigma: float = 0.22) -> np.ndarray:
    g = gaussian(t, sigma)
    return -(t / sigma**2) * g


def gaussian_doublet(t: np.ndarray, sigma: float = 0.22) -> np.ndarray:
    g = gaussian(t, sigma)
    return ((t**2 / sigma**4) - 1 / sigma**2) * g


def hermite_pulse(t: np.ndarray, sigma: float = 0.22) -> np.ndarray:
    # Third-order Hermite-Gaussian pulse, H_3(u) exp(-u^2/2).
    u = t / sigma
    return (8 * u**3 - 12 * u) * np.exp(-0.5 * u**2)


def prolate_spheroidal(t: np.ndarray, time_bandwidth: float = 3.5) -> np.ndarray:
    # DPSS is sampled on an equally spaced grid. Interpolation puts it on t.
    seq = dpss(t.size, NW=time_bandwidth, Kmax=1, sym=True)[0]
    return seq if seq[t.size // 2] > 0 else -seq


def main() -> None:
    out = Path(__file__).with_name("uwb_pulse_shapes.png")
    t = np.linspace(-1.0, 1.0, 2001)
    pulses = {
        "Gaussian pulse": gaussian(t),
        "Gaussian monocycle": gaussian_monocycle(t),
        "Gaussian doublet": gaussian_doublet(t),
        "Hermite-Gaussian (order 3)": hermite_pulse(t),
        "Prolate spheroidal (DPSS)": prolate_spheroidal(t),
    }

    fig, axes = plt.subplots(5, 1, figsize=(10, 11), sharex=True, constrained_layout=True)
    fig.suptitle("Common UWB-like pulse shapes", fontsize=16)
    for ax, (name, pulse) in zip(axes, pulses.items()):
        ax.plot(t, peak_normalize(pulse), linewidth=1.8)
        ax.axhline(0, color="0.35", linewidth=0.7)
        ax.set_ylabel("Amplitude")
        ax.set_title(name, loc="left", fontsize=11)
        ax.grid(True, alpha=0.25)
        ax.set_ylim(-1.08, 1.08)
    axes[-1].set_xlabel("Normalized time")
    fig.savefig(out, dpi=180)
    print(f"Saved {out}")

    # Representative VDD faults. These are normalized around nominal supply.
    vdd_out = Path(__file__).with_name("vdd_glitch_shapes.png")
    tv = np.linspace(-2.0, 8.0, 5000)
    v0 = 1.0
    t0 = 2.0
    width = 0.8
    edge = 0.035
    smooth_step = lambda z: 0.5 * (1.0 + np.tanh(z / edge))
    window = smooth_step(tv - t0) - smooth_step(tv - (t0 + width))
    negative_droop = v0 - 0.35 * window
    positive_overshoot = v0 + 0.25 * window
    ring = 0.18 * np.exp(-0.7 * np.maximum(tv - t0, 0)) * np.sin(2 * np.pi * 1.8 * np.maximum(tv - t0, 0))
    damped_ringing = v0 + ring * (tv >= t0)
    monocycle = v0 + 0.25 * peak_normalize(gaussian_monocycle(tv - t0, 0.16))
    doublet = v0 + 0.25 * peak_normalize(gaussian_doublet(tv - t0, 0.20))
    rng = np.random.default_rng(7)
    measured_like = v0 + 0.20 * window + ring * (tv >= t0) + 0.008 * rng.standard_normal(tv.size)

    vdd_signals = [
        ("Negative droop", negative_droop),
        ("Positive overshoot", positive_overshoot),
        ("Damped ringing", damped_ringing),
        ("Gaussian monocycle", monocycle),
        ("Gaussian doublet", doublet),
        ("Synthetic measured-like waveform", measured_like),
    ]
    fig2, axes2 = plt.subplots(6, 1, figsize=(10, 13), sharex=True, constrained_layout=True)
    fig2.suptitle("Representative VDD spike and glitch waveforms", fontsize=16)
    for ax, (name, signal) in zip(axes2, vdd_signals):
        ax.plot(tv, signal, linewidth=1.5)
        ax.axhline(v0, color="0.35", linewidth=0.7, linestyle="--", label="Nominal VDD")
        ax.set_ylabel("VDD / Vnom")
        ax.set_title(name, loc="left", fontsize=11)
        ax.grid(True, alpha=0.25)
        ax.set_ylim(0.55, 1.35)
    axes2[-1].set_xlabel("Time (normalized units)")
    fig2.savefig(vdd_out, dpi=180)
    print(f"Saved {vdd_out}")


if __name__ == "__main__":
    main()
