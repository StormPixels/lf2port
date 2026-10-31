package com.lf2port.data

import com.badlogic.gdx.files.FileHandle

/**
 * Indeks aset di folder luar APK, tanpa membedakan huruf besar/kecil
 * (Android case-sensitive, sedangkan path di file .dat memakai backslash dan huruf campuran).
 */
class AssetIndex(root: FileHandle) {
    private val map = HashMap<String, FileHandle>()

    init {
        walk(root, "")
    }

    private fun walk(dir: FileHandle, prefix: String) {
        for (f in dir.list()) {
            val key = prefix + f.name()
            if (f.isDirectory) walk(f, "$key/") else map[key.lowercase()] = f
        }
    }

    val size: Int get() = map.size

    fun resolve(path: String): FileHandle? = map[path.replace('\\', '/').lowercase()]
}
