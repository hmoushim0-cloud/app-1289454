package sa.factory.manaraedtechsmarte.hardware

import sa.factory.manaraedtechsmarte.domain.radar.*
import sa.factory.manaraedtechsmarte.hardware.stylus.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "Smart Stylus Digitizer 4096-Level + BLE Beacon (FMCW Radar Lab)"
    private val radarEngine = FmcwRadarDspEngine()
    private val stylusDriver = SmartStylusBeaconDriver()

    fun getRadarEngine(): FmcwRadarDspEngine = radarEngine
    fun getStylusDriver(): SmartStylusBeaconDriver = stylusDriver

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(
            peripheral,
            "CONNECTING",
            "Radar Lab: Initializing FMCW 77 GHz RF Front-End & BLE Classroom Beacon..."
        ))
        delay(300)

        emit(HardwareTelemetryFrame(
            peripheral,
            "ONLINE",
            String.format(
                Locale.US,
                "FMCW Online: fc=%.1f GHz | B=%.1f GHz | Res=%.3fm | Stylus 4096-Level Active",
                radarEngine.config.carrierFrequencyHz / 1.0e9,
                radarEngine.config.sweepBandwidthHz / 1.0e9,
                radarEngine.config.rangeResolutionMeters
            )
        ))

        var frameCounter = 1
        val sampleTargets = listOf(
            RadarTarget("TGT-01", 12.50, 15.2, 10.0, 0.0, 24.5),
            RadarTarget("TGT-02", 45.80, -22.4, 15.0, 15.0, 18.2),
            RadarTarget("TGT-LAB", 3.20, 0.0, 5.0, -10.0, 32.0)
        )

        while (true) {
            delay(1500)
            val jitterPressure = Random.nextInt(1000, 3900)
            val strokeWidth = stylusDriver.calculateStrokeWidth(StylusDrawingTool.CHIRP_SLOPE_PEN, jitterPressure)
            val dspSummary = radarEngine.processRadarFrame(sampleTargets)

            val packet = ClassroomBeaconTelemetryPacket(
                stationId = 0x1234.toShort(),
                radarState = RadarBeaconState.CHIRPING_TRANSMIT,
                targetCount = sampleTargets.size.toByte(),
                nearestRangeCm = (Math.round((dspSummary.nearestTargetRangeMeters ?: 0.0) * 100.0)).toShort(),
                nearestVelocityCmPerSec = (Math.round((dspSummary.maxDetectedVelocityMps ?: 0.0) * 100.0)).toShort(),
                peakSnrDb = 32.toByte(),
                stylusPressureRaw = jitterPressure.toShort(),
                sequenceCounter = frameCounter
            )

            val rawBeacon = stylusDriver.serializeBeaconPacket(packet)
            val beaconHex = rawBeacon.joinToString("") { String.format("%02X", it) }

            val payload = String.format(
                Locale.US,
                "Radar Sweep #%d: Nearest=%.2fm (fb=%.1f kHz) | Targets=%d | Pen: %d/4095 (%.2fpx) | BLE Beacon: [%s]",
                frameCounter,
                dspSummary.nearestTargetRangeMeters ?: 0.0,
                radarEngine.calculateBeatFrequency(dspSummary.nearestTargetRangeMeters ?: 0.0) / 1000.0,
                dspSummary.detectedPeaks.size,
                jitterPressure,
                strokeWidth,
                beaconHex
            )

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", payload))
            frameCounter++
        }
    }
}
