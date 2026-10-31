package com.lf2port

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import com.lf2port.data.AssetIndex
import com.lf2port.data.BmpDecoder
import com.lf2port.data.Lf2Crypto
import com.lf2port.data.ObjectDat
import com.lf2port.data.ObjectParser

/**
 * Milestone 1: penampil data. Memuat karakter dari data.txt, menampilkan animasi frame
 * beserta kotak bdy (hijau) dan itr (merah) untuk memverifikasi parser dan sprite.
 *
 * Kontrol: ketuk sisi kiri/kanan = ganti karakter, ketuk bagian bawah = frame berikutnya (jeda),
 * ketuk bagian atas = lanjutkan animasi.
 */
class Lf2Game(private val roots: List<String>) : ApplicationAdapter() {
    private class Entry(val id: Int, val file: String)

    private lateinit var assets: AssetIndex
    private lateinit var batch: SpriteBatch
    private lateinit var shapes: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var cam: OrthographicCamera
    private lateinit var viewport: FitViewport

    private val entries = ArrayList<Entry>()
    private var entryIdx = 0
    private var obj: ObjectDat? = null
    private var textures = ArrayList<Texture>()
    private var error: String? = null

    private var curFrame = 0
    private var ticks = 0
    private var acc = 0f
    private var paused = false

    private val scale = 3f
    private val anchorX = 400f
    private val anchorY = 330f

    override fun create() {
        cam = OrthographicCamera()
        viewport = FitViewport(800f, 450f, cam)
        cam.setToOrtho(true, 800f, 450f)
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        font = BitmapFont(true) // font bawaan libGDX, y-down
        try {
            val dir = roots.map { Gdx.files.absolute(it) }.firstOrNull { it.child("data/data.txt").exists() }
                ?: throw IllegalStateException(
                    "Aset game belum ada. Salin isi folder LF2_v2.0a_darkfiren ke salah satu folder ini " +
                        "(data/data.txt harus ada di dalamnya):\n" + roots.joinToString("\n")
                )
            assets = AssetIndex(dir)
            val dataTxt = assets.resolve("data/data.txt")
                ?: throw IllegalStateException("data/data.txt tidak ditemukan")
            val text = dataTxt.readString("ISO-8859-1").substringBefore("<background>")
            Regex("""id:\s*(\d+)\s+type:\s*0\s+file:\s*(\S+)""").findAll(text).forEach {
                entries.add(Entry(it.groupValues[1].toInt(), it.groupValues[2]))
            }
            if (entries.isEmpty()) throw IllegalStateException("Tidak ada karakter di data.txt")
            loadEntry(0)
        } catch (e: Exception) {
            error = e.toString()
        }
    }

    private fun loadEntry(i: Int) {
        textures.forEach { it.dispose() }
        textures = ArrayList()
        obj = null
        error = null
        entryIdx = (i + entries.size) % entries.size
        val e = entries[entryIdx]
        try {
            val fh = assets.resolve("data/" + e.file.substringAfter("data\\").substringAfter("data/"))
                ?: assets.resolve(e.file)
                ?: throw IllegalStateException("File tidak ditemukan: ${e.file}")
            val o = ObjectParser.parse(Lf2Crypto.readDat(fh.readBytes()))
            for (s in o.sheets) {
                val bmp = assets.resolve(s.path) ?: throw IllegalStateException("Sprite tidak ada: ${s.path}")
                val pm = BmpDecoder.decode(bmp.readBytes())
                val tex = Texture(pm)
                tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                pm.dispose()
                textures.add(tex)
            }
            obj = o
            curFrame = if (o.frames.containsKey(0)) 0 else (o.frames.keys.firstOrNull() ?: 0)
            ticks = 0
            paused = false
        } catch (ex: Exception) {
            error = "${e.file}: $ex"
        }
    }

    private fun tick() {
        val o = obj ?: return
        val f = o.frames[curFrame] ?: return
        ticks++
        if (ticks > f.int("wait")) {
            ticks = 0
            val next = f.int("next", 0)
            val target = if (next == 999 || next == 0) 0 else next
            curFrame = if (o.frames.containsKey(target)) target else 0
        }
    }

    private fun handleInput() {
        if (!Gdx.input.justTouched()) return
        val o = obj
        val x = Gdx.input.x.toFloat() / Gdx.graphics.width
        val y = Gdx.input.y.toFloat() / Gdx.graphics.height
        when {
            y > 0.8f && o != null -> {
                val keys = o.frames.keys.sorted()
                val at = keys.indexOf(curFrame)
                curFrame = keys[(at + 1) % keys.size]
                ticks = 0
                paused = true
            }
            y < 0.2f -> paused = false
            x < 0.5f -> loadEntry(entryIdx - 1)
            else -> loadEntry(entryIdx + 1)
        }
    }

    override fun render() {
        handleInput()
        if (!paused) {
            acc += Gdx.graphics.deltaTime
            while (acc >= 1f / 30f) { acc -= 1f / 30f; tick() }
        }

        Gdx.gl.glClearColor(0.12f, 0.12f, 0.16f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        cam.update()
        batch.projectionMatrix = cam.combined
        shapes.projectionMatrix = cam.combined

        val o = obj
        val f = o?.frames?.get(curFrame)
        var ox = 0f
        var oy = 0f

        batch.begin()
        if (o != null && f != null) {
            val pic = f.int("pic")
            val si = o.sheets.indexOfFirst { pic in it.first..it.last }
            if (si >= 0 && si < textures.size) {
                val s = o.sheets[si]
                val idx = pic - s.first
                val sx = (idx % s.perRow) * (s.w + 1)
                val sy = (idx / s.perRow) * (s.h + 1)
                val region = TextureRegion(textures[si], sx, sy, s.w, s.h)
                region.flip(false, true) // kamera y-bawah: balik tekstur agar tegak
                ox = anchorX - f.int("centerx") * scale
                oy = anchorY - f.int("centery") * scale
                batch.draw(region, ox, oy, s.w * scale, s.h * scale)
            }
        }
        font.color = Color.WHITE
        val e = entries.getOrNull(entryIdx)
        val head = if (e != null) "[${entryIdx + 1}/${entries.size}] ${o?.name ?: e.file}" else "LF2 DarkFiren"
        font.draw(batch, head, 12f, 10f)
        if (f != null) {
            font.draw(
                batch,
                "frame ${f.id} ${f.name}  pic ${f.int("pic")}  state ${f.int("state")}  wait ${f.int("wait")}  next ${f.int("next")}" +
                    if (paused) "  [JEDA]" else "",
                12f, 30f
            )
        }
        error?.let {
            font.color = Color.SALMON
            font.draw(batch, it, 12f, 60f, 776f, 1, true)
        }
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "kiri/kanan: ganti karakter   bawah: frame berikutnya   atas: lanjut", 12f, 425f)
        batch.end()

        if (f != null) {
            Gdx.gl.glLineWidth(2f)
            shapes.begin(ShapeRenderer.ShapeType.Line)
            shapes.color = Color.GREEN
            for (b in f.bdy) shapes.rect(ox + b.x * scale, oy + b.y * scale, b.w * scale, b.h * scale)
            shapes.color = Color.RED
            for (b in f.itr) shapes.rect(ox + b.x * scale, oy + b.y * scale, b.w * scale, b.h * scale)
            shapes.end()
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun dispose() {
        textures.forEach { it.dispose() }
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }
}
