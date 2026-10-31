package com.lf2port.data

import com.badlogic.gdx.graphics.Pixmap
import kotlin.math.abs

/**
 * Decoder BMP untuk sprite LF2: 8-bit (RLE8 maupun tanpa kompresi), 4/24/32-bit.
 * Warna hitam murni (0,0,0) dijadikan transparan bila colorKeyBlack = true.
 */
object BmpDecoder {
    fun decode(d: ByteArray, colorKeyBlack: Boolean = true): Pixmap {
        require(d.size > 54 && d[0] == 'B'.code.toByte() && d[1] == 'M'.code.toByte()) { "Bukan file BMP" }
        fun u8(o: Int) = d[o].toInt() and 0xFF
        fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
        fun i32(o: Int) = u16(o) or (u16(o + 2) shl 16)

        val dataOff = i32(10)
        val hdr = i32(14)
        val w = i32(18)
        val h0 = i32(22)
        val bpp = u16(28)
        val comp = i32(30)
        val topDown = h0 < 0
        val h = abs(h0)
        val rgba = ByteArray(w * h * 4)

        fun put(row: Int, x: Int, r: Int, g: Int, b: Int) {
            if (x < 0 || x >= w || row < 0 || row >= h) return
            val y = if (topDown) row else h - 1 - row
            val o = (y * w + x) * 4
            if (colorKeyBlack && r == 0 && g == 0 && b == 0) {
                rgba[o] = 0; rgba[o + 1] = 0; rgba[o + 2] = 0; rgba[o + 3] = 0
            } else {
                rgba[o] = r.toByte(); rgba[o + 1] = g.toByte(); rgba[o + 2] = b.toByte(); rgba[o + 3] = 0xFF.toByte()
            }
        }

        var palR = IntArray(0)
        var palG = IntArray(0)
        var palB = IntArray(0)
        if (bpp <= 8) {
            var n = i32(46)
            if (n == 0) n = 1 shl bpp
            palR = IntArray(n); palG = IntArray(n); palB = IntArray(n)
            for (i in 0 until n) {
                val o = 14 + hdr + 4 * i
                palB[i] = u8(o); palG[i] = u8(o + 1); palR[i] = u8(o + 2)
            }
        }
        fun putIdx(row: Int, x: Int, idx: Int) {
            if (idx < palR.size) put(row, x, palR[idx], palG[idx], palB[idx])
        }

        when {
            bpp == 8 && comp == 1 -> { // RLE8
                var i = dataOff
                var x = 0
                var row = 0
                while (i + 1 < d.size && row < h) {
                    val n = u8(i)
                    val v = u8(i + 1)
                    i += 2
                    if (n > 0) {
                        for (k in 0 until n) putIdx(row, x + k, v)
                        x += n
                    } else when (v) {
                        0 -> { x = 0; row++ }
                        1 -> break
                        2 -> { x += u8(i); row += u8(i + 1); i += 2 }
                        else -> {
                            for (k in 0 until v) putIdx(row, x + k, u8(i + k))
                            x += v
                            i += v + (v and 1)
                        }
                    }
                }
            }
            bpp == 8 && comp == 0 -> {
                val stride = (w + 3) and 3.inv()
                for (row in 0 until h) for (x in 0 until w) putIdx(row, x, u8(dataOff + row * stride + x))
            }
            bpp == 4 && comp == 0 -> {
                val stride = (((w * 4) + 31) / 32) * 4
                for (row in 0 until h) for (x in 0 until w) {
                    val b = u8(dataOff + row * stride + x / 2)
                    putIdx(row, x, if (x % 2 == 0) b shr 4 else b and 15)
                }
            }
            bpp == 24 && comp == 0 -> {
                val stride = ((w * 3) + 3) and 3.inv()
                for (row in 0 until h) for (x in 0 until w) {
                    val o = dataOff + row * stride + x * 3
                    put(row, x, u8(o + 2), u8(o + 1), u8(o))
                }
            }
            bpp == 32 && (comp == 0 || comp == 3) -> {
                for (row in 0 until h) for (x in 0 until w) {
                    val o = dataOff + (row * w + x) * 4
                    put(row, x, u8(o + 2), u8(o + 1), u8(o))
                }
            }
            else -> throw IllegalArgumentException("Format BMP belum didukung: bpp=$bpp comp=$comp")
        }

        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val buf = pm.pixels
        buf.position(0)
        buf.put(rgba)
        buf.position(0)
        return pm
    }
}
