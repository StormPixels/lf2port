package com.lf2port

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.lf2port.data.AssetIndex
import com.lf2port.data.BgDat
import com.lf2port.data.BgParser
import com.lf2port.data.BmpDecoder
import com.lf2port.data.Lf2Crypto
import com.lf2port.data.ObjectDat
import com.lf2port.data.ObjectParser
import com.lf2port.engine.Fighter
import com.lf2port.engine.Pad
import kotlin.math.hypot
import kotlin.math.min

/**
 * Milestone 2: dunia + satu pemain.
 * Kontrol: D-pad kiri-bawah (ketuk 2x cepat ke kiri/kanan = lari), A = serang, J = lompat, D = tangkis.
 * Lari + J = dash, di udara + A = serangan lompat.
 * Ketuk pojok kiri atas / kanan atas = ganti karakter, ketuk tengah atas = ganti background.
 */
class Lf2Play(private val roots: List<String>) : ApplicationAdapter() {
    private class Entry(val id: Int, val file: String)
    private class Layer(val def: com.lf2port.data.BgLayer, val region: TextureRegion)

    private val viewW = 794f
    private val viewH = 550f

    private lateinit var assets: AssetIndex
    private lateinit var batch: SpriteBatch
    private lateinit var shapes: ShapeRenderer
    private lateinit var font: BitmapFont
    private val cam = OrthographicCamera()
    private val uiCam = OrthographicCamera()
    private lateinit var viewport: FitViewport
    private lateinit var uiViewport: ScreenViewport

    private val chars = ArrayList<Entry>()
    private val bgs = ArrayList<Entry>()
    private var charIdx = 0
    private var bgIdx = 0

    private var obj: ObjectDat? = null
    private var sheets = ArrayList<Texture>()
    private var bg: BgDat? = null
    private var layers = ArrayList<Layer>()
    private var layerTex = ArrayList<Texture>()
    private var fighter: Fighter? = null
    private var error: String? = null

    private val pad = Pad()
    private val taps = ArrayList<IntArray>()
    private var acc = 0f
    private var camX = 0f

    override fun create() {
        cam.setToOrtho(true, viewW, viewH)
        viewport = FitViewport(viewW, viewH, cam)
        uiCam.setToOrtho(true)
        uiViewport = ScreenViewport(uiCam)
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        font = BitmapFont(true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                taps.add(intArrayOf(screenX, screenY))
                return true
            }
        }
        try {
            val dir = roots.map { Gdx.files.absolute(it) }.firstOrNull { it.child("data/data.txt").exists() }
                ?: throw IllegalStateException(
                    "Aset game belum ada. Salin isi folder LF2_v2.0a_darkfiren ke salah satu folder ini " +
                        "(data/data.txt harus ada di dalamnya):\n" + roots.joinToString("\n")
                )
            assets = AssetIndex(dir)
            val text = (assets.resolve("data/data.txt")
                ?: throw IllegalStateException("data/data.txt tidak ditemukan"))
                .readString("ISO-8859-1")
            val objText = text.substringBefore("<background>")
            Regex("""id:\s*(\d+)\s+type:\s*0\s+file:\s*(\S+)""").findAll(objText).forEach {
                chars.add(Entry(it.groupValues[1].toInt(), it.groupValues[2]))
            }
            Regex("""id:\s*(\d+)\s+file:\s*(\S+)""").findAll(text.substringAfter("<background>", "")).forEach {
                bgs.add(Entry(it.groupValues[1].toInt(), it.groupValues[2]))
            }
            if (chars.isEmpty()) throw IllegalStateException("Tidak ada karakter di data.txt")
            val fi = chars.indexOfFirst { it.file.lowercase().contains("firen.dat") }
            charIdx = if (fi >= 0) fi else 0
            val bi = bgs.indexOfFirst { it.id == 1 }
            bgIdx = if (bi >= 0) bi else 0
            loadBg(bgIdx)
            loadChar(charIdx)
        } catch (e: Exception) {
            error = e.toString()
        }
    }

    private fun resolveDat(file: String) =
        assets.resolve(file) ?: assets.resolve("$file.dat")

    private fun loadBg(i: Int) {
        layerTex.forEach { it.dispose() }
        layerTex = ArrayList()
        layers = ArrayList()
        bg = null
        if (bgs.isEmpty()) return
        bgIdx = (i + bgs.size) % bgs.size
        val e = bgs[bgIdx]
        try {
            val fh = resolveDat(e.file) ?: throw IllegalStateException("Background tidak ada: ${e.file}")
            val def = BgParser.parse(Lf2Crypto.readDat(fh.readBytes()))
            for (l in def.layers) {
                if (l.isRect) continue
                try {
                    val bmp = assets.resolve(l.file) ?: continue
                    val pm = BmpDecoder.decode(bmp.readBytes(), l.transparent)
                    val tex = Texture(pm)
                    pm.dispose()
                    layerTex.add(tex)
                    val region = TextureRegion(tex)
                    region.flip(false, true)
                    layers.add(Layer(l, region))
                } catch (_: Exception) {
                    // lewati layer yang gagal dimuat
                }
            }
            bg = def
            error = null
            spawn()
        } catch (ex: Exception) {
            error = "${e.file}: $ex"
        }
    }

    private fun loadChar(i: Int) {
        sheets.forEach { it.dispose() }
        sheets = ArrayList()
        obj = null
        fighter = null
        charIdx = (i + chars.size) % chars.size
        val e = chars[charIdx]
        try {
            val fh = assets.resolve(e.file) ?: throw IllegalStateException("File tidak ditemukan: ${e.file}")
            val o = ObjectParser.parse(Lf2Crypto.readDat(fh.readBytes()))
            for (s in o.sheets) {
                val bmp = assets.resolve(s.path) ?: throw IllegalStateException("Sprite tidak ada: ${s.path}")
                val pm = BmpDecoder.decode(bmp.readBytes())
                val tex = Texture(pm)
                tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                pm.dispose()
                sheets.add(tex)
            }
            obj = o
            error = null
            spawn()
        } catch (ex: Exception) {
            error = "${e.file}: $ex"
        }
    }

    private fun spawn() {
        val o = obj ?: return
        val b = bg ?: return
        fighter = Fighter(o, 200f, (b.zMin + b.zMax) / 2f)
    }

    // ---- kontrol sentuh ----

    private class Layout(val u: Float, val w: Float, val h: Float) {
        val dpadX = 2.6f * u
        val dpadY = h - 2.6f * u
        val dpadR = 1.7f * u
        val aX = w - 5.2f * u
        val aY = h - 2.0f * u
        val jX = w - 3.2f * u
        val jY = h - 3.4f * u
        val dX = w - 1.6f * u
        val dY = h - 1.8f * u
        val br = 1.0f * u
    }

    private fun layout(): Layout {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        return Layout(min(w, h) * 0.11f, w, h)
    }

    private fun readPad(l: Layout) {
        pad.left = false; pad.right = false; pad.up = false; pad.down = false
        pad.a = false; pad.j = false; pad.d = false
        for (i in 0 until 5) {
            if (!Gdx.input.isTouched(i)) continue
            val tx = Gdx.input.getX(i).toFloat()
            val ty = Gdx.input.getY(i).toFloat()
            if (hypot(tx - l.dpadX, ty - l.dpadY) < l.dpadR * 1.5f) {
                val dx = tx - l.dpadX
                val dy = ty - l.dpadY
                val th = l.dpadR * 0.4f
                if (dx < -th) pad.left = true
                if (dx > th) pad.right = true
                if (dy < -th) pad.up = true
                if (dy > th) pad.down = true
            }
            if (hypot(tx - l.aX, ty - l.aY) < l.br * 1.3f) pad.a = true
            if (hypot(tx - l.jX, ty - l.jY) < l.br * 1.3f) pad.j = true
            if (hypot(tx - l.dX, ty - l.dY) < l.br * 1.3f) pad.d = true
        }
    }

    private fun handleTaps() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val copy = ArrayList(taps)
        taps.clear()
        for (t in copy) {
            val x = t[0] / w
            val y = t[1] / h
            if (y < 0.15f) {
                if (x < 0.25f) loadChar(charIdx - 1)
                else if (x > 0.75f) loadChar(charIdx + 1)
                else if (x in 0.4f..0.6f) loadBg(bgIdx + 1)
            }
        }
    }

    // ---- loop utama ----

    override fun render() {
        val l = layout()
        if (chars.isNotEmpty()) handleTaps() else taps.clear()
        readPad(l)

        val f = fighter
        val b = bg
        if (f != null && b != null) {
            acc += Gdx.graphics.deltaTime
            while (acc >= 1f / 30f) {
                acc -= 1f / 30f
                f.tick(pad, b.width, b.zMin, b.zMax)
            }
            camX = (f.x - viewW / 2f).coerceIn(0f, maxOf(0f, b.width - viewW))
        }

        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // dunia
        viewport.apply()
        cam.update()
        batch.projectionMatrix = cam.combined
        shapes.projectionMatrix = cam.combined

        batch.begin()
        if (b != null) drawLayers(b)
        batch.end()

        if (f != null) {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            shapes.color = Color(0f, 0f, 0f, 0.35f)
            shapes.ellipse(f.x - camX - 22f, f.z - 5f, 44f, 10f)
            shapes.end()
        }

        batch.begin()
        val o = obj
        if (f != null) f.draw(batch, sheets, camX)
        font.color = Color.WHITE
        font.data.setScale(1f)
        if (f != null && o != null) {
            font.draw(
                batch,
                "${o.name}  [${charIdx + 1}/${chars.size}]   bg: ${bg?.name ?: "-"}   " +
                    "frame ${f.frame.id} ${f.frame.name}  state ${f.frame.int("state")}",
                10f, 8f
            )
        }
        error?.let {
            font.color = Color.SALMON
            font.draw(batch, it, 16f, 60f, viewW - 32f, Align.left, true)
        }
        batch.end()

        // UI sentuh (piksel layar)
        uiViewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        uiViewport.apply(true)
        uiCam.update()
        shapes.projectionMatrix = uiCam.combined
        batch.projectionMatrix = uiCam.combined
        drawControls(l)
    }

    private fun drawLayers(b: BgDat) {
        val span = b.width - viewW
        for (ly in layers) {
            val d = ly.def
            val tw = ly.region.regionWidth.toFloat()
            val th = ly.region.regionHeight.toFloat()
            val factor = if (span > 0f) ((d.width - viewW) / span).coerceIn(0f, 1f) else 0f
            val ox = d.x - camX * factor
            if (d.loop > 0) {
                val count = (d.width / d.loop.toFloat()).toInt() + 1
                for (k in 0 until count) {
                    val dx = ox + k * d.loop
                    if (dx + tw < 0f || dx > viewW) continue
                    batch.draw(ly.region, dx, d.y.toFloat(), tw, th)
                }
            } else {
                batch.draw(ly.region, ox, d.y.toFloat(), tw, th)
            }
        }
    }

    private fun drawControls(l: Layout) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(1f, 1f, 1f, 0.12f)
        shapes.circle(l.dpadX, l.dpadY, l.dpadR)
        shapes.color = Color(1f, 1f, 1f, if (pad.a) 0.45f else 0.15f)
        shapes.circle(l.aX, l.aY, l.br)
        shapes.color = Color(1f, 1f, 1f, if (pad.j) 0.45f else 0.15f)
        shapes.circle(l.jX, l.jY, l.br)
        shapes.color = Color(1f, 1f, 1f, if (pad.d) 0.45f else 0.15f)
        shapes.circle(l.dX, l.dY, l.br)
        val t = l.dpadR * 0.45f
        shapes.color = Color(1f, 1f, 1f, 0.5f)
        if (pad.left) shapes.circle(l.dpadX - t, l.dpadY, l.u * 0.3f)
        if (pad.right) shapes.circle(l.dpadX + t, l.dpadY, l.u * 0.3f)
        if (pad.up) shapes.circle(l.dpadX, l.dpadY - t, l.u * 0.3f)
        if (pad.down) shapes.circle(l.dpadX, l.dpadY + t, l.u * 0.3f)
        shapes.end()

        batch.begin()
        font.data.setScale(l.u / 28f)
        font.color = Color(1f, 1f, 1f, 0.8f)
        val ty = -font.lineHeight / 2f
        font.draw(batch, "A", l.aX - l.br, l.aY + ty, 2f * l.br, Align.center, false)
        font.draw(batch, "J", l.jX - l.br, l.jY + ty, 2f * l.br, Align.center, false)
        font.draw(batch, "D", l.dX - l.br, l.dY + ty, 2f * l.br, Align.center, false)
        font.data.setScale(1f)
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        uiViewport.update(width, height, true)
    }

    override fun dispose() {
        sheets.forEach { it.dispose() }
        layerTex.forEach { it.dispose() }
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }
}
