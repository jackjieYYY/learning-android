package com.jack.englishlearning.ui.pixel

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.ui.theme.ArtPixel
import com.jack.englishlearning.ui.theme.PixelTheme
import kotlin.math.roundToInt

enum class PixelButtonKind { Primary, Secondary }

@Composable
private fun buttonColors(kind: PixelButtonKind, enabled: Boolean): Pair<BlockColors, Color> {
    val palette = PixelTheme.palette
    return when {
        !enabled -> BlockColors(palette.divider, palette.divider, palette.panelShade, palette.outline) to palette.textMuted
        kind == PixelButtonKind.Primary -> palette.accentColors() to palette.onAccent
        else -> palette.panelColors() to palette.text
    }
}

@Composable
fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                kind: PixelButtonKind = PixelButtonKind.Primary, icon: PixelGlyph? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (colors, content) = buttonColors(kind, enabled)
    Box(modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
        .pixelBlock(colors, pressed = pressed, seed = text.hashCode())
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Row(Modifier.offset(y = if (pressed) ArtPixel else 0.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.let { PixelIcon(it, content, size = 18.dp) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PixelIconButton(glyph: PixelGlyph, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                    kind: PixelButtonKind = PixelButtonKind.Secondary, enabled: Boolean = true,
                    size: Dp = 44.dp, iconSize: Dp = 22.dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (colors, content) = buttonColors(kind, enabled)
    Box(modifier.size(size)
        .pixelBlock(colors, pressed = pressed, seed = glyph.name.hashCode())
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center) {
        PixelIcon(glyph, content, Modifier.offset(y = if (pressed) ArtPixel else 0.dp), size = iconSize)
    }
}

/** A bevelled block container; with [onClick] the whole panel presses in like a button. */
@Composable
fun PixelPanel(modifier: Modifier = Modifier, colors: BlockColors = PixelTheme.palette.panelColors(),
               textured: Boolean = true, contentPadding: PaddingValues = PaddingValues(16.dp), seed: Int = 1,
               onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(modifier
        .pixelBlock(colors, textured = textured, pressed = pressed, seed = seed)
        .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
        .padding(contentPadding), content = content)
}

/** Full-width textured top bar that extends under the status bar. */
@Composable
fun PixelTopBar(title: String, modifier: Modifier = Modifier, navigation: (@Composable () -> Unit)? = null,
                compact: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val palette = PixelTheme.palette
    Row(modifier.fillMaxWidth()
        .drawWithCache {
            val p = artPixelPx()
            val paint = Paint().apply {
                isAntiAlias = false
                isFilterBitmap = false
                shader = BitmapShader(noiseTile(palette.panel, 3), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    .apply { setLocalMatrix(Matrix().apply { setScale(p, p) }) }
            }
            onDrawBehind {
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
                drawRect(palette.panelShade, Offset(0f, size.height - 2 * p), Size(size.width, p))
                drawRect(palette.outline, Offset(0f, size.height - p), Size(size.width, p))
            }
        }
        .statusBarsPadding()
        .padding(start = 8.dp, end = 8.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp)
        .heightIn(min = if (compact) 24.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        navigation?.invoke()
        Text(title, style = if (compact) MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 20.sp)
            else MaterialTheme.typography.titleLarge, color = palette.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (navigation == null) 8.dp else 0.dp))
        actions()
    }
}

@Composable
fun PixelDivider(modifier: Modifier = Modifier, color: Color = PixelTheme.palette.divider, height: Dp = 6.dp) {
    Spacer(modifier.fillMaxWidth().height(height).pixelRule(color))
}

/** Pixel-font section heading; hierarchy comes from size and the space above it. */
@Composable
fun PixelSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = PixelTheme.palette.text,
                      style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(text, modifier, style = style, color = color)
}

/** Segmented progress bar; `null` progress shows a stepped indeterminate marker. */
@Composable
fun PixelProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val palette = PixelTheme.palette
    val track = BlockColors(palette.panelShade, palette.panelShade, palette.panelShade, palette.outline)
    val phase = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "pixel-progress")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "pixel-progress-phase").value
    } else 0f
    Canvas(modifier.fillMaxWidth().height(14.dp)
        .pixelBlock(track, textured = false, bevel = false)
        .semantics {
            if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
        }) {
        val p = artPixelPx()
        val inner = size.width - 4 * p
        val height = size.height - 4 * p
        val segment = 3 * p
        val count = (inner / segment).toInt().coerceAtLeast(1)
        val (from, to) = if (progress == null) {
            val step = (phase * (count + 6)).toInt() - 6
            step to step + 6
        } else 0 to (progress.coerceIn(0f, 1f) * count).roundToInt()
        for (index in maxOf(from, 0) until minOf(to, count)) {
            drawRect(palette.accent, Offset(2 * p + index * segment, 2 * p), Size(segment - p, height))
        }
    }
}

/** Crack stages (1–9) for the original "mining a block" download indicator. */
private val crackRows = listOf(
    "................",
    "..........7.....",
    "...8.......6....",
    "....6.....5..9..",
    ".....5...4......",
    "......4.3...8...",
    ".9.....32.......",
    "..7.5..21.......",
    ".......12..4....",
    "......3..3..5...",
    ".....4....4.....",
    "....5..8...5....",
    "...6........6...",
    "..7..........7..",
    ".8............9.",
    "................",
)

/** A clay block that cracks a little more as the download progresses. */
@Composable
fun MiningBlock(progress: Float?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val palette = PixelTheme.palette
    val stage = progress?.let { (it.coerceIn(0f, 1f) * 9).toInt() } ?: 0
    Canvas(modifier.size(size).semantics { contentDescription = "下载进度" }) {
        val cell = this.size.width / 16f
        val extra = 0.5f
        for (y in 0 until 16) for (x in 0 until 16) {
            val hash = (x * 73 + y * 151 + x * y * 7) % 11
            val color = when {
                x == 0 || y == 0 || x == 15 || y == 15 -> palette.outline
                x == 1 || y == 1 -> palette.accentLight
                x == 14 || y == 14 -> palette.accentShade
                hash == 0 -> lerp(palette.accent, Color.White, 0.12f)
                hash == 1 || hash == 2 -> lerp(palette.accent, Color.Black, 0.08f)
                else -> palette.accent
            }
            drawRect(color, Offset(x * cell, y * cell), Size(cell + extra, cell + extra))
            val crack = crackRows[y].getOrNull(x)?.digitToIntOrNull()
            if (crack != null && crack <= stage) {
                drawRect(palette.outline, Offset(x * cell, y * cell), Size(cell + extra, cell + extra))
            }
        }
    }
}

/** Square pixel radio row used by settings. */
@Composable
fun PixelRadioRow(text: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PixelTheme.palette
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(24.dp).pixelBlock(palette.paperColors(), textured = false, pressed = true),
            contentAlignment = Alignment.Center) {
            if (selected) Spacer(Modifier.size(12.dp).pixelBlock(palette.accentColors(), textured = false, bevel = false))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = palette.text)
    }
}
