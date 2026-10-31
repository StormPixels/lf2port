package com.lf2port.engine

import kotlin.math.abs
import kotlin.random.Random

/** AI paling sederhana: dekati target, sejajarkan z, lalu pukul. */
class Ai(private val self: Fighter, private val target: () -> Fighter?) {
    private val pad = Pad()
    private var cooldown = 30

    fun think(): Pad {
        pad.clear()
        if (cooldown > 0) cooldown--
        val t = target() ?: return pad
        if (t.hp <= 0f || self.hp <= 0f) return pad
        val st = self.state
        if (st != 0 && st != 1) return pad

        val dx = t.x - self.x
        val dz = t.z - self.z
        val ax = abs(dx)
        val toward = if (dx >= 0f) 1 else -1

        if (ax > 62f || self.facing != toward) {
            if (toward > 0) pad.right = true else pad.left = true
        }
        if (abs(dz) > 10f) {
            if (dz > 0f) pad.down = true else pad.up = true
        }
        if (cooldown == 0 && ax < 70f && abs(dz) < 16f && self.facing == toward) {
            pad.a = true
            cooldown = 30 + Random.nextInt(30)
        }
        return pad
    }
}
