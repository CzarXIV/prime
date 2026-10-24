package dev.prime.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object P {
    val Black = Color(0xFF000000)
    val Line = Color(0xFF262626)
    val Ghost = Color(0xFF4A4A4A)
    val Accent = Color(0xFFFF3B1F)
    val AccentDeep = Color(0xFF6B180B)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8C8C8C)
}

@Composable
fun PrimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = P.Accent,
            onPrimary = Color.Black,
            background = P.Black,
            onBackground = P.Text,
            surface = P.Black,
            onSurface = P.Text,
        ),
        content = content,
    )
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(P.Line))

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = P.Muted) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

@Composable
fun Headline(text: String, size: TextUnit = 32.sp, color: Color = P.Text, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Black,
        lineHeight = size * 1.05f,
        letterSpacing = (-0.5).sp,
    )
}

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !filled -> Color.Transparent
        enabled -> P.Accent
        else -> P.Line
    }
    val fg = when {
        filled && enabled -> Color.Black
        !filled && enabled -> P.Text
        else -> P.Muted
    }
    val border = if (!filled) Modifier.border(1.5.dp, if (enabled) P.Text else P.Line) else Modifier
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(bg)
            .then(border)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = fg, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp)
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = P.Text, fontSize = size, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(P.Accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 10.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = P.Ghost, fontSize = size, fontWeight = FontWeight.Bold)
                    }
                    inner()
                }
                Box(
                    Modifier.fillMaxWidth().height(2.dp)
                        .background(if (value.isEmpty()) P.Line else P.Accent)
                )
            }
        },
    )
}

@Composable
fun CheckSquare(checked: Boolean) {
    Box(
        Modifier.size(24.dp)
            .then(if (checked) Modifier.background(P.Accent) else Modifier.border(2.dp, P.Ghost)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
fun SquareBtn(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).border(1.5.dp, P.Line).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(t, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        SquareBtn("−", onMinus)
        Text(
            value,
            color = P.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp),
        )
        SquareBtn("+", onPlus)
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = P.Muted, fontSize = 13.sp)
        }
        HGap(12)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = P.Accent,
                checkedBorderColor = P.Accent,
                uncheckedThumbColor = P.Muted,
                uncheckedTrackColor = P.Black,
                uncheckedBorderColor = P.Line,
            ),
        )
    }
}
