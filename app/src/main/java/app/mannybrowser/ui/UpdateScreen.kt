package app.mannybrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mannybrowser.BrowserState
import app.mannybrowser.R
import app.mannybrowser.update.UpdateState

/** App-update surface behind the menu entry. Every step is user-tapped; install
 *  goes through the system package installer sheet, never silently. */
@Composable
fun UpdateScreen(state: BrowserState) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.update_current, state.installedVersionName, state.installedVersionCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        when (val s = state.updateState) {
            UpdateState.Idle -> updateButton(stringResource(R.string.update_check), "update_check") {
                state.checkForUpdate(silent = false)
            }
            UpdateState.Checking -> updateStatus(stringResource(R.string.update_checking))
            UpdateState.UpToDate -> {
                updateStatus(stringResource(R.string.update_latest))
                updateButton(stringResource(R.string.update_check_again), "update_check") {
                    state.checkForUpdate(silent = false)
                }
            }
            is UpdateState.Available -> {
                updateStatus(stringResource(R.string.update_available, s.info.versionName, s.info.versionCode))
                updateButton(stringResource(R.string.update_download), "update_download") {
                    state.downloadUpdate()
                }
            }
            is UpdateState.Downloading -> updateStatus(stringResource(R.string.update_downloading, s.percent))
            is UpdateState.ReadyToInstall -> {
                updateStatus(stringResource(R.string.update_ready))
                updateButton(stringResource(R.string.update_install), "update_install") {
                    state.installUpdate()
                }
            }
            is UpdateState.Failed -> {
                updateStatus(
                    stringResource(
                        if (s.info == null) R.string.update_failed_check else R.string.update_failed_verify
                    )
                )
                updateButton(stringResource(R.string.update_try_again), "update_check") {
                    state.checkForUpdate(silent = false)
                }
            }
        }
    }
}

@Composable
private fun updateButton(label: String, tag: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp).testTag(tag)) {
        Text(label)
    }
}

@Composable
private fun updateStatus(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}
