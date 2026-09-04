package com.example.catoverlay

/**
 * Direct port of the user's C++ solver:
 *  - board cells carry a "color id" (colors[r][c])
 *  - regions = 4-connected same-color components
 *  - one cat per row, one per column, one per region
 *  - cats placed on consecutive rows cannot be horizontally adjacent
 *    (|colDiff| <= 1) — matches the original `valid()` check.
 */
object CatSolver {

    /** Returns catCol[row] -> column, or null if no solution exists. */
    fun solve(colors: Array<IntArray>): IntArray? {
        val n = colors.size
        require(n > 0 && colors.all { it.size == n }) { "Board must be NxN" }

        // ---- Build regions (BFS flood fill, same logic as buildRegions()) ----
        val region = Array(n) { IntArray(n) { -1 } }
        var regionCount = 0
        val dr = intArrayOf(1, -1, 0, 0)
        val dc = intArrayOf(0, 0, 1, -1)

        for (r in 0 until n) {
            for (c in 0 until n) {
                if (region[r][c] != -1) continue
                val color = colors[r][c]
                val queue = ArrayDeque<IntArray>()
                queue.add(intArrayOf(r, c))
                region[r][c] = regionCount

                while (queue.isNotEmpty()) {
                    val (x, y) = queue.removeFirst()
                    for (k in 0 until 4) {
                        val nx = x + dr[k]
                        val ny = y + dc[k]
                        if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue
                        if (region[nx][ny] != -1) continue
                        if (colors[nx][ny] != color) continue
                        region[nx][ny] = regionCount
                        queue.add(intArrayOf(nx, ny))
                    }
                }
                regionCount++
            }
        }

        val usedCol = BooleanArray(n)
        val usedRegion = BooleanArray(regionCount)
        val catCol = IntArray(n) { -1 }

        fun valid(r: Int, c: Int): Boolean {
            if (usedCol[c]) return false
            val reg = region[r][c]
            if (usedRegion[reg]) return false
            // Only the immediately preceding row can ever be adjacent,
            // same optimization as the original C++.
            if (r > 0) {
                val pc = catCol[r - 1]
                if (pc != -1 && kotlin.math.abs(pc - c) <= 1) return false
            }
            return true
        }

        fun solveRow(row: Int): Boolean {
            if (row == n) return true
            for (c in 0 until n) {
                if (!valid(row, c)) continue
                val reg = region[row][c]
                catCol[row] = c
                usedCol[c] = true
                usedRegion[reg] = true
                if (solveRow(row + 1)) return true
                catCol[row] = -1
                usedCol[c] = false
                usedRegion[reg] = false
            }
            return false
        }

        return if (solveRow(0)) catCol.copyOf() else null
    }
}
