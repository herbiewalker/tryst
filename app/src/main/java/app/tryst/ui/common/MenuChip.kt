package app.tryst.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A [FilterChip] that opens a dropdown of [menuItems]; the lambda receives a `dismiss` callback so an
 * item can choose whether picking it closes the menu (single-select) or leaves it open (multi-select).
 *
 * Shared by Search's filter chips and the Insights time scope.
 */
@Composable
fun MenuChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    contentDescription: String? = null,
    menuItems: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            enabled = enabled,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = contentDescription) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuItems { expanded = false }
        }
    }
}

/** A dropdown row that shows a leading check when it's the current selection. */
@Composable
fun CheckableItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { if (checked) Icon(Icons.Filled.Check, contentDescription = null) },
    )
}
