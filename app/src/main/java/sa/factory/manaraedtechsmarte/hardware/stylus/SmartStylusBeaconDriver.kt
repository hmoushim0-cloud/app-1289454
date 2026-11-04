package sa.factory.manaraedtechsmarte.hardware.stylus

import java.io.Serializable
import java.util.Locale

/**
 * Smart Stylus Digitizer (4096-Level Pressure) & BLE Classroom Beacon Telemetry Driver
 * Strictly Zero Emojis per factory invariant.
 */

enum class StylusDrawingTool(val baseWidthPx: Double, val maxScale: Double, val toolNameAr: String) {
    CHIRP_SLOPE_PEN(2.5, 3.0, "قلم إشارة الرادار FMCW"),
    RANGE_GATE_HIGHLIGHTER(12.0, 1.8, "محدد بوابات المدى"),
    ANTENNA_BEAM_VECTOR(4.0, 2.5, "متجه الإشعاع الهوائي"),
    TARGET_ANNOTATOR(3.0, 2.0, "مؤشر الأهداف المكتشفة")
}

enum class RadarBeaconState(val stateCode: Byte, val label: String) {
    IDLE(0x00, "خامل"),
    CHIRPING_TRANSMIT(0x01, "إرسال النبضات"),
    RECEIVE_PROCESSING(0x02, "معالجة الإشارة المرتدة"),
    CALIBRATING(0x03, "معايرة مختبرية"),
    FAULT(0x04, "تنبيه خطأ")
}

data class StylusDigitizerSample(
    val pressureRaw: Int, // 0..4095
    val tiltAzimuthDeg: Double,
    val tiltAltitudeDeg: Double,
    val strokeSpeedPxSec: Double,
    val selectedTool: StylusDrawingTool
) : Serializable {
    val normalizedPressure: Double
        get() = (pressureRaw.coerceIn(0, 4095)).toDouble() / 4095.0

    val calculatedStrokeWidthPx: Double
        get() {
            val scale = 0.5 + (0.5 * normalizedPressure * selectedTool.maxScale)
            return Math.round(selectedTool.baseWidthPx * scale * 100.0) / 100.0
        }
}

data class ClassroomBeaconTelemetryPacket(
    val stationId: Short,
    val radarState: RadarBeaconState,
    val targetCount: Byte,
    val nearestRangeCm: Short,
    val nearestVelocityCmPerSec: Short,
    val peakSnrDb: Byte,
    val stylusPressureRaw: Short,
    val sequenceCounter: Int
) : Serializable

class SmartStylusBeaconDriver {

    companion object {
        const val PREAMBLE_BYTE_1: Byte = 0xBE.toByte()
        const val PREAMBLE_BYTE_2: Byte = 0xAC.toByte()
        const val PACKET_TOTAL_SIZE = 19
    }

    /**
     * Calculates dynamic stroke width for stylus digitizer drawing.
     */
    fun calculateStrokeWidth(tool: StylusDrawingTool, rawPressure: Int): Double {
        val sample = StylusDigitizerSample(
            pressureRaw = rawPressure,
            tiltAzimuthDeg = 45.0,
            tiltAltitudeDeg = 60.0,
            strokeSpeedPxSec = 100.0,
            selectedTool = tool
        )
        return sample.calculatedStrokeWidthPx
    }

    /**
     * Computes standard CRC-16-CCITT (polynomial 0x1021, init 0xFFFF).
     */
    fun computeCrc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0xFFFF
        val polynomial = 0x1021

        for (i in offset until (offset + length)) {
            val b = data[i].toInt() and 0xFF
            for (j in 0 until 8) {
                val bit = ((b shr (7 - j)) and 1) == 1
                val c15 = ((crc shr 15) and 1) == 1
                crc = (crc shl 1) and 0xFFFF
                if (c15 xor bit) {
                    crc = crc xor polynomial
                }
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Serializes a classroom BLE beacon telemetry packet into a 19-byte binary advertisement frame:
     * Bytes 0..1: Preamble 0xBE, 0xAC
     * Bytes 2..3: Station ID (Short)
     * Byte 4: Radar State (Byte)
     * Byte 5: Target Count (Byte)
     * Bytes 6..7: Nearest Range in CM (Short)
     * Bytes 8..9: Nearest Velocity in CM/s (Short)
     * Byte 10: Peak SNR in dB (Byte)
     * Bytes 11..12: Stylus Pressure (Short)
     * Bytes 13..16: Sequence Counter (Int)
     * Bytes 17..18: CRC-16 Checksum (Short)
     */
    fun serializeBeaconPacket(packet: ClassroomBeaconTelemetryPacket): ByteArray {
        val buf = ByteArray(PACKET_TOTAL_SIZE)
        buf[0] = PREAMBLE_BYTE_1
        buf[1] = PREAMBLE_BYTE_2

        // Station ID
        buf[2] = ((packet.stationId.toInt() shr 8) and 0xFF).toByte()
        buf[3] = (packet.stationId.toInt() and 0xFF).toByte()

        // Radar State & Target Count
        buf[4] = packet.radarState.stateCode
        buf[5] = packet.targetCount

        // Range & Velocity
        buf[6] = ((packet.nearestRangeCm.toInt() shr 8) and 0xFF).toByte()
        buf[7] = (packet.nearestRangeCm.toInt() and 0xFF).toByte()

        buf[8] = ((packet.nearestVelocityCmPerSec.toInt() shr 8) and 0xFF).toByte()
        buf[9] = (packet.nearestVelocityCmPerSec.toInt() and 0xFF).toByte()

        // SNR
        buf[10] = packet.peakSnrDb

        // Stylus Pressure
        buf[11] = ((packet.stylusPressureRaw.toInt() shr 8) and 0xFF).toByte()
        buf[12] = (packet.stylusPressureRaw.toInt() and 0xFF).toByte()

        // Sequence Counter
        buf[13] = ((packet.sequenceCounter shr 24) and 0xFF).toByte()
        buf[14] = ((packet.sequenceCounter shr 16) and 0xFF).toByte()
        buf[15] = ((packet.sequenceCounter shr 8) and 0xFF).toByte()
        buf[16] = (packet.sequenceCounter and 0xFF).toByte()

        // CRC-16 over first 17 bytes
        val crc = computeCrc16(buf, 0, 17)
        buf[17] = ((crc shr 8) and 0xFF).toByte()
        buf[18] = (crc and 0xFF).toByte()

        return buf
    }

    /**
     * Decodes and validates a 19-byte BLE classroom beacon telemetry packet.
     */
    fun deserializeBeaconPacket(raw: ByteArray): ClassroomBeaconTelemetryPacket? {
        if (raw.size != PACKET_TOTAL_SIZE) return null
        if (raw[0] != PREAMBLE_BYTE_1 || raw[1] != PREAMBLE_BYTE_2) return null

        val expectedCrc = computeCrc16(raw, 0, 17)
        val actualCrc = (((raw[17].toInt() and 0xFF) shl 8) or (raw[18].toInt() and 0xFF)) and 0xFFFF

        if (expectedCrc != actualCrc) {
            return null // CRC mismatch
        }

        val stationId = (((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)).toShort()
        val stateByte = raw[4]
        val radarState = RadarBeaconState.values().firstOrNull { it.stateCode == stateByte } ?: RadarBeaconState.IDLE
        val targetCount = raw[5]

        val rangeCm = (((raw[6].toInt() and 0xFF) shl 8) or (raw[7].toInt() and 0xFF)).toShort()
        val velocityCm = (((raw[8].toInt() and 0xFF) shl 8) or (raw[9].toInt() and 0xFF)).toShort()
        val snr = raw[10]
        val pressure = (((raw[11].toInt() and 0xFF) shl 8) or (raw[12].toInt() and 0xFF)).toShort()

        val seq = ((raw[13].toInt() and 0xFF) shl 24) or
                  ((raw[14].toInt() and 0xFF) shl 16) or
                  ((raw[15].toInt() and 0xFF) shl 8) or
                  (raw[16].toInt() and 0xFF)

        return ClassroomBeaconTelemetryPacket(
            stationId = stationId,
            radarState = radarState,
            targetCount = targetCount,
            nearestRangeCm = rangeCm,
            nearestVelocityCmPerSec = velocityCm,
            peakSnrDb = snr,
            stylusPressureRaw = pressure,
            sequenceCounter = seq
        )
    }
}
