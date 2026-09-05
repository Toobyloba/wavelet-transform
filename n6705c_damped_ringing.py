"""Program a damped-ringing VDD transient on a Keysight N6705C.

Default behavior is a dry run that prints the SCPI commands. Use --run only
after checking the voltage, current, timing, and DUT safety limits.
"""

from __future__ import annotations

import argparse
import math
from typing import Iterable


def damped_ringing_waveform(
    v_nom: float,
    amplitude: float,
    ring_frequency_hz: float,
    decay_per_second: float,
    sample_period_s: float,
    total_time_s: float,
    ring_start_s: float,
) -> list[float]:
    """Return voltage samples, beginning and ending at v_nom."""
    count = max(2, round(total_time_s / sample_period_s) + 1)
    values: list[float] = []
    for i in range(count):
        t = i * sample_period_s
        tau = t - ring_start_s
        if tau < 0:
            values.append(v_nom)
        else:
            values.append(
                v_nom
                + amplitude
                * math.exp(-decay_per_second * tau)
                * math.sin(2 * math.pi * ring_frequency_hz * tau)
            )
    values[0] = v_nom
    values[-1] = v_nom
    return values


def csv(values: Iterable[float]) -> str:
    return ",".join(f"{value:.9g}" for value in values)


def build_commands(channel: int, values: list[float], dwell_s: float, v_nom: float) -> list[str]:
    ch = f"(@{channel})"
    levels = csv(values)
    dwells = csv([dwell_s] * len(values))
    bost = csv([0] * len(values))
    return [
        "*CLS",
        f"VOLT {v_nom:.9g},{ch}",
        f"ARB:FUNC:TYPE VOLT,{ch}",
        f"ARB:FUNC:SHAP UDEF,{ch}",
        f"ARB:VOLT:UDEF:LEV {levels},{ch}",
        f"ARB:VOLT:UDEF:DWEL {dwells},{ch}",
        f"ARB:VOLT:UDEF:BOST {bost},{ch}",
        f"ARB:TERM:LAST OFF,{ch}",
        f"ARB:COUN 1,{ch}",
        f"VOLT:MODE ARB,{ch}",
        "TRIG:ARB:SOUR BUS",
        f"OUTP ON,{ch}",
        f"INIT:TRAN {ch}",
        "*TRG",
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resource", default="TCPIP0::192.168.0.10::inst0::INSTR")
    parser.add_argument("--channel", type=int, default=1)
    parser.add_argument("--v-nom", type=float, default=3.3)
    parser.add_argument("--amplitude", type=float, default=0.25, help="Peak ringing amplitude in volts")
    parser.add_argument("--frequency", type=float, default=2_000.0, help="Ringing frequency in Hz")
    parser.add_argument("--decay", type=float, default=700.0, help="Exponential decay constant, 1/s")
    parser.add_argument("--dwell-us", type=float, default=20.0, help="Dwell per point in microseconds")
    parser.add_argument("--total-ms", type=float, default=10.0)
    parser.add_argument("--start-ms", type=float, default=2.0)
    parser.add_argument("--run", action="store_true", help="Actually connect, enable, and trigger the N6705C")
    args = parser.parse_args()

    dwell_s = args.dwell_us * 1e-6
    values = damped_ringing_waveform(
        args.v_nom,
        args.amplitude,
        args.frequency,
        args.decay,
        dwell_s,
        args.total_ms * 1e-3,
        args.start_ms * 1e-3,
    )
    commands = build_commands(args.channel, values, dwell_s, args.v_nom)
    print(f"Prepared {len(values)} points, total duration {len(values) * args.dwell_us:.3f} us")

    if not args.run:
        print("Dry run. First commands:")
        for command in commands[:4]:
            print(command)
        print(f"ARB:VOLT:UDEF:LEV <{len(values)} voltage points>,(@{args.channel})")
        print(f"ARB:VOLT:UDEF:DWEL <{len(values)} dwell points>,(@{args.channel})")
        print(f"ARB:VOLT:UDEF:BOST <{len(values)} trigger flags>,(@{args.channel})")
        for command in commands[7:]:
            print(command)
        print("... use --run only after validating the generated levels and the DUT limits")
        return

    import pyvisa  # Imported only when hardware execution is requested.

    rm = pyvisa.ResourceManager()
    instrument = rm.open_resource(args.resource)
    instrument.timeout = 30_000
    try:
        print(instrument.query("*IDN?"))
        for command in commands:
            instrument.write(command)
        print("ARB triggered")
    finally:
        instrument.close()
        rm.close()


if __name__ == "__main__":
    main()
