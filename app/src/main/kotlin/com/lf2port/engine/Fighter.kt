package com.lf2port.engine

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lf2port.data.Frame
import com.lf2port.data.ObjectDat

class Pad {
    var left = false
    var right = false
    var up = false
    var down = false
    var a = false
    var j = false
    var d = false
}

/**
 * Petarung dengan logika frame ala LF2 (30 tick/detik).
 * Koordinat: x (horizontal), z (kedalaman, = posisi y layar di tanah), y (tinggi, positif ke atas).
 * vy mengikuti konvensi LF2: negatif = naik.
 * Sudah ada: state 0-2 (diam/jalan/lari), 3 (serang), 4 (lompat), 5 (dash), 7 (tangkis), 15 (mendarat).
 * Belum ada: damage, benda/senjata, tangkap, terluka/jatuh, AI.
 */
class Fighter(private val obj: ObjectDat, var x: Float, var z: Float) {
    var y = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var facing = 1
    var frame: Frame = obj.frames[0] ?: obj.frames.values.first()
        private set

    private var counter = frame.int("wait")
    private var pad = Pad()
    private var tickNo = 0
    private var altPunch = false
    private var pLeft = false
    private var pRight = false
    private var pA = false
    private var pJ = false
    private val lastPress = intArrayOf(-100, -100)

    private fun prm(k: String, d: Float): Float = obj.params[k]?.trim()?.toFloatOrNull() ?: d

    private fun fv(f: Frame, k: String): Float =
        f.props[k]?.trim()?.split(' ')?.firstOrNull()?.toFloatOrNull() ?: 0f

    private fun enter(id: Int) {
        val f = obj.frames[id] ?: return
        frame = f
        counter = f.int("wait")
        val dvx = fv(f, "dvx")
        val dvy = fv(f, "dvy")
        val dvz = fv(f, "dvz")
        if (dvx != 0f && dvx != 550f) vx = dvx * facing
        if (dvy != 0f && dvy != 550f) vy = dvy
        if (dvz != 0f && dvz != 550f) vz = dvz
    }

    fun tick(p: Pad, bgWidth: Int, zMin: Int, zMax: Int) {
        pad = p
        tickNo++
        val dirX = (if (p.right) 1 else 0) - (if (p.left) 1 else 0)
        val dirZ = (if (p.down) 1 else 0) - (if (p.up) 1 else 0)

        var doubleTap = false
        if (p.left && !pLeft) {
            if (tickNo - lastPress[0] <= 12) doubleTap = true
            lastPress[0] = tickNo
        }
        if (p.right && !pRight) {
            if (tickNo - lastPress[1] <= 12) doubleTap = true
            lastPress[1] = tickNo
        }
        val aEdge = p.a && !pA
        val jEdge = p.j && !pJ
        pLeft = p.left; pRight = p.right; pA = p.a; pJ = p.j

        control(dirX, dirZ, doubleTap, aEdge, jEdge)
        advance(dirX, dirZ)
        physics()

        x = x.coerceIn(0f, bgWidth.toFloat())
        z = z.coerceIn(zMin.toFloat(), zMax.toFloat())
    }

    private fun control(dirX: Int, dirZ: Int, doubleTap: Boolean, aEdge: Boolean, jEdge: Boolean) {
        val st = frame.int("state")
        val grounded = y <= 0f && vy >= 0f
        if (grounded && st in 0..2) {
            when {
                aEdge -> {
                    if (st == 2) {
                        enter(85)
                    } else {
                        enter(if (altPunch) 65 else 60)
                        altPunch = !altPunch
                    }
                }
                jEdge -> {
                    if (st == 2) {
                        enter(213) // dash
                        vy = prm("dash_height", -10f)
                        vx = facing * prm("dash_distance", 18f)
                        vz = dirZ * prm("dash_distancez", 5f)
                    } else {
                        if (dirX != 0) facing = dirX
                        enter(210) // jongkok sebelum lompat
                    }
                }
                pad.d && st != 2 -> {
                    vx = 0f; vz = 0f
                    enter(110)
                }
                else -> locomotion(st, dirX, dirZ, doubleTap)
            }
        } else if (!grounded && st == 4 && aEdge) {
            enter(80) // serangan lompat
        }
    }

    private fun locomotion(st: Int, dirX: Int, dirZ: Int, doubleTap: Boolean) {
        if (st == 2) {
            if (dirX != facing) {
                enter(218) // berhenti berlari
            } else {
                vx = facing * prm("running_speed", 9.6f)
                vz = dirZ * prm("running_speedz", 1.58f)
            }
            return
        }
        if (dirX != 0 || dirZ != 0) {
            if (dirX != 0) facing = dirX
            if (doubleTap && dirX != 0) {
                enter(9)
                vx = facing * prm("running_speed", 9.6f)
                vz = dirZ * prm("running_speedz", 1.58f)
            } else {
                if (st == 0) enter(5)
                vx = dirX * prm("walking_speed", 5f)
                vz = dirZ * prm("walking_speedz", 2.5f)
            }
        } else {
            if (st == 1) enter(0)
            vx = 0f
            vz = 0f
        }
    }

    private fun advance(dirX: Int, dirZ: Int) {
        if (counter > 0) {
            counter--
            return
        }
        val st = frame.int("state")
        val id = frame.id
        val n = frame.int("next")
        when {
            st == 1 -> enter(if (id >= 8) 5 else id + 1)
            st == 2 -> enter(if (id >= 11) 9 else id + 1)
            st == 7 && (id == 110 || id == 111) -> enter(if (pad.d) 110 else 0)
            id == 210 -> { // lepas landas
                vy = prm("jump_height", -16.3f)
                vx = dirX * prm("jump_distance", 10f)
                vz = dirZ * prm("jump_distancez", 3.75f)
                enter(if (n == 0 || n == 999) 211 else n)
            }
            n == 0 || n == 999 -> {
                if (y > 0f || vy < 0f) counter = frame.int("wait") // tahan frame selama di udara
                else enter(0)
            }
            else -> enter(n)
        }
    }

    private fun physics() {
        val st = frame.int("state")
        val wasAir = y > 0f || vy < 0f
        x += vx
        z += vz
        if (wasAir) {
            y -= vy
            vy += GRAVITY
            if (y <= 0f) {
                y = 0f
                if (vy >= 0f) {
                    vy = 0f
                    if (st == 3 || st == 4 || st == 5) enter(215) // mendarat
                }
            }
        } else if (st == 3 || st == 15) {
            vx *= 0.7f
            vz *= 0.7f
        }
    }

    fun draw(batch: SpriteBatch, textures: List<Texture>, camX: Float) {
        val f = frame
        val pic = f.int("pic")
        val si = obj.sheets.indexOfFirst { pic in it.first..it.last }
        if (si < 0 || si >= textures.size) return
        val s = obj.sheets[si]
        val idx = pic - s.first
        val sx = (idx % s.perRow) * (s.w + 1)
        val sy = (idx / s.perRow) * (s.h + 1)
        val r = TextureRegion(textures[si], sx, sy, s.w, s.h)
        r.flip(facing < 0, true)
        val cx = f.int("centerx").toFloat()
        val cy = f.int("centery").toFloat()
        val dx = if (facing > 0) x - camX - cx else x - camX - (s.w - cx)
        val dy = z - y - cy
        batch.draw(r, dx, dy, s.w.toFloat(), s.h.toFloat())
    }

    companion object {
        const val GRAVITY = 1.7f
    }
}
