package com.jack.englishlearning.ui.pixel

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.jack.englishlearning.ui.theme.ArtPixel
import com.jack.englishlearning.ui.theme.PixelPalette
import java.util.Random
import kotlin.math.roundToInt

/** Colours for one bevelled block: face, top-left highlight, bottom-right shadow and outline. */
data class BlockColors(val fill: Color, val light: Color, val shade: Color, val outline: Color)

fun PixelPalette.panelColors() = BlockColors(panel, panelLight, panelShade, outline)
fun PixelPalette.accentColors() = BlockColors(accent, accentLight, accentShade, outline)
fun PixelPalette.paperColors() = BlockColors(background, background, divider, outline)

private const val NOISE_SIZE = 24
private val noiseCache = HashMap<Pair<Int, Int>, Bitmap>()

private fun shifted(color: Color, amount: Float): Color =
    if (amount >= 0) lerp(color, Color.White, amount) else lerp(color, Color.Black, -amount)

/** Deterministic, subtle stone-like noise tile in the face colour; scaled up without filtering. */
internal fun noiseTile(base: Color, seed: Int): Bitmap = synchronized(noiseCache) {
    noiseCache.getOrPut(base.toArgb() to seed) {
        val random = Random(seed * 7919L + 17)
        val pixels = IntArray(NOISE_SIZE * NOISE_SIZE) {
            val roll = random.nextInt(100)
            val amount = when {
                roll < 12 -> 0.06f
                roll < 26 -> -0.05f
                roll < 30 -> -0.10f
                else -> 0f
            }
            shifted(base, amount).toArgb()
        }
        Bitmap.createBitmap(pixels, NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888)
    }
}

/** One art pixel rounded to whole device pixels so block edges stay crisp. */
internal fun Density.artPixelPx(): Float = ArtPixel.toPx().roundToInt().coerceAtLeast(1).toFloat()

/**
 * Draws a Minecraft-like bevelled block behind the content: 1-art-pixel outline with cut corners,
 * light top/left and dark bottom/right edges, and an optional pixel-noise texture on the face.
 * Pressed blocks swap the bevel so they look pushed in.
 */
fun Modifier.pixelBlock(colors: BlockColors, textured: Boolean = true, pressed: Boolean = false,
                        seed: Int = 1, bevel: Boolean = true): Modifier = drawWithCache {
    val p = artPixelPx()
    val paint = if (textured) Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        shader = BitmapShader(noiseTile(colors.fill, seed), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(p, p); postTranslate(p, p) })
        }
    } else null
    onDrawBehind {
        val w = size.width
        val h = size.height
        if (w < 3 * p || h < 3 * p) return@onDrawBehind
        // Outline as four bars leaves the four corner pixels empty: the pixel "cut corner".
        drawRect(colors.outline, Offset(p, 0f), Size(w - 2 * p, p))
        drawRect(colors.outline, Offset(p, h - p), Size(w - 2 * p, p))
        drawRect(colors.outline, Offset(0f, p), Size(p, h - 2 * p))
        drawRect(colors.outline, Offset(w - p, p), Size(p, h - 2 * p))
        if (paint != null) drawIntoCanvas { it.nativeCanvas.drawRect(p, p, w - p, h - p, paint) }
        else drawRect(colors.fill, Offset(p, p), Size(w - 2 * p, h - 2 * p))
        if (bevel) {
            val top = if (pressed) colors.shade else colors.light
            val bottom = if (pressed) colors.light else colors.shade
            drawRect(top, Offset(p, p), Size(w - 2 * p, p))
            drawRect(top, Offset(p, p), Size(p, h - 2 * p))
            drawRect(bottom, Offset(p, h - 2 * p), Size(w - 2 * p, p))
            drawRect(bottom, Offset(w - 2 * p, 2 * p), Size(p, h - 3 * p))
        }
    }
}

/** A dotted 1-art-pixel rule. */
fun Modifier.pixelRule(color: Color): Modifier = drawWithCache {
    val p = artPixelPx()
    onDrawBehind {
        var x = 0f
        val y = (size.height - p) / 2
        while (x < size.width) {
            drawRect(color, Offset(x, y), Size(minOf(p * 2, size.width - x), p))
            x += p * 3
        }
    }
}
