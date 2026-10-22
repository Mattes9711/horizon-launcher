package app.horizon.launcher.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.horizon.launcher.R

/** Inter (SIL Open Font License) - clean, SF-like UI typeface. */
val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

val SoftShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f)

/** Current time as the TV formats it (24 h / 12 h), refreshed on the minute. */
@Composable
fun rememberClock(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val format = androidx.compose.runtime.remember { android.text.format.DateFormat.getTimeFormat(context) }
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000 - now % 60_000 + 50)
        }
    }
    return format.format(java.util.Date(now))
}

/** All text in the launcher goes through here, so every label uses Inter. */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    shadow: Shadow? = null,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = Inter,
            shadow = shadow,
            textAlign = align,
            letterSpacing = letterSpacing,
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        softWrap = maxLines > 1,
    )
}
