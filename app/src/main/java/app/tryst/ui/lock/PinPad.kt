package app.tryst.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tryst.R
import app.tryst.ui.common.rememberHaptics
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PIN_LENGTH = 6

/**
 * Self-contained numeric PIN entry: title/subtitle, a row of dots, optional error text, and a
 * 0–9 keypad with backspace. Calls [onPinComplete] once [PIN_LENGTH] digits are entered, then
 * clears its buffer so the caller can react (advance, show error, etc.).
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String?,
    error: String?,
    enabled: Boolean,
    onPinComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }
    val haptics = rememberHaptics()

    // Shake the dots horizontally whenever a new error arrives, and buzz a rejection.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            haptics.reject()
            shake.snapTo(0f)
            shake.animateTo(1f, tween(400))
        }
    }
    val shakeOffset = (sin(shake.value * 6 * Math.PI) * 10 * (1f - shake.value)).roundToInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            // Setup/Lock render straight into the edge-to-edge window (no Scaffold), so inset the
            // keypad off the status and navigation bars ourselves.
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(32.dp))
        PinDots(filled = pin.length, modifier = Modifier.offset { IntOffset(shakeOffset, 0) })
        Spacer(Modifier.height(16.dp))

        Text(
            text = error ?: " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        Keypad(
            enabled = enabled,
            onDigit = { digit ->
                if (pin.length < PIN_LENGTH) {
                    haptics.tick()
                    pin += digit
                    if (pin.length == PIN_LENGTH) {
                        val entered = pin
                        pin = ""
                        onPinComplete(entered)
                    }
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) {
                    haptics.tick()
                    pin = pin.dropLast(1)
                }
            },
        )
    }
}

@Composable
private fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    // The dots are decorative; expose the entry progress (not the digits) to TalkBack.
    val progressDesc = stringResource(R.string.cd_pin_progress, filled, PIN_LENGTH)
    Row(
        modifier.semantics { contentDescription = progressDesc },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < filled
            val color by animateColorAsState(
                if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(150),
                label = "pinDotColor",
            )
            // The newest dot springs up slightly bigger as it fills.
            val dotSize by animateDpAsState(
                if (isFilled) 18.dp else 16.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pinDotSize",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { ch -> KeyButton(ch.toString(), enabled, Modifier.weight(1f)) { onDigit(ch) } }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.weight(1f))
            KeyButton("0", enabled, Modifier.weight(1f)) { onDigit('0') }
            KeyButton("⌫", enabled, Modifier.weight(1f), filled = false, contentDescription = stringResource(R.string.cd_pin_delete), onClick = onBackspace)
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    filled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // A quick squish on press gives the key tactile weight, on top of the Surface ripple.
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "keyScale")
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .aspectRatio(1.4f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        color = if (filled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
