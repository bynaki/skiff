package com.naki.skiff.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.naki.skiff.R
import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile

/**
 * Add or edit one server. The start directory lives here rather than being rediscovered
 * each session, so a pane switched to this server lands where the user works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    existing: ServerProfile?,
    onSave: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var startPath by remember { mutableStateOf(existing?.startPath ?: ".") }
    var password by remember {
        mutableStateOf(
            (existing?.auth as? AuthMethod.Password)?.encryptedPassword
                ?.let(SecretStore::decrypt)
                .orEmpty(),
        )
    }
    var passwordVisible by remember { mutableStateOf(false) }

    val portNumber = port.toIntOrNull()
    val hostError = if (host.isBlank()) stringResource(R.string.error_host_empty) else null
    val userError = if (username.isBlank()) stringResource(R.string.error_username_empty) else null
    val portError = if (portNumber == null || portNumber !in 1..65535) {
        stringResource(R.string.error_port_invalid)
    } else {
        null
    }
    val valid = hostError == null && userError == null && portError == null

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_cancel))
                    }
                },
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.server_new_title else R.string.server_edit_title,
                        ),
                    )
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { onDelete(existing) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete_server))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(name, { name = it }, stringResource(R.string.server_name))
            Field(host, { host = it }, stringResource(R.string.server_host), error = hostError)
            Field(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = stringResource(R.string.server_port),
                error = portError,
                keyboardType = KeyboardType.Number,
            )
            Field(username, { username = it }, stringResource(R.string.server_username), error = userError)

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.server_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Field(startPath, { startPath = it }, stringResource(R.string.server_start_path))
            Text(
                stringResource(R.string.server_start_path_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = valid,
                    onClick = {
                        onSave(
                            (existing ?: ServerProfile(name = "", host = "", username = "")).copy(
                                name = name.ifBlank { host },
                                host = host.trim(),
                                port = portNumber ?: 22,
                                username = username.trim(),
                                startPath = startPath.ifBlank { "." },
                                // Re-encrypt on every save: the plaintext never lands on disk.
                                auth = AuthMethod.Password(
                                    password.takeIf { it.isNotEmpty() }?.let(SecretStore::encrypt),
                                ),
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
