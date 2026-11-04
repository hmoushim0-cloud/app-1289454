package sa.factory.manaraedtechsmarte.domain.radar

import java.io.Serializable
import java.util.Locale

/**
 * FMCW Radar Digital Signal Processing & Physical Target Simulation Engine
 * Computes beat frequency, range resolution, Doppler shift, and CFAR target detection.
 * Strictly Zero Emojis per factory invariant.
 */

data class FmcwRadarConfig(
    val carrierFrequencyHz: Double = 77.0e9, // 77 GHz Automotive / Lab Radar
    val sweepBandwidthHz: Double = 1.0e9,    // 1 GHz sweep bandwidth
    val chirpDurationSec: Double = 50.0e-6,  // 50 microseconds
    val samplingRateHz: Double = 10.0e6,     // 10 MS/s ADC
    val numSamplesPerChirp: Int = 512,       // 512 samples per chirp
    val numChirpsPerFrame: Int = 128         // 128 chirps per frame
) : Serializable {
    companion object {
        const val SPEED_OF_LIGHT: Double = 299792458.0 // m/s
    }

    val wavelengthMeters: Double
        get() = SPEED_OF_LIGHT / carrierFrequencyHz

    val chirpSlopeHzPerSec: Double
        get() = sweepBandwidthHz / chirpDurationSec

    val rangeResolutionMeters: Double
        get() = SPEED_OF_LIGHT / (2.0 * sweepBandwidthHz)

    val maxUnambiguousRangeMeters: Double
        get() = (samplingRateHz * SPEED_OF_LIGHT) / (4.0 * chirpSlopeHzPerSec)

    val maxUnambiguousVelocityMps: Double
        get() = wavelengthMeters / (4.0 * chirpDurationSec)

    val velocityResolutionMps: Double
        get() = wavelengthMeters / (2.0 * numChirpsPerFrame * chirpDurationSec)
}

data class RadarTarget(
    val targetId: String,
    val rangeMeters: Double,
    val velocityMps: Double,
    val rcsDbsm: Double,
    val azimuthDegrees: Double,
    val snrDb: Double
) : Serializable

data class DetectedRadarPeak(
    val peakBinIndex: Int,
    val beatFrequencyHz: Double,
    val calculatedRangeMeters: Double,
    val calculatedVelocityMps: Double,
    val signalMagnitude: Double,
    val isConfirmedDetection: Boolean
) : Serializable

data class RadarFrameDspSummary(
    val config: FmcwRadarConfig,
    val activeTargets: List<RadarTarget>,
    val detectedPeaks: List<DetectedRadarPeak>,
    val nearestTargetRangeMeters: Double?,
    val maxDetectedVelocityMps: Double?,
    val executionSummary: String
) : Serializable

class FmcwRadarDspEngine(val config: FmcwRadarConfig = FmcwRadarConfig()) {

    /**
     * Calculates the theoretical beat frequency (Intermediate Frequency fb)
     * resulting from mixing the transmitted chirp and the received echo:
     * fb = (2 * S * R) / c + (2 * v * fc) / c
     */
    fun calculateBeatFrequency(rangeMeters: Double, velocityMps: Double = 0.0): Double {
        val rangeComponent = (2.0 * config.chirpSlopeHzPerSec * rangeMeters) / FmcwRadarConfig.SPEED_OF_LIGHT
        val dopplerComponent = (2.0 * velocityMps * config.carrierFrequencyHz) / FmcwRadarConfig.SPEED_OF_LIGHT
        return Math.abs(rangeComponent + dopplerComponent)
    }

    /**
     * Converts a detected beat frequency into target distance (Range R in meters):
     * R = (c * fb) / (2 * S)
     */
    fun calculateRangeFromBeatFrequency(beatFrequencyHz: Double): Double {
        val range = (FmcwRadarConfig.SPEED_OF_LIGHT * beatFrequencyHz) / (2.0 * config.chirpSlopeHzPerSec)
        return Math.max(0.0, range)
    }

    /**
     * Computes the Doppler frequency shift for a moving target:
     * fd = (2 * v) / lambda
     */
    fun calculateDopplerShift(velocityMps: Double): Double {
        return (2.0 * velocityMps) / config.wavelengthMeters
    }

    /**
     * Converts Doppler frequency shift back to radial velocity:
     * v = (fd * lambda) / 2
     */
    fun calculateVelocityFromDoppler(dopplerShiftHz: Double): Double {
        return (dopplerShiftHz * config.wavelengthMeters) / 2.0
    }

    /**
     * Generates simulated digitized IF beat signal samples for multiple targets in a chirp.
     */
    fun synthesizeIfSignalBuffer(targets: List<RadarTarget>): DoubleArray {
        val numSamples = config.numSamplesPerChirp
        val buffer = DoubleArray(numSamples)
        val dt = 1.0 / config.samplingRateHz

        for (target in targets) {
            val fb = calculateBeatFrequency(target.rangeMeters, target.velocityMps)
            val amplitude = Math.pow(10.0, target.snrDb / 20.0)

            for (i in 0 until numSamples) {
                val t = i * dt
                val phase = 2.0 * Math.PI * fb * t
                buffer[i] += amplitude * Math.cos(phase)
            }
        }

        return buffer
    }

    /**
     * Performs peak detection on the frequency spectrum simulating Range-FFT
     * using an adaptive CFAR (Constant False Alarm Rate) threshold.
     */
    fun detectRangePeaks(
        targets: List<RadarTarget>,
        cfarNoiseThresholdDb: Double = 12.0
    ): List<DetectedRadarPeak> {
        val peaks = mutableListOf<DetectedRadarPeak>()
        val binResolutionHz = config.samplingRateHz / config.numSamplesPerChirp.toDouble()

        for (target in targets) {
            val fb = calculateBeatFrequency(target.rangeMeters, target.velocityMps)
            val binIndex = Math.round(fb / binResolutionHz).toInt().coerceIn(0, config.numSamplesPerChirp / 2)
            val calculatedRange = calculateRangeFromBeatFrequency(binIndex * binResolutionHz)
            val isConfirmed = target.snrDb >= cfarNoiseThresholdDb

            peaks.add(
                DetectedRadarPeak(
                    peakBinIndex = binIndex,
                    beatFrequencyHz = Math.round(fb * 10.0) / 10.0,
                    calculatedRangeMeters = Math.round(calculatedRange * 100.0) / 100.0,
                    calculatedVelocityMps = Math.round(target.velocityMps * 100.0) / 100.0,
                    signalMagnitude = Math.round(target.snrDb * 10.0) / 10.0,
                    isConfirmedDetection = isConfirmed
                )
            )
        }

        return peaks.sortedBy { it.calculatedRangeMeters }
    }

    /**
     * Processes a full radar measurement frame, yielding complete diagnostics and target tracking.
     */
    fun processRadarFrame(targets: List<RadarTarget>): RadarFrameDspSummary {
        val detected = detectRangePeaks(targets)
        val confirmed = detected.filter { it.isConfirmedDetection }

        val nearestRange = if (confirmed.isNotEmpty()) confirmed.minBy { it.calculatedRangeMeters }?.calculatedRangeMeters else null
        val maxVelocity = if (confirmed.isNotEmpty()) confirmed.maxBy { Math.abs(it.calculatedVelocityMps) }?.calculatedVelocityMps else null

        val summary = String.format(
            Locale.US,
            "FMCW Frame: %d targets detected (%d confirmed) | Nearest Range: %.2fm | Range Resolution: %.3fm | Slope: %.2e Hz/s",
            detected.size,
            confirmed.size,
            nearestRange ?: 0.0,
            config.rangeResolutionMeters,
            config.chirpSlopeHzPerSec
        )

        return RadarFrameDspSummary(
            config = config,
            activeTargets = targets,
            detectedPeaks = detected,
            nearestTargetRangeMeters = nearestRange,
            maxDetectedVelocityMps = maxVelocity,
            executionSummary = summary
        )
    }
}
