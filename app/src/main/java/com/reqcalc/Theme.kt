package com.reqcalc

// ─────────────────────────────────────────────────────────────
// Design system: tokens, type, icons and reusable components.
// Nothing outside this file hard-codes a color, radius or size.
// ─────────────────────────────────────────────────────────────

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── 1) Color tokens (the CSS-variable equivalent) ───────────
@Immutable
class Tokens(
    val bg: Color, val card: Color, val tx: Color, val mu: Color, val bd: Color, val input: Color,
    val pr: Color, val pr2: Color, val ok: Color, val er: Color,
)

val LightTokens = Tokens(
    bg = Color(0xFFF4F5F8), card = Color(0xFFFFFFFF), tx = Color(0xFF12151C), mu = Color(0xFF6A7282),
    bd = Color(0xFFE5E8EE), input = Color(0xFFF1F3F8),
    pr = Color(0xFF4F6BFF), pr2 = Color(0xFF7C5CFF), ok = Color(0xFF15803D), er = Color(0xFFDC2626),
)
val DarkTokens = Tokens(
    bg = Color(0xFF0B0D12), card = Color(0xFF141821), tx = Color(0xFFECEEF3), mu = Color(0xFF9AA3B5),
    bd = Color(0xFF262C3A), input = Color(0xFF1B2030),
    pr = Color(0xFF6580FF), pr2 = Color(0xFF8E74FF), ok = Color(0xFF34D399), er = Color(0xFFF87171),
)
val LocalTokens = staticCompositionLocalOf { LightTokens }
@Composable @ReadOnlyComposable fun tk(): Tokens = LocalTokens.current

// ── 2) Spacing, radii, type ─────────────────────────────────
object Sp { val s = 6.dp; val m = 10.dp; val l = 14.dp; val xl = 20.dp; val page = 16.dp }
object Rd {
    val card = RoundedCornerShape(20.dp); val btn = RoundedCornerShape(16.dp)
    val input = RoundedCornerShape(13.dp); val tile = RoundedCornerShape(13.dp)
    val small = RoundedCornerShape(11.dp); val pill = RoundedCornerShape(50)
}
val MaxWidth = 520.dp

/** Two weights only: Normal and Bold. Line height 1.65 everywhere. */
object Type {
    private fun st(size: Float, w: FontWeight) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = w, lineHeight = (size * 1.65f).sp)
    val page = st(18.4f, FontWeight.Bold)
    val card = st(16.8f, FontWeight.Bold)
    val body = st(16f, FontWeight.Normal)
    val bodyB = st(16f, FontWeight.Bold)
    val small = st(13.6f, FontWeight.Normal)
    val smallB = st(13.6f, FontWeight.Bold)
    val badge = st(12f, FontWeight.Bold)
    val tiny = st(12f, FontWeight.Normal)
    val big = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
}

@Composable
fun Txt(
    text: String, style: TextStyle = Type.body, color: Color = tk().tx, modifier: Modifier = Modifier,
    align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE,
) = Text(text, modifier, color = color, style = style, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Keeps digit equations like "21 + 0 = 21" in reading order inside RTL text. */
fun ltr(s: String) = "‪$s‬"

// ── 5) Icons: one consistent set, stroke 1.8, round caps, currentColor ──
private fun icon(vararg d: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        d.forEach {
            addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object Ic {
    private const val CIRCLE = "M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18z"
    val Upload = icon("M12 15V3", "M7 8l5-5 5 5", "M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4")
    val Download = icon("M12 3v12", "M7 10l5 5 5-5", "M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4")
    val Copy = icon("M9 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z", "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1")
    val Check = icon("M5 12.5l4.5 4.5L19 7.5")
    val CheckCircle = icon(CIRCLE, "M8 12.5l2.8 2.8L16 9.5")
    val Search = icon("M11 4a7 7 0 1 0 0 14a7 7 0 0 0 0-14z", "M16 16l4 4")
    val Back = icon("M19 12H5", "M11 6l-6 6 6 6")
    val Chevron = icon("M9 6l6 6-6 6")
    val Globe = icon(CIRCLE, "M3 12h18", "M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9z")
    val Edit = icon("M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4z", "M13.5 6.5l4 4")
    val Alert = icon("M12 3L2.5 20h19L12 3z", "M12 10v4", "M12 17.2v.01")
    val Info = icon(CIRCLE, "M12 11v5", "M12 8v.01")
    val Ban = icon(CIRCLE, "M5.6 5.6l12.8 12.8")
    val File = icon("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5z", "M14 3v5h5", "M9 13h6", "M9 17h4")
    val Calc = icon("M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8 7.5h8", "M8.5 12h.01", "M12 12h.01", "M15.5 12h.01", "M8.5 16h.01", "M12 16h.01", "M15.5 16h.01")
}

@Composable
fun Glyph(icon: ImageVector, tint: Color = tk().mu, size: Dp = 20.dp, mirror: Boolean = false, modifier: Modifier = Modifier) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Icon(icon, null, modifier.size(size).scale(if (mirror && rtl) -1f else 1f, 1f), tint = tint)
}

// ── Page scaffolding ────────────────────────────────────────
/** Background: --bg with a faint brand radial gradient in both top corners; one centered column. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    val t = tk()
    Box(
        Modifier.fillMaxSize().background(t.bg).drawBehind {
            val r = size.width * 0.9f
            drawRect(Brush.radialGradient(listOf(t.pr.copy(alpha = 0.14f), Color.Transparent), Offset(0f, 0f), r))
            drawRect(Brush.radialGradient(listOf(t.pr2.copy(alpha = 0.12f), Color.Transparent), Offset(size.width, 0f), r))
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = MaxWidth).fillMaxSize().systemBarsPadding().padding(horizontal = Sp.page),
            content = content,
        )
    }
}

// ── 4) Components ───────────────────────────────────────────
@Composable
fun AppCard(modifier: Modifier = Modifier, pad: Dp = Sp.page, content: @Composable ColumnScope.() -> Unit) {
    val t = tk()
    Column(
        modifier.fillMaxWidth()
            .shadow(3.dp, Rd.card, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(t.card, Rd.card).border(1.dp, t.bd, Rd.card).padding(pad),
        content = content,
    )
}

@Composable
fun Tile(icon: ImageVector, tint: Color = tk().pr, mirror: Boolean = false) {
    Box(Modifier.size(46.dp).clip(Rd.tile).background(tint.copy(alpha = 0.12f)), Alignment.Center) { Glyph(icon, tint, mirror = mirror) }
}

@Composable
fun Logo() {
    val t = tk()
    Box(
        Modifier.size(44.dp)
            .shadow(8.dp, Rd.tile, ambientColor = t.pr.copy(alpha = 0.35f), spotColor = t.pr.copy(alpha = 0.35f))
            .clip(Rd.tile).background(Brush.linearGradient(listOf(t.pr, t.pr2))),
        Alignment.Center,
    ) { Glyph(Ic.Calc, Color.White, 22.dp) }
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false,
) {
    val t = tk()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.98f else 1f, tween(150), label = "press")
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.5f }
            .shadow(if (enabled) 8.dp else 0.dp, Rd.btn, ambientColor = t.pr.copy(alpha = 0.35f), spotColor = t.pr.copy(alpha = 0.35f))
            .clip(Rd.btn).background(Brush.linearGradient(listOf(t.pr, t.pr2)))
            .clickable(src, null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = Sp.xl, vertical = Sp.l),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else if (icon != null) Glyph(icon, Color.White)
            Txt(text, Type.bodyB, Color.White)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val t = tk()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.98f else 1f, tween(150), label = "press")
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.5f }
            .clip(Rd.btn).border(1.dp, t.bd, Rd.btn)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Sp.xl, vertical = Sp.l),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
            if (icon != null) Glyph(icon, t.tx)
            Txt(text, Type.bodyB)
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = tk().mu, mirror: Boolean = false) {
    val t = tk()
    Box(
        Modifier.size(40.dp).clip(Rd.small).border(1.dp, t.bd, Rd.small)
            .semantics { contentDescription = desc }
            .clickable(role = Role.Button, onClick = onClick),
        Alignment.Center,
    ) { Crossfade(icon, animationSpec = tween(150), label = "icon") { Glyph(it, tint, mirror = mirror) } }
}

/** Copy button: morphs into a check for ~1.5s, then reverts. */
@Composable
fun CopyBtn(value: String, desc: String) {
    val clip = LocalClipboardManager.current
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) { delay(1500); done = false } }
    IconBtn(if (done) Ic.Check else Ic.Copy, desc, { clip.setText(AnnotatedString(value)); done = true }, if (done) tk().ok else tk().mu)
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    leading: ImageVector? = null, keyboard: KeyboardType = KeyboardType.Text,
) {
    val t = tk()
    var focus by remember { mutableStateOf(false) }
    BasicTextField(
        value, onChange, modifier.fillMaxWidth().onFocusChanged { focus = it.isFocused },
        singleLine = true, textStyle = Type.body.copy(color = t.tx), cursorBrush = SolidColor(t.pr),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .drawBehind {
                        if (focus) {
                            val o = 2.dp.toPx()
                            drawRoundRect(t.pr.copy(alpha = 0.2f), Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), CornerRadius(15.dp.toPx()), style = Stroke(4.dp.toPx()))
                        }
                    }
                    .background(t.input, Rd.input).border(1.dp, if (focus) t.pr else t.bd, Rd.input)
                    .padding(horizontal = Sp.l, vertical = Sp.m),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.m),
            ) {
                if (leading != null) Glyph(leading, t.mu, 18.dp)
                Box(Modifier.weight(1f), Alignment.CenterStart) {
                    if (value.isEmpty()) Txt(placeholder, Type.body, t.mu, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(Rd.pill).background(color.copy(alpha = 0.12f)).padding(horizontal = Sp.m, vertical = 2.dp)) {
        Txt(text, Type.badge, color, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = tk()
    val c = if (selected) t.pr else t.mu
    Box(
        Modifier.heightIn(min = 40.dp).clip(Rd.pill)
            .background(if (selected) t.pr.copy(alpha = 0.12f) else Color.Transparent, Rd.pill)
            .border(1.dp, if (selected) t.pr else t.bd, Rd.pill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Sp.xl, vertical = Sp.s),
        Alignment.Center,
    ) { Txt(text, Type.smallB, c, maxLines = 1) }
}

enum class Kind { RUN, OK, ERR }

/** Status banner: icon + title + description, in three states. Tinted 12% with a same-hue border. */
@Composable
fun Banner(kind: Kind, title: String, desc: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val t = tk()
    val c = when (kind) { Kind.RUN -> t.pr; Kind.OK -> t.ok; Kind.ERR -> t.er }
    Column(
        modifier.fillMaxWidth().background(c.copy(alpha = 0.12f), Rd.btn).border(1.dp, c.copy(alpha = 0.4f), Rd.btn).padding(Sp.l),
        verticalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.l), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(24.dp), Alignment.Center) {
                when (kind) {
                    Kind.RUN -> CircularProgressIndicator(Modifier.size(20.dp), color = c, strokeWidth = 2.dp)
                    Kind.OK -> Glyph(Ic.CheckCircle, c)
                    Kind.ERR -> Glyph(Ic.Alert, c)
                }
            }
            Column(Modifier.weight(1f)) {
                Txt(title, Type.bodyB, t.tx)
                Txt(desc, Type.small, t.mu)
            }
        }
        action?.invoke()
    }
}

/** Bottom sheet for secondary choices. Dismisses on scrim tap; the caller also wires the back button. */
@Composable
fun BoxScope.Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = tk()
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    }
    AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(250)) { it }, exit = slideOutVertically(tween(200)) { it },
    ) {
        val top = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        Column(
            Modifier.widthIn(max = MaxWidth).fillMaxWidth()
                .background(t.card, top).border(1.dp, t.bd, top)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(Sp.page),
            verticalArrangement = Arrangement.spacedBy(Sp.l),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).background(t.bd, Rd.pill))
            content()
        }
    }
}

/** Short confirmation at the bottom of the screen; hides itself after 2 seconds. */
@Composable
fun BoxScope.Toast(message: String?, onDone: () -> Unit) {
    val t = tk()
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    LaunchedEffect(message) { if (message != null) { delay(2000); onDone() } }
    AnimatedVisibility(
        message != null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Sp.xl),
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }, exit = fadeOut(tween(200)),
    ) {
        Box(Modifier.shadow(6.dp, Rd.pill).background(t.tx, Rd.pill).padding(horizontal = Sp.xl, vertical = Sp.m)) {
            Txt(last, Type.smallB, t.bg)
        }
    }
}
