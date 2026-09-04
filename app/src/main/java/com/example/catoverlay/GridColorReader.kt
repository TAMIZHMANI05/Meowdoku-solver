package com.example.catoverlay

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object GridColorReader {

    /** ids[r][c] plus the detected RGB centroid for each id — handy for debugging. */
    data class ColorBoard(val ids: Array<IntArray>, val palette: List<Triple<Int, Int, Int>>)

    /**
     * Samples the bitmap on a rows x cols grid and returns a board of small
     * integer "color ids" — cells whose sampled color is within [tolerance]
     * of each other get the same id.
     *
     * Two things matter a lot here, since a single misclassified cell can
     * split one real connected color-blob into two "regions" for the
     * solver (which then correctly, but wrongly-from-your-perspective,
     * gives each region its own cat):
     *  1. Sample from well inside the cell (skip [marginFraction] on each
     *     edge) so we never pick up the light gaps/borders between tiles.
     *  2. Use a median color (resistant to a stray anti-aliased pixel)
     *     and match each cell against the *closest* existing cluster,
     *     updating that cluster's centroid as a running mean — a frozen
     *     first-sample centroid drifts away from later same-color cells
     *     and can silently spawn a spurious second cluster.
     */
    fun readColors(
        bitmap: Bitmap,
        rows: Int,
        cols: Int,
        tolerance: Int = 30,
        marginFraction: Double = 0.28
    ): ColorBoard {
        val w = bitmap.width
        val h = bitmap.height
        val cellW = w / cols
        val cellH = h / rows

        // ---- Pass 1: median-sample each cell from its interior only ----
        val cellColor = Array(rows) { arrayOfNulls<Triple<Int, Int, Int>>(cols) }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * cellW
                val y0 = r * cellH
                val marginX = (cellW * marginFraction).toInt()
                val marginY = (cellH * marginFraction).toInt()
                val left = x0 + marginX
                val top = y0 + marginY
                val right = (x0 + cellW - marginX).coerceAtLeast(left + 1)
                val bottom = (y0 + cellH - marginY).coerceAtLeast(top + 1)

                val rs = mutableListOf<Int>()
                val gs = mutableListOf<Int>()
                val bs = mutableListOf<Int>()

                val stepX = maxOf(1, (right - left) / 5)
                val stepY = maxOf(1, (bottom - top) / 5)
                var y = top
                while (y < bottom) {
                    var x = left
                    while (x < right) {
                        val px = x.coerceIn(0, w - 1)
                        val py = y.coerceIn(0, h - 1)
                        val pixel = bitmap.getPixel(px, py)
                        rs.add(Color.red(pixel))
                        gs.add(Color.green(pixel))
                        bs.add(Color.blue(pixel))
                        x += stepX
                    }
                    y += stepY
                }

                rs.sort(); gs.sort(); bs.sort()
                val mid = rs.size / 2
                cellColor[r][c] = Triple(rs[mid], gs[mid], bs[mid])
            }
        }

        // ---- Pass 2: cluster all sampled colors, nearest-match + running mean ----
        class Cluster(var sumR: Long, var sumG: Long, var sumB: Long, var count: Int) {
            fun centroid() = Triple((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
        }

        val clusters = mutableListOf<Cluster>()
        val ids = Array(rows) { IntArray(cols) }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val (cr, cg, cb) = cellColor[r][c]!!

                var bestIdx = -1
                var bestDist = Int.MAX_VALUE
                for ((i, cl) in clusters.withIndex()) {
                    val (pr, pg, pb) = cl.centroid()
                    val dist = abs(pr - cr) + abs(pg - cg) + abs(pb - cb)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = i
                    }
                }

                if (bestIdx != -1 && bestDist <= tolerance * 3) {
                    clusters[bestIdx].sumR += cr
                    clusters[bestIdx].sumG += cg
                    clusters[bestIdx].sumB += cb
                    clusters[bestIdx].count++
                    ids[r][c] = bestIdx
                } else {
                    clusters.add(Cluster(cr.toLong(), cg.toLong(), cb.toLong(), 1))
                    ids[r][c] = clusters.size - 1
                }
            }
        }

        return ColorBoard(ids, clusters.map { it.centroid() })
    }
}
