package com.example.catoverlay

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object GridColorReader {

    /**
     * Samples the bitmap on a rows x cols grid and returns a board of small
     * integer "color ids" — cells whose average color is within [tolerance]
     * of each other get the same id. This id is exactly the `color` value
     * the C++ solver keys regions off of; it doesn't need to mean anything
     * beyond "same id == same paint color".
     */
    fun readColors(bitmap: Bitmap, rows: Int, cols: Int, tolerance: Int = 30): Array<IntArray> {
        val w = bitmap.width
        val h = bitmap.height
        val cellW = w / cols
        val cellH = h / rows

        val palette = mutableListOf<Triple<Int, Int, Int>>()
        val ids = Array(rows) { IntArray(cols) }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = c * cellW + cellW / 2
                val cy = r * cellH + cellH / 2
                val rad = maxOf(1, minOf(cellW, cellH) / 6)

                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var count = 0
                var dx = -rad
                while (dx <= rad) {
                    var dy = -rad
                    while (dy <= rad) {
                        val px = (cx + dx).coerceIn(0, w - 1)
                        val py = (cy + dy).coerceIn(0, h - 1)
                        val pixel = bitmap.getPixel(px, py)
                        sumR += Color.red(pixel)
                        sumG += Color.green(pixel)
                        sumB += Color.blue(pixel)
                        count++
                        dy += maxOf(1, rad / 2)
                    }
                    dx += maxOf(1, rad / 2)
                }

                val avgR = (sumR / count).toInt()
                val avgG = (sumG / count).toInt()
                val avgB = (sumB / count).toInt()

                var matched = -1
                for ((i, p) in palette.withIndex()) {
                    val dist = abs(p.first - avgR) + abs(p.second - avgG) + abs(p.third - avgB)
                    if (dist <= tolerance * 3) {
                        matched = i
                        break
                    }
                }
                if (matched == -1) {
                    palette.add(Triple(avgR, avgG, avgB))
                    matched = palette.size - 1
                }
                ids[r][c] = matched
            }
        }
        return ids
    }
}
