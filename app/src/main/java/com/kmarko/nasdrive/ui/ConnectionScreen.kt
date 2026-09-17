package com.kmarko.nasdrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kmarko.nasdrive.SmbConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    savedConfig: SmbConfig?,
    loading: Boolean,
    errorMessage: String?,
    onConnect: (SmbConfig) -> Unit
) {
    var host by remember { mutableStateOf(savedConfig?.host ?: "") }
    var port by remember { mutableStateOf((savedConfig?.port ?: 445).toString()) }
    var share by remember { mutableStateOf(savedConfig?.shareName ?: "") }
    var username by remember { mutableStateOf(savedConfig?.username ?: "") }
    var password by remember { mutableStateOf(savedConfig?.password ?: "") }
    var domain by remember { mutableStateOf(savedConfig?.domain ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Connect to NAS") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Enter the NAS's Tailscale address (e.g. 100.x.x.x or nas.your-tailnet.ts.net) " +
                    "and its SMB share name. Make sure Tailscale is connected on this phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host (Tailscale IP or MagicDNS name)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = share,
                onValueChange = { share = it },
                label = { Text("Share name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain / Workgroup (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    onConnect(
                        SmbConfig(
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 445,
                            shareName = share.trim(),
                            username = username.trim(),
                            password = password,
                            domain = domain.trim()
                        )
                    )
                },
                enabled = !loading && host.isNotBlank() && share.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect")
                }
            }
        }
    }
}
