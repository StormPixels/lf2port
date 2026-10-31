package com.lf2port.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle

/**
 * Indeks aset tanpa membedakan huruf besar/kecil (Android case-sensitive,
 * sedangkan path di file .dat memakai backslash dan huruf campuran).
 */
class AssetIndex(private val root: String) {
    private val map = HashMap<String, FileHandle>()

    init {
        walk(Gdx.files.internal(root))
    }

    private fun walk(dir: FileHandle) {
        for (f in dir.list()) {
            if (f.isDirectory) walk(f) else map[f.path().replace('\\', '/').lowercase()] = f
        }
    }

    val size: Int get() = map.size

    fun resolve(path: String): FileHandle? =
        map["$root/${path.replace('\\', '/')}".lowercase()]
}
