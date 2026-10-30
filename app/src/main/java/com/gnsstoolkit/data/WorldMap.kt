package com.gnsstoolkit.data

import android.content.Context
import com.gnsstoolkit.R
import java.io.DataInputStream

/**
 * 世界陆地轮廓，供昼夜分界图使用。
 *
 * 数据来自 Natural Earth 1:110m land，经 Douglas-Peucker 简化（容差 0.25°）后
 * 量化成 0.01° 的 uint16，压缩进 `res/raw/world_land.bin`（约 11 KB）。
 * 只有一个环列表，湖泊等内环也在其中，绘制时用**偶奇填充**即可挖空。
 */
object WorldMap {

    @Volatile
    private var cache: List<FloatArray>? = null

    /** 每个数组是 [lon, lat, lon, lat, …]，角度制。 */
    fun landRings(context: Context): List<FloatArray> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val rings = read(context)
            cache = rings
            return rings
        }
    }

    private fun read(context: Context): List<FloatArray> {
        val rings = ArrayList<FloatArray>(160)
        context.resources.openRawResource(R.raw.world_land).use { stream ->
            DataInputStream(stream.buffered(16 * 1024)).use { input ->
                val ringCount = readUnsignedShort(input)
                repeat(ringCount) {
                    val pointCount = readUnsignedShort(input)
                    val points = FloatArray(pointCount * 2)
                    for (i in 0 until pointCount) {
                        points[i * 2] = readUnsignedShort(input) / 100f - 180f
                        points[i * 2 + 1] = readUnsignedShort(input) / 100f - 90f
                    }
                    rings.add(points)
                }
            }
        }
        return rings
    }

    /** 小端 uint16。DataInputStream 是大端，所以自己拼。 */
    private fun readUnsignedShort(input: DataInputStream): Int {
        val low = input.read()
        val high = input.read()
        require(high >= 0 && low >= 0) { "world_land.bin 被截断" }
        return low or (high shl 8)
    }
}
