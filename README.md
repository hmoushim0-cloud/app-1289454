# تطبيق منارة التعليمية — شرح ومحاكاة لرادار FMCW (`manara-edtech-smart-education-1289454`)
> منظومة smart_education المتكاملة مع ربط Smart Stylus Digitizer 4096-Level + BLE Beacon

- **Project ID**: `#1289454`
- **Commercial Archetype**: `smart_education`
- **Bound Hardware Peripheral**: `Smart Stylus Digitizer 4096-Level + BLE Beacon`
- **Package Name**: `sa.factory.manaraedtechsmarte`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

---

## [Sidekick Autonomous Update]

### Overview of Autonomous Enhancements
Sidekick has upgraded **Manara EdTech** (`manara-edtech-smart-education-1289454`) with an interactive FMCW (Frequency-Modulated Continuous-Wave) Radar RF Digital Signal Processing Engine, Range-Doppler Target Extraction with CFAR Filtering, and a 4096-Level Pressure Stylus & BLE Classroom Telemetry Beacon Driver.

### Newly Built Modules & Architecture
1. **FMCW Radar RF Signal Processing Engine** (`FmcwRadarDspEngine.kt`):
   - **Chirp Dynamics & Physical Resolution**: Computes carrier wavelength ($f_c = 77$ GHz, $\lambda = 3.89$ mm), chirp slope ($S = 2 \times 10^{13}$ Hz/s for $B = 1$ GHz over $50 \mu$s), and theoretical range resolution ($\Delta R = c / 2B \approx 15$ cm).
   - **Beat Frequency & Target Range Mapping**: Computes intermediate beat frequencies ($f_b = 2SR/c + 2vf_c/c$) and performs reciprocal range calculations.
   - **Doppler Shift & Radial Velocity Conversion**: Resolves target approach/recession velocities using Doppler frequency shifts ($f_d = 2v/\lambda$).
   - **Range Peak Detection with CFAR Thresholding**: Constant False Alarm Rate (CFAR) peak extraction differentiating real targets from noise floors.
   - **Multi-Target Frame Synthesis**: Synthesizes time-domain IF beat signal buffers for multi-target educational simulations.

2. **Smart Stylus Digitizer & Classroom BLE Beacon Driver** (`SmartStylusBeaconDriver.kt`):
   - **4096-Level Pressure Stylus Dynamics**: Calculates dynamic line stroke width across 4 specialized RF lab tools (`CHIRP_SLOPE_PEN`, `RANGE_GATE_HIGHLIGHTER`, `ANTENNA_BEAM_VECTOR`, `TARGET_ANNOTATOR`).
   - **19-Byte BLE Telemetry Frame Protocol**: Encodes station ID, radar transmit/receive state, detected target count, nearest range in cm, radial velocity in cm/s, SNR in dB, stylus pressure, and 16-bit CRC (CCITT polynomial `0x1021`).
   - **Autonomous CRC-16 Validation**: Full CRC integrity verification rejecting corrupt or truncated packets.

3. **Hardware Sensor Driver Integration** (`HardwareSensorDriver.kt`):
   - Emits real-time StateFlow telemetry broadcasting radar chirp sweeps, beat frequencies, target ranges, stylus pressure, and broadcast BLE beacon frames.

4. **Automated Unit Test Suite** (`ManaraEdtechRadarFeatureTest.kt`):
   - 7 comprehensive JUnit 4 unit tests covering chirp physical constants, beat frequency range math, Doppler velocity conversion, CFAR noise filtering, stylus stroke scaling, and BLE beacon serialization/CRC verification (100% passing).

### How to Run & Verify
```bash
# 1. Compile and run unit test suite
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
CP="/usr/share/java/junit4-4.13.2.jar:/usr/share/java/hamcrest-2.2.jar:/usr/share/java/kotlin-stdlib.jar"

kotlinc -Xskip-metadata-version-check -cp "$CP" -d /tmp/radar_test_bin \
  app/src/main/java/sa/factory/manaraedtechsmarte/domain/radar/FmcwRadarDspEngine.kt \
  app/src/main/java/sa/factory/manaraedtechsmarte/hardware/stylus/SmartStylusBeaconDriver.kt \
  app/src/test/java/sa/factory/manaraedtechsmarte/ManaraEdtechRadarFeatureTest.kt

java -cp "/tmp/radar_test_bin:$CP" org.junit.runner.JUnitCore sa.factory.manaraedtechsmarte.ManaraEdtechRadarFeatureTest
```
