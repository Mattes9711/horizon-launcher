package app.horizon.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Simple line icons drawn in code (no icon fonts or third-party artwork). */
enum class Glyph { POWER, MOON, SPEAKER, SPARKLE, INPUT, WIFI, GEAR, EYE, CARDS, NOTE, AIRPLAY, PHOTO, HOME, GRID, HDMI }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 26.dp) {
    // offscreen so the 'Clear' cut-outs (moon, gear) only cut the icon itself
    Canvas(Modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) { draw(glyph, color) }
}

private fun DrawScope.draw(g: Glyph, c: Color) {
    val w = size.width
    val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
    val mid = Offset(w / 2, w / 2)
    when (g) {
        Glyph.POWER -> {
            drawArc(c, -60f, 300f, false, Offset(w * 0.18f, w * 0.2f), Size(w * 0.64f, w * 0.64f), style = s)
            drawLine(c, Offset(w / 2, w * 0.08f), Offset(w / 2, w * 0.45f), s.width, StrokeCap.Round)
        }
        Glyph.MOON -> {
            drawCircle(c, w * 0.34f, mid)
            drawCircle(Color.Transparent, w * 0.3f, Offset(w * 0.68f, w * 0.34f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        }
        Glyph.SPEAKER -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.12f, w * 0.38f); lineTo(w * 0.3f, w * 0.38f); lineTo(w * 0.52f, w * 0.18f)
                lineTo(w * 0.52f, w * 0.82f); lineTo(w * 0.3f, w * 0.62f); lineTo(w * 0.12f, w * 0.62f); close()
            }
            drawPath(p, c)
            drawArc(c, -50f, 100f, false, Offset(w * 0.42f, w * 0.3f), Size(w * 0.4f, w * 0.4f), style = s)
            drawArc(c, -50f, 100f, false, Offset(w * 0.36f, w * 0.16f), Size(w * 0.68f, w * 0.68f), style = s)
        }
        Glyph.SPARKLE -> {
            fun star(cx: Float, cy: Float, r: Float) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy - r); quadraticBezierTo(cx, cy, cx + r, cy); quadraticBezierTo(cx, cy, cx, cy + r)
                    quadraticBezierTo(cx, cy, cx - r, cy); quadraticBezierTo(cx, cy, cx, cy - r); close()
                }
                drawPath(p, c)
            }
            star(w * 0.42f, w * 0.55f, w * 0.34f)
            star(w * 0.78f, w * 0.22f, w * 0.16f)
        }
        Glyph.INPUT -> {
            drawRoundRect(c, Offset(w * 0.1f, w * 0.18f), Size(w * 0.8f, w * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = s)
            drawLine(c, Offset(w * 0.32f, w * 0.86f), Offset(w * 0.68f, w * 0.86f), s.width, StrokeCap.Round)
        }
        Glyph.WIFI -> {
            for (i in 0..2) {
                val r = w * (0.16f + i * 0.17f)
                drawArc(c, -135f, 90f, false, Offset(w / 2 - r, w * 0.8f - r), Size(r * 2, r * 2), style = s)
            }
            drawCircle(c, w * 0.07f, Offset(w / 2, w * 0.8f))
        }
        Glyph.GEAR -> {
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val o = Offset(w / 2 + (w * 0.36f * Math.cos(a)).toFloat(), w / 2 + (w * 0.36f * Math.sin(a)).toFloat())
                drawLine(c, mid, o, w * 0.16f, StrokeCap.Round)
            }
            drawCircle(c, w * 0.3f, mid)
            drawCircle(Color.Transparent, w * 0.12f, mid, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        }
        Glyph.EYE -> {
            drawArc(c, 200f, 140f, false, Offset(w * 0.06f, w * 0.22f), Size(w * 0.88f, w * 0.8f), style = s)
            drawArc(c, 20f, 140f, false, Offset(w * 0.06f, w * -0.02f), Size(w * 0.88f, w * 0.8f), style = s)
            drawCircle(c, w * 0.12f, mid)
        }
        Glyph.CARDS -> {
            drawRoundRect(c, Offset(w * 0.04f, w * 0.3f), Size(w * 0.26f, w * 0.4f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f), alpha = 0.5f)
            drawRoundRect(c, Offset(w * 0.7f, w * 0.3f), Size(w * 0.26f, w * 0.4f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f), alpha = 0.5f)
            drawRoundRect(c, Offset(w * 0.24f, w * 0.2f), Size(w * 0.52f, w * 0.6f), androidx.compose.ui.geometry.CornerRadius(w * 0.07f))
        }
        Glyph.AIRPLAY -> {
            // screen outline open at the bottom, with a triangle rising into it
            val r = androidx.compose.ui.geometry.CornerRadius(w * 0.08f)
            drawRoundRect(c, Offset(w * 0.08f, w * 0.12f), Size(w * 0.84f, w * 0.56f), r, style = s)
            val tri = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, w * 0.5f); lineTo(w * 0.74f, w * 0.9f); lineTo(w * 0.26f, w * 0.9f); close()
            }
            drawRect(Color.Transparent, Offset(w * 0.24f, w * 0.6f), Size(w * 0.52f, w * 0.16f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            drawPath(tri, c)
        }
        Glyph.PHOTO -> {
            val r = androidx.compose.ui.geometry.CornerRadius(w * 0.08f)
            drawRoundRect(c, Offset(w * 0.08f, w * 0.18f), Size(w * 0.84f, w * 0.64f), r, style = s)
            val hill = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.16f, w * 0.76f); lineTo(w * 0.4f, w * 0.46f); lineTo(w * 0.56f, w * 0.64f)
                lineTo(w * 0.66f, w * 0.54f); lineTo(w * 0.84f, w * 0.76f); close()
            }
            drawPath(hill, c)
            drawCircle(c, w * 0.07f, Offset(w * 0.7f, w * 0.34f))
        }
        Glyph.HOME -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.12f, w * 0.46f); lineTo(w * 0.5f, w * 0.16f); lineTo(w * 0.88f, w * 0.46f)
                moveTo(w * 0.22f, w * 0.40f); lineTo(w * 0.22f, w * 0.84f); lineTo(w * 0.78f, w * 0.84f); lineTo(w * 0.78f, w * 0.40f)
            }
            drawPath(p, c, style = Stroke(width = s.width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        Glyph.GRID -> {
            val r = androidx.compose.ui.geometry.CornerRadius(w * 0.07f)
            for (x in listOf(0.14f, 0.56f)) for (y in listOf(0.14f, 0.56f)) {
                drawRoundRect(c, Offset(w * x, w * y), Size(w * 0.3f, w * 0.3f), r, style = s)
            }
        }
        Glyph.HDMI -> {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.1f, w * 0.36f); lineTo(w * 0.9f, w * 0.36f); lineTo(w * 0.9f, w * 0.54f)
                lineTo(w * 0.8f, w * 0.68f); lineTo(w * 0.2f, w * 0.68f); lineTo(w * 0.1f, w * 0.54f); close()
            }
            drawPath(p, c, style = Stroke(width = s.width, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        Glyph.NOTE -> {
            drawCircle(c, w * 0.13f, Offset(w * 0.3f, w * 0.74f))
            drawCircle(c, w * 0.13f, Offset(w * 0.74f, w * 0.64f))
            drawLine(c, Offset(w * 0.41f, w * 0.74f), Offset(w * 0.41f, w * 0.2f), s.width, StrokeCap.Round)
            drawLine(c, Offset(w * 0.85f, w * 0.64f), Offset(w * 0.85f, w * 0.1f), s.width, StrokeCap.Round)
            drawLine(c, Offset(w * 0.41f, w * 0.2f), Offset(w * 0.85f, w * 0.1f), s.width, StrokeCap.Round)
        }
    }
}
