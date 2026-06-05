package app.tryst.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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

    Column(
        modifier = modifier
            .fillMaxSize()
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
        PinDots(filled = pin.length)
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
                    pin += digit
                    if (pin.length == PIN_LENGTH) {
                        val entered = pin
                        pin = ""
                        onPinComplete(entered)
                    }
                }
            },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        )
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { index ->
            val color =
                if (index < filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .size(16.dp)
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
            KeyButton("⌫", enabled, Modifier.weight(1f), filled = false, onClick = onBackspace)
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .aspectRatio(1.4f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (filled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
