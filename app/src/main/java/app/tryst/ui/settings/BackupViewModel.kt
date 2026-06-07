package app.tryst.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.session.SessionManager
import app.tryst.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val backup: BackupManager,
) : ViewModel() {

    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Keep the app unlocked across the system file-picker handoff (same as the photo picker). */
    fun suppressAutoLock() = session.suppressNextAutoLock()

    fun export(uri: Uri, password: String) {
        viewModelScope.launch {
            busy = true
            status = null
            try {
                context.contentResolver.openOutputStream(uri)?.use { backup.export(password, it) }
                    ?: throw IOException("Couldn't open the destination file")
                status = "Encrypted backup saved."
            } catch (e: Exception) {
                status = "Export failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun import(uri: Uri, password: String) {
        viewModelScope.launch {
            busy = true
            status = null
            try {
                context.contentResolver.openInputStream(uri)?.use { backup.import(password, it) }
                    ?: throw IOException("Couldn't open the file")
                status = "Restore complete."
            } catch (e: Exception) {
                status = "Import failed — wrong password or not a Tryst backup."
            } finally {
                busy = false
            }
        }
    }
}
