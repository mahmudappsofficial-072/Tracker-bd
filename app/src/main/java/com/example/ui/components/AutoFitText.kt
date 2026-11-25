package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Flicker-free single-line text that automatically shrinks from [maxFontSize] down to
 * [minFontSize] when constrained horizontally, preventing ellipses truncation and unwanted
 * multi-line wrapping across all supported languages and screen widths/font scales.
 *
 * Uses [Text] directly (no SubcomposeLayout), making it safe inside IntrinsicSize rows/columns,
 * buttons, chips, and navigation bars.
 */
@Composable
fun AutoFitSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxFontSize: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 14.sp,
    minFontSize: TextUnit = 8.sp,
    stepGranularity: Float = 0.5f,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    val resolvedMaxSp = if (maxFontSize.isSpecified) maxFontSize.value else 14f
    val resolvedMinSp = minFontSize.value.coerceIn(6f, resolvedMaxSp)
    val resolvedLetterSpacing = if (letterSpacing.isSpecified) letterSpacing else style.letterSpacing
    val resolvedWeight = fontWeight ?: style.fontWeight

    val baseStyle = remember(style, resolvedWeight, resolvedLetterSpacing) {
        style.copy(
            fontWeight = resolvedWeight,
            letterSpacing = resolvedLetterSpacing,
            lineHeight = TextUnit.Unspecified
        )
    }

    val textMeasurer = rememberTextMeasurer()
    var currentFontSize by remember(text, resolvedMaxSp, resolvedMinSp, baseStyle) {
        mutableStateOf(resolvedMaxSp.sp)
    }
    var readyToDraw by remember(text, resolvedMaxSp, resolvedMinSp, baseStyle) {
        mutableStateOf(false)
    }

    Text(
        text = text,
        color = color,
        style = baseStyle,
        fontSize = currentFontSize,
        fontWeight = resolvedWeight,
        letterSpacing = resolvedLetterSpacing,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            val didOverflow = result.didOverflowWidth || result.hasVisualOverflow
            if (didOverflow && result.size.width > 0 && currentFontSize.value > resolvedMinSp + 0.05f) {
                val targetWidthPx = result.size.width
                var candidate = (currentFontSize.value - stepGranularity).coerceAtLeast(resolvedMinSp)
                while (candidate > resolvedMinSp) {
                    val measured = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = baseStyle.copy(fontSize = candidate.sp),
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        maxLines = 1
                    )
                    if (measured.size.width <= targetWidthPx) {
                        break
                    }
                    candidate = (candidate - stepGranularity).coerceAtLeast(resolvedMinSp)
                }
                if (candidate < currentFontSize.value - 0.01f) {
                    currentFontSize = candidate.sp
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        },
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        }
    )
}
