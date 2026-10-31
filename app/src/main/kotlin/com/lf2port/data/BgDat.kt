package com.lf2port.data

class BgLayer(
    val file: String, val transparent: Boolean, val width: Int,
    val x: Int, val y: Int, val loop: Int, val isRect: Boolean
)

class BgDat(val name: String, val width: Int, val zMin: Int, val zMax: Int, val layers: List<BgLayer>)

object BgParser {
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

    private fun first(v: String?): Int? = v?.trim()?.split(' ')?.firstOrNull()?.toIntOrNull()

    fun parse(text: String): BgDat {
        var name = ""
        var width = 794
        var z1 = 300
        var z2 = 500
        val layers = ArrayList<BgLayer>()
        var inLayer = false
        var file = ""
        var props = LinkedHashMap<String, String>()

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            when {
                line.startsWith("layer_end") -> {
                    if (inLayer && file.isNotEmpty()) {
                        layers.add(
                            BgLayer(
                                file, props["transparency"]?.trim() == "1",
                                first(props["width"]) ?: 0, first(props["x"]) ?: 0, first(props["y"]) ?: 0,
                                first(props["loop"]) ?: 0, props.containsKey("rect")
                            )
                        )
                    }
                    inLayer = false
                }
                line.startsWith("layer:") -> {
                    inLayer = true
                    file = ""
                    props = LinkedHashMap()
                }
                inLayer -> {
                    if (!line.contains(':')) {
                        if (file.isEmpty()) file = line
                    } else {
                        props.putAll(pairs(line))
                    }
                }
                else -> {
                    val m = pairs(line)
                    m["name"]?.let { name = it }
                    first(m["width"])?.let { width = it }
                    m["zboundary"]?.let {
                        val p = it.trim().split(' ')
                        if (p.size >= 2) {
                            z1 = p[0].toIntOrNull() ?: z1
                            z2 = p[1].toIntOrNull() ?: z2
                        }
                    }
                }
            }
        }
        return BgDat(name, width, z1, z2, layers)
    }
}
