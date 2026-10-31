package com.lf2port.data

/** Dekripsi file .dat LF2: 123 byte header, lalu kunci 37 byte berulang (mulai offset 12). */
object Lf2Crypto {
    private val KEY = "SiuHungIsAGoodBearBecauseHeIsVeryGood".toByteArray(Charsets.ISO_8859_1)
    private const val HEADER = 123
    private const val KEY_SHIFT = 12

    fun decrypt(raw: ByteArray): ByteArray {
        if (raw.size <= HEADER) return ByteArray(0)
        val out = ByteArray(raw.size - HEADER)
        for (i in out.indices) {
            out[i] = (raw[HEADER + i].toInt() - KEY[(i + KEY_SHIFT) % KEY.size].toInt()).toByte()
        }
        return out
    }

    private fun looksLikeText(b: ByteArray): Boolean {
        val n = minOf(b.size, 2000)
        if (n == 0) return false
        var ok = 0
        for (i in 0 until n) {
            val c = b[i].toInt() and 0xFF
            if (c == 9 || c == 10 || c == 13 || c in 32..126) ok++
        }
        return ok.toFloat() / n > 0.97f
    }

    /** Baca isi .dat sebagai teks. Jika tidak terenkripsi, dipakai apa adanya. */
    fun readDat(raw: ByteArray): String {
        val dec = decrypt(raw)
        val bytes = if (looksLikeText(dec)) dec else raw
        return String(bytes, Charsets.ISO_8859_1)
    }
}
