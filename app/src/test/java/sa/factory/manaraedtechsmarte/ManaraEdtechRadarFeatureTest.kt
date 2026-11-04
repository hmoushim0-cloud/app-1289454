package sa.factory.manaraedtechsmarte

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.manaraedtechsmarte.domain.radar.*
import sa.factory.manaraedtechsmarte.hardware.stylus.*

/**
 * Unit Tests for FMCW Radar Physics, DSP Detection & Classroom Stylus Beacon Driver
 * Strictly Zero Emojis per factory invariant.
 */
class ManaraEdtechRadarFeatureTest {

    private lateinit var radarEngine: FmcwRadarDspEngine
    private lateinit var stylusDriver: SmartStylusBeaconDriver

    @Before
    fun setUp() {
        val config = FmcwRadarConfig(
            carrierFrequencyHz = 77.0e9, // 77 GHz
            sweepBandwidthHz = 1.0e9,    // 1 GHz
            chirpDurationSec = 50.0e-6,  // 50 us
            samplingRateHz = 10.0e6,     // 10 MS/s
            numSamplesPerChirp = 512,
            numChirpsPerFrame = 128
        )
        radarEngine = FmcwRadarDspEngine(config)
        stylusDriver = SmartStylusBeaconDriver()
    }

    @Test
    fun testFmcwChirpPhysicsAndResolution() {
        val cfg = radarEngine.config

        // Wavelength lambda = c / fc -> 299792458 / 77e9 = ~0.0038934 m (3.89 mm)
        assertEquals(0.0038934, cfg.wavelengthMeters, 0.0001)

        // Chirp slope S = B / Tc -> 1e9 / 50e-6 = 2.0e13 Hz/s
        assertEquals(2.0e13, cfg.chirpSlopeHzPerSec, 1.0e9)

        // Range resolution deltaR = c / (2 * B) -> 299792458 / 2e9 = ~0.1499 m (15 cm)
        assertEquals(0.1499, cfg.rangeResolutionMeters, 0.001)

        // Max unambiguous range = (Fs * c) / (4 * S) -> (10e6 * 299792458) / (8e13) = 37.47 m
        assertEquals(37.47, cfg.maxUnambiguousRangeMeters, 0.1)

        // Max unambiguous velocity = lambda / (4 * Tc) -> 0.0038934 / (200e-6) = 19.46 m/s (~70 km/h)
        assertEquals(19.46, cfg.maxUnambiguousVelocityMps, 0.1)
    }

    @Test
    fun testBeatFrequencyAndRangeCalculation() {
        val targetRangeMeters = 25.0 // 25 meters target
        val expectedFb = (2.0 * radarEngine.config.chirpSlopeHzPerSec * targetRangeMeters) / FmcwRadarConfig.SPEED_OF_LIGHT

        val calculatedFb = radarEngine.calculateBeatFrequency(rangeMeters = targetRangeMeters, velocityMps = 0.0)
        assertEquals(expectedFb, calculatedFb, 1.0)

        // Inverse calculation: fb back to Range
        val invertedRange = radarEngine.calculateRangeFromBeatFrequency(calculatedFb)
        assertEquals(targetRangeMeters, invertedRange, 0.001)
    }

    @Test
    fun testDopplerShiftAndRadialVelocityConversion() {
        val targetVelocityMps = 15.0 // 15 m/s (~54 km/h approaching target)
        val expectedFd = (2.0 * targetVelocityMps) / radarEngine.config.wavelengthMeters

        val calculatedFd = radarEngine.calculateDopplerShift(targetVelocityMps)
        assertEquals(expectedFd, calculatedFd, 0.1)

        // Inverse calculation: fd back to Velocity
        val invertedVelocity = radarEngine.calculateVelocityFromDoppler(calculatedFd)
        assertEquals(targetVelocityMps, invertedVelocity, 0.001)
    }

    @Test
    fun testRangePeakDetectionAndCfarThreshold() {
        val targets = listOf(
            RadarTarget("T1", 10.0, 0.0, 10.0, 0.0, 26.0), // High SNR
            RadarTarget("T2", 28.5, 5.0, 5.0, 10.0, 8.0)    // Low SNR below 12dB CFAR
        )

        val peaks = radarEngine.detectRangePeaks(targets, cfarNoiseThresholdDb = 12.0)
        assertEquals("Both targets must generate spectrum peaks", 2, peaks.size)

        val t1Peak = peaks.firstOrNull { it.calculatedRangeMeters in 9.5..10.5 }
        assertNotNull(t1Peak)
        assertTrue("Target with 26dB SNR must be confirmed", t1Peak?.isConfirmedDetection == true)

        val t2Peak = peaks.firstOrNull { it.calculatedRangeMeters in 28.0..29.0 }
        assertNotNull(t2Peak)
        assertFalse("Target with 8dB SNR must not be confirmed under 12dB threshold", t2Peak?.isConfirmedDetection == true)

        // Test frame processor
        val summary = radarEngine.processRadarFrame(targets)
        assertEquals(10.0, summary.nearestTargetRangeMeters ?: 0.0, 0.5)
        assertTrue(summary.executionSummary.contains("FMCW Frame"))
    }

    @Test
    fun testStylusDigitizerDynamicStrokeScaling() {
        val penTool = StylusDrawingTool.CHIRP_SLOPE_PEN
        val highlighterTool = StylusDrawingTool.RANGE_GATE_HIGHLIGHTER

        val lightStroke = stylusDriver.calculateStrokeWidth(penTool, rawPressure = 0)
        val heavyStroke = stylusDriver.calculateStrokeWidth(penTool, rawPressure = 4095)

        assertTrue("Higher stylus pressure must produce thicker stroke", heavyStroke > lightStroke)

        val highlighterStroke = stylusDriver.calculateStrokeWidth(highlighterTool, rawPressure = 2048)
        assertTrue("Highlighter tool base width must exceed standard pen width", highlighterStroke > heavyStroke)
    }

    @Test
    fun testClassroomBleBeaconPacketSerializationAndCrc16() {
        val packet = ClassroomBeaconTelemetryPacket(
            stationId = 0x4A12.toShort(),
            radarState = RadarBeaconState.CHIRPING_TRANSMIT,
            targetCount = 3.toByte(),
            nearestRangeCm = 1250.toShort(), // 12.50 meters
            nearestVelocityCmPerSec = (-450).toShort(), // -4.5 m/s
            peakSnrDb = 28.toByte(),
            stylusPressureRaw = 3100.toShort(),
            sequenceCounter = 1042
        )

        val serialized = stylusDriver.serializeBeaconPacket(packet)
        assertEquals("Beacon packet size must be 19 bytes", 19, serialized.size)
        assertEquals("Preamble byte 0 must be 0xBE", 0xBE.toByte(), serialized[0])
        assertEquals("Preamble byte 1 must be 0xAC", 0xAC.toByte(), serialized[1])

        val deserialized = stylusDriver.deserializeBeaconPacket(serialized)
        assertNotNull("Deserialized packet must not be null", deserialized)

        assertEquals(packet.stationId, deserialized?.stationId)
        assertEquals(packet.radarState, deserialized?.radarState)
        assertEquals(packet.targetCount, deserialized?.targetCount)
        assertEquals(packet.nearestRangeCm, deserialized?.nearestRangeCm)
        assertEquals(packet.nearestVelocityCmPerSec, deserialized?.nearestVelocityCmPerSec)
        assertEquals(packet.peakSnrDb, deserialized?.peakSnrDb)
        assertEquals(packet.stylusPressureRaw, deserialized?.stylusPressureRaw)
        assertEquals(packet.sequenceCounter, deserialized?.sequenceCounter)
    }

    @Test
    fun testCorruptedBeaconPacketRejection() {
        val packet = ClassroomBeaconTelemetryPacket(
            stationId = 0x1111.toShort(),
            radarState = RadarBeaconState.RECEIVE_PROCESSING,
            targetCount = 1.toByte(),
            nearestRangeCm = 500.toShort(),
            nearestVelocityCmPerSec = 0.toShort(),
            peakSnrDb = 20.toByte(),
            stylusPressureRaw = 2000.toShort(),
            sequenceCounter = 55
        )

        val validBytes = stylusDriver.serializeBeaconPacket(packet)

        // 1. Corrupt CRC byte
        val badCrc = validBytes.clone()
        badCrc[18] = (badCrc[18] + 1).toByte()
        assertNull("Packet with corrupted CRC must be rejected", stylusDriver.deserializeBeaconPacket(badCrc))

        // 2. Corrupt preamble byte
        val badPreamble = validBytes.clone()
        badPreamble[0] = 0x00.toByte()
        assertNull("Packet with corrupted preamble must be rejected", stylusDriver.deserializeBeaconPacket(badPreamble))

        // 3. Invalid length
        val truncated = validBytes.copyOf(15)
        assertNull("Truncated packet must be rejected", stylusDriver.deserializeBeaconPacket(truncated))
    }
}
