package com.lf2port.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Spark(val x: Float, val z: Float, val h: Float, var ttl: Int)

class World(val bgWidth: Int, val zMin: Int, val zMax: Int) {
    val fighters = ArrayList<Fighter>()
    val sparks = ArrayList<Spark>()
    var player: Fighter? = null

    fun tick(playerPad: Pad) {
        for (f in fighters) {
            val p = f.ai?.think() ?: playerPad
            f.tick(p, bgWidth, zMin, zMax)
        }
        resolveHits()

        for (f in fighters) {
            if (f.deadTicks > 75) {
                val pl = player
                if (f === pl || pl == null) {
                    f.revive(f.x, f.z)
                } else {
                    val side = if (pl.x < bgWidth / 2f) 1 else -1
                    val nx = (pl.x + side * (350f + (f.z % 100f))).coerceIn(0f, bgWidth.toFloat())
                    f.revive(nx, (zMin + zMax) / 2f)
                }
            }
        }

        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.ttl--
            if (s.ttl <= 0) it.remove()
        }
    }

    private fun resolveHits() {
        for (a in fighters) {
            if (a.arestTimer > 0) continue
            val itrs = a.frame.itr.filter { it.kind == 0 }
            if (itrs.isEmpty()) continue
            val ar = a.rects(itrs)
            for (v in fighters) {
                if (v === a || v.team == a.team) continue
                if (v.state == 14) continue // tergeletak: kebal
                if (v.vrest.containsKey(a) || a.hitThisFrame.contains(v)) continue
                val vr = v.rects(v.frame.bdy)
                var hitA: Rect? = null
                var hitB: Rect? = null
                outer@ for (r in ar) {
                    if (abs(v.z - a.z) > r.box.int("zwidth", 12)) continue
                    for (b in vr) {
                        if (r.x0 < b.x1 && b.x0 < r.x1 && r.bottom < b.top && b.bottom < r.top) {
                            hitA = r
                            hitB = b
                            break@outer
                        }
                    }
                }
                val ha = hitA ?: continue
                val hb = hitB ?: continue

                v.takeHit(a, ha.box)
                val arest = ha.box.int("arest")
                val vrest = ha.box.int("vrest")
                if (arest > 0) a.arestTimer = arest
                if (vrest > 0) v.vrest[a] = vrest
                a.hitThisFrame.add(v)

                val cx = (max(ha.x0, hb.x0) + min(ha.x1, hb.x1)) / 2f
                val ch = (min(ha.top, hb.top) + max(ha.bottom, hb.bottom)) / 2f
                sparks.add(Spark(cx, v.z, ch, 6))
            }
        }
    }
}
