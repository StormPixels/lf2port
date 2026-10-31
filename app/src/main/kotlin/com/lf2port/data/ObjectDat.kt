package com.lf2port.data

class SpriteFile(
    val first: Int, val last: Int, val path: String,
    val w: Int, val h: Int, val perRow: Int, val rows: Int
)

/** Satu blok bdy / itr / wpoint / bpoint / opoint / cpoint. */
class Box(val props: Map<String, String>) {
    fun int(k: String, d: Int = 0): Int =
        props[k]?.trim()?.split(' ')?.firstOrNull()?.toIntOrNull() ?: d
    val kind get() = int("kind")
    val x get() = int("x")
    val y get() = int("y")
    val w get() = int("w")
    val h get() = int("h")
}

class Frame(
    val id: Int,
    val name: String,
    val props: Map<String, String>,
    val blocks: Map<String, List<Box>>
) {
    fun int(k: String, d: Int = 0): Int =
        props[k]?.trim()?.split(' ')?.firstOrNull()?.toIntOrNull() ?: d
    val bdy: List<Box> get() = blocks["bdy"] ?: emptyList()
    val itr: List<Box> get() = blocks["itr"] ?: emptyList()
}

class ObjectDat(
    val name: String,
    val head: String,
    val small: String,
    val sheets: List<SpriteFile>,
    val params: Map<String, String>,
    val frames: Map<Int, Frame>
)

object ObjectParser {

    /** Pecah baris "key: nilai  key2: nilai2" menjadi pasangan. */
    private fun pairs(line: String): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        var key: String? = null
        val sb = StringBuilder()
        for (t in line.trim().split(Regex("\\s+"))) {
            if (t.length > 1 && t.endsWith(":")) {
                if (key != null) m[key] = sb.toString().trim()
                sb.setLength(0)
                key = t.dropLast(1)
            } else {
                sb.append(t).append(' ')
            }
        }
        if (key != null) m[key] = sb.toString().trim()
        return m
    }

    private val fileKey = Regex("""file\((\d+)-(\d+)\)""")

    fun parse(text: String): ObjectDat {
        var name = ""
        var head = ""
        var small = ""
        val sheets = ArrayList<SpriteFile>()
        val params = LinkedHashMap<String, String>()
        val frames = LinkedHashMap<Int, Frame>()

        var section = 0 // 0 = di luar, 1 = header bmp, 2 = frame
        var fId = 0
        var fName = ""
        var fProps = LinkedHashMap<String, String>()
        var blocks = LinkedHashMap<String, MutableList<Box>>()
        var curBlock: String? = null
        var curProps = LinkedHashMap<String, String>()

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("<bmp_begin>") -> section = 1
                line.startsWith("<bmp_end>") -> section = 0
                line.startsWith("<frame>") -> {
                    section = 2
                    val p = line.removePrefix("<frame>").trim().split(Regex("\\s+"), 2)
                    fId = p[0].toIntOrNull() ?: 0
                    fName = p.getOrElse(1) { "" }
                    fProps = LinkedHashMap()
                    blocks = LinkedHashMap()
                    curBlock = null
                }
                line.startsWith("<frame_end>") -> {
                    frames[fId] = Frame(fId, fName, fProps, blocks)
                    section = 0
                }
                section == 1 -> {
                    if (!line.contains(':')) {
                        val p = line.split(Regex("\\s+"), 2)
                        if (p.size == 2) params[p[0]] = p[1].trim()
                    } else {
                        val m = pairs(line)
                        var handled = false
                        for ((k, v) in m) {
                            val fm = fileKey.matchEntire(k)
                            if (fm != null) {
                                sheets.add(
                                    SpriteFile(
                                        fm.groupValues[1].toInt(), fm.groupValues[2].toInt(), v.substringBefore(' '),
                                        m["w"]?.toIntOrNull() ?: 0, m["h"]?.toIntOrNull() ?: 0,
                                        m["row"]?.toIntOrNull() ?: 1, m["col"]?.toIntOrNull() ?: 1
                                    )
                                )
                                handled = true
                            }
                        }
                        if (!handled) {
                            m["name"]?.let { name = it }
                            m["head"]?.let { head = it }
                            m["small"]?.let { small = it }
                        }
                    }
                }
                section == 2 -> {
                    if (line.endsWith("_end:")) {
                        val b = curBlock
                        if (b != null) blocks.getOrPut(b) { ArrayList() }.add(Box(curProps))
                        curBlock = null
                    } else if (line.endsWith(":") && !line.contains(' ')) {
                        curBlock = line.dropLast(1)
                        curProps = LinkedHashMap()
                    } else {
                        val m = pairs(line)
                        if (curBlock != null) curProps.putAll(m) else fProps.putAll(m)
                    }
                }
            }
        }
        return ObjectDat(name, head, small, sheets, params, frames)
    }
}
