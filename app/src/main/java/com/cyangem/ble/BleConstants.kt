package com.cyangem.ble

import java.util.UUID

/**
 * Known BLE constants for HeyCyan glasses.
 *
 * Service UUIDs confirmed from reverse-engineered SDK (FerSaiyan/Alternative-HeyCyan-App-and-SDK).
 * Characteristic UUIDs: Primary service chars are derived from common vendor patterns.
 * Secondary service matches Nordic UART Service (NUS) structure.
 *
 * ⚠️ IF COMMANDS DON'T RESPOND: Use the BLE Inspector in the app's Settings screen
 * to discover the actual characteristics on your device and update CHAR_WRITE / CHAR_NOTIFY below.
 */
object BleConstants {

    // ── Service UUIDs (confirmed from BLE Inspector on W630_7B3B) ───────────────
    val SERVICE_PRIMARY: UUID   = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
    val SERVICE_SECONDARY: UUID = UUID.fromString("0000ae3a-0000-1000-8000-00805f9b34fb")
    val SERVICE_DATA: UUID      = UUID.fromString("00003802-0000-1000-8000-00805f9b34fb")

    // ── Characteristic UUIDs (confirmed from BLE Inspector) ───────────────────
    // Primary service (ae30)
    val CHAR_WRITE: UUID  = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb") // props=4  WRITE
    val CHAR_NOTIFY: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb") // props=16 NOTIFY
    val CHAR_WRITE2: UUID = UUID.fromString("0000ae03-0000-1000-8000-00805f9b34fb") // props=4  WRITE
    val CHAR_NOTIFY2: UUID= UUID.fromString("0000ae04-0000-1000-8000-00805f9b34fb") // props=16 NOTIFY
    val CHAR_INDICATE: UUID=UUID.fromString("0000ae05-0000-1000-8000-00805f9b34fb") // props=32 INDICATE
    val CHAR_RW: UUID     = UUID.fromString("0000ae10-0000-1000-8000-00805f9b34fb") // props=10 READ+WRITE

    // Data service (3802) — likely used for media/AI data transfer
    val CHAR_DATA: UUID   = UUID.fromString("00004a02-0000-1000-8000-00805f9b34fb") // props=26 READ+WRITE+NOTIFY

    // Secondary service (ae3a)
    val NUS_RX: UUID = UUID.fromString("0000ae3b-0000-1000-8000-00805f9b34fb") // props=4  WRITE
    val NUS_TX: UUID = UUID.fromString("0000ae3c-0000-1000-8000-00805f9b34fb") // props=16 NOTIFY

    // Standard CCCD descriptor (enable notifications)
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── Command bytes ──────────────────────────────────────────────────────────
    // Derived from iOS QCSDK QCOperatorDeviceMode enum + binary analysis
    const val CMD_PHOTO: Byte        = 0x01
    const val CMD_VIDEO_START: Byte  = 0x02
    const val CMD_VIDEO_STOP: Byte   = 0x03
    const val CMD_AUDIO_START: Byte  = 0x04
    const val CMD_AUDIO_STOP: Byte   = 0x05
    const val CMD_AI_PHOTO: Byte     = 0x06  // triggers on-glass AI → we intercept & route to Gemini
    const val CMD_BATTERY_REQ: Byte  = 0x07
    const val CMD_VERSION_REQ: Byte  = 0x08
    const val CMD_MEDIA_COUNT: Byte  = 0x09
    const val CMD_DATETIME_SYNC: Byte = 0x0A

    // ── Command frame builder ──────────────────────────────────────────────────
    /**
     * Build a command frame: [0xAA, cmd, ...payload, checksum]
     * HeyCyan uses a simple header + checksum protocol.
     */
    fun buildCommand(cmd: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val frame = ByteArray(3 + payload.size)
        frame[0] = 0xAA.toByte()   // header
        frame[1] = cmd
        frame[2] = payload.size.toByte()
        payload.copyInto(frame, 3)
        return frame
    }

    /**
     * Build datetime sync payload from current system time.
     */
    fun buildDatetimePayload(): ByteArray {
        val cal = java.util.Calendar.getInstance()
        return byteArrayOf(
            (cal.get(java.util.Calendar.YEAR) - 2000).toByte(),
            (cal.get(java.util.Calendar.MONTH) + 1).toByte(),
            cal.get(java.util.Calendar.DAY_OF_MONTH).toByte(),
            cal.get(java.util.Calendar.HOUR_OF_DAY).toByte(),
            cal.get(java.util.Calendar.MINUTE).toByte(),
            cal.get(java.util.Calendar.SECOND).toByte()
        )
    }

    // ── Wi-Fi / HTTP media sync ────────────────────────────────────────────────
    val GLASSES_IP_CANDIDATES = listOf(
        "192.168.43.1",  // Android hotspot default
        "192.168.49.1",  // Wi-Fi Direct group owner
        "192.168.4.1",
        "192.168.1.1",
        "10.0.0.1"
    )
    const val MEDIA_CONFIG_PATH = "/files/media.config"
    const val MEDIA_FILES_BASE  = "/files/"
    const val HTTP_PORT         = 80
    const val HTTP_TIMEOUT_SEC  = 10L

    // ── Device name prefix for BLE scan filter ─────────────────────────────────
    val DEVICE_NAME_PREFIXES = listOf("HeyCyan", "HCyan", "QC-", "G300", "W610", "W630", "M01S")
}
