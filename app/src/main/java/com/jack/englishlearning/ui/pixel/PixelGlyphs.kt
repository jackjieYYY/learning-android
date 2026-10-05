package com.jack.englishlearning.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Original 16×16 pixel icon; '#' is an inked pixel, anything else is transparent. */
class PixelGlyph(val name: String, vararg rows: String) {
    val rows: List<String> = rows.toList()
    /** Horizontal runs (row, startColumn, length) so each run draws as one rectangle without seams. */
    internal val runs: List<Triple<Int, Int, Int>> = buildList {
        this@PixelGlyph.rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                if (row[x] != '#') { x++; continue }
                val start = x
                while (x < row.length && row[x] == '#') x++
                add(Triple(y, start, x - start))
            }
        }
    }
}

object PixelGlyphs {
    val Play = PixelGlyph("play",
        "................",
        "................",
        "...##...........",
        "...####.........",
        "...######.......",
        "...########.....",
        "...##########...",
        "...############.",
        "...############.",
        "...##########...",
        "...########.....",
        "...######.......",
        "...####.........",
        "...##...........",
        "................",
        "................")
    val Pause = PixelGlyph("pause",
        "................",
        "................",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "...####..####...",
        "................",
        "................")
    val Download = PixelGlyph("download",
        "................",
        ".......##.......",
        ".......##.......",
        ".......##.......",
        ".......##.......",
        ".......##.......",
        "...##..##..##...",
        "....##.##.##....",
        ".....######.....",
        "......####......",
        ".......##.......",
        "................",
        ".##..........##.",
        ".##..........##.",
        ".##############.",
        ".##############.")
    val Refresh = PixelGlyph("refresh",
        "................",
        "....########....",
        "...##########...",
        "..###......###..",
        "..##........##..",
        "..##........##..",
        "..##......######",
        "..##.......####.",
        "..##........##..",
        "..##............",
        "..##............",
        "..###...........",
        "...#########....",
        "....########....",
        "................",
        "................")
    val Folder = PixelGlyph("folder",
        "................",
        "................",
        ".#####..........",
        ".#######........",
        ".##############.",
        ".##############.",
        ".##..........##.",
        ".##############.",
        ".##############.",
        ".##############.",
        ".##############.",
        ".##############.",
        ".##############.",
        "................",
        "................",
        "................")
    val Gear = PixelGlyph("gear",
        "................",
        "......####......",
        "..##..####..##..",
        "..############..",
        "...##########...",
        "..####....####..",
        "#####......#####",
        "####........####",
        "####........####",
        "#####......#####",
        "..####....####..",
        "...##########...",
        "..############..",
        "..##..####..##..",
        "......####......",
        "................")
    val Back = PixelGlyph("back",
        "................",
        "................",
        "......##........",
        ".....###........",
        "....###.........",
        "...###..........",
        "..##############",
        "..##############",
        "...###..........",
        "....###.........",
        ".....###........",
        "......##........",
        "................",
        "................",
        "................",
        "................")
    val Close = PixelGlyph("close",
        "................",
        "................",
        "..##........##..",
        "..###......###..",
        "...###....###...",
        "....###..###....",
        ".....######.....",
        "......####......",
        "......####......",
        ".....######.....",
        "....###..###....",
        "...###....###...",
        "..###......###..",
        "..##........##..",
        "................",
        "................")
    val Book = PixelGlyph("book",
        "................",
        "................",
        ".######..######.",
        ".#....#..#....#.",
        ".#.##.#..#.##.#.",
        ".#....#..#....#.",
        ".#.##.#..#.##.#.",
        ".#....#..#....#.",
        ".#.##.#..#.##.#.",
        ".#....#..#....#.",
        ".######..######.",
        "......####......",
        "................",
        "................",
        "................",
        "................")
    val PictureInPicture = PixelGlyph("pip",
        "................",
        "................",
        ".##############.",
        ".#............#.",
        ".#............#.",
        ".#............#.",
        ".#............#.",
        ".#.....######.#.",
        ".#.....######.#.",
        ".#.....######.#.",
        ".#.....######.#.",
        ".#............#.",
        ".##############.",
        "................",
        "................",
        "................")
    val Check = PixelGlyph("check",
        "................",
        "................",
        "................",
        "................",
        "............##..",
        "...........###..",
        "..........###...",
        ".##......###....",
        ".###....###.....",
        "..###..###......",
        "...######.......",
        "....####........",
        ".....##.........",
        "................",
        "................",
        "................")

    val all = listOf(Play, Pause, Download, Refresh, Folder, Gear, Back, Close, Book, PictureInPicture, Check)
}

@Composable
fun PixelIcon(glyph: PixelGlyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp,
              contentDescription: String? = null) {
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        val cell = this.size.width / 16f
        // Overdraw by a fraction of a pixel so neighbouring runs never show hairline gaps.
        val extra = ceil(cell) - cell + 0.5f
        glyph.runs.forEach { (y, x, length) ->
            drawRect(tint, Offset(x * cell, y * cell), Size(length * cell + extra, cell + extra))
        }
    }
}
