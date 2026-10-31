package com.lf2port

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import java.io.File

class MainActivity : AndroidApplication() {

    /** Folder data game: Android/data/<paket>/files/lf2 dan Android/media/<paket>/lf2. */
    private fun gameRoots(): List<String> {
        val list = ArrayList<String>()
        getExternalFilesDir(null)?.let { list.add(File(it, "lf2").absolutePath) }
        externalMediaDirs.firstOrNull()?.let { list.add(File(it, "lf2").absolutePath) }
        list.forEach { File(it).mkdirs() } // buat folder agar mudah ditemukan
        return list
    }

    /** Log crash disimpan di folder data eksternal agar bisa dibuka lewat file manager. */
    private val crashFile: File get() = File(getExternalFilesDir(null) ?: filesDir, "crash.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simpan stack trace saat crash (termasuk dari thread GL) supaya bisa dibaca di launch berikutnya.
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                crashFile.writeText(Log.getStackTraceString(e))
            } catch (_: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }

        val previous = if (crashFile.exists()) crashFile.readText() else null
        if (previous != null) showCrash(previous) else startGame()
    }

    private fun startGame() {
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
        }
        try {
            initialize(Lf2Game(gameRoots()), config)
        } catch (t: Throwable) {
            val trace = Log.getStackTraceString(t)
            try {
                crashFile.writeText(trace)
            } catch (_: Throwable) {
            }
            showCrash(trace)
        }
    }

    private fun showCrash(trace: String) {
        val tv = TextView(this).apply {
            text = trace
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        val button = Button(this).apply {
            text = "Hapus log dan coba lagi"
            setOnClickListener {
                crashFile.delete()
                recreate()
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                button,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            addView(
                ScrollView(this@MainActivity).apply { addView(tv) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        setContentView(root)
    }
}
