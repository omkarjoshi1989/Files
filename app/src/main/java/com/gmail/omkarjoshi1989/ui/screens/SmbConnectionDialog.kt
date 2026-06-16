package com.gmail.omkarjoshi1989.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gmail.omkarjoshi1989.model.LanHostCandidate
import com.gmail.omkarjoshi1989.model.SmbAuthMode
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.util.LanDiscoveryManager
import kotlinx.coroutines.launch

@Composable
fun SmbConnectionDialog(
	initialValue: SmbConnectionConfig?,
	onDismiss: () -> Unit,
	onSave: (SmbConnectionConfig) -> Unit,
	onDelete: ((SmbConnectionConfig) -> Unit)? = null
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var displayName by remember { mutableStateOf(initialValue?.displayName ?: "") }
	var host by remember { mutableStateOf(initialValue?.host ?: "") }
	var portText by remember { mutableStateOf((initialValue?.port ?: 445).toString()) }
	var shareName by remember { mutableStateOf(initialValue?.defaultShareName ?: "") }
	var authMode by remember { mutableStateOf(initialValue?.authMode ?: SmbAuthMode.GUEST) }
	var username by remember { mutableStateOf(initialValue?.username ?: "") }
	var password by remember { mutableStateOf(initialValue?.password ?: "") }
	var domain by remember { mutableStateOf(initialValue?.domain ?: "") }
	var discovering by remember { mutableStateOf(false) }
	var hosts by remember { mutableStateOf<List<LanHostCandidate>>(emptyList()) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(if (initialValue == null) "Add LAN/SMB Connection" else "Edit LAN/SMB Connection") },
		text = {
			LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				item {
					OutlinedTextField(
						value = displayName,
						onValueChange = { displayName = it },
						label = { Text("Connection name") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}
				item {
					OutlinedTextField(
						value = host,
						onValueChange = { host = it },
						label = { Text("Laptop IP or hostname") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}
				item {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
						OutlinedTextField(
							value = portText,
							onValueChange = { portText = it.filter(Char::isDigit) },
							label = { Text("Port") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedTextField(
							value = shareName,
							onValueChange = { shareName = it },
							label = { Text("Default share (optional)") },
							singleLine = true,
							modifier = Modifier.weight(2f)
						)
					}
				}
				item {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = authMode == SmbAuthMode.GUEST,
							onClick = { authMode = SmbAuthMode.GUEST },
							label = { Text("Guest") }
						)
						FilterChip(
							selected = authMode == SmbAuthMode.USERNAME_PASSWORD,
							onClick = { authMode = SmbAuthMode.USERNAME_PASSWORD },
							label = { Text("Username/Password") }
						)
					}
				}
				if (authMode == SmbAuthMode.USERNAME_PASSWORD) {
					item {
						OutlinedTextField(
							value = username,
							onValueChange = { username = it },
							label = { Text("Username") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					}
					item {
						OutlinedTextField(
							value = password,
							onValueChange = { password = it },
							label = { Text("Password") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					}
					item {
						OutlinedTextField(
							value = domain,
							onValueChange = { domain = it },
							label = { Text("Domain (optional)") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
				item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
				item {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable(enabled = !discovering) {
								scope.launch {
									discovering = true
									hosts = runCatching { LanDiscoveryManager.discoverNearbyHosts(context) }
										.getOrDefault(emptyList())
									discovering = false
								}
							}
							.padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(Icons.Filled.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
						Spacer(modifier = Modifier.width(8.dp))
						Text("Scan nearby machines", fontWeight = FontWeight.Medium)
						Spacer(modifier = Modifier.weight(1f))
						if (discovering) CircularProgressIndicator(modifier = Modifier.size(20.dp))
					}
				}
				items(hosts, key = { it.ipAddress }) { candidate ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable {
								host = candidate.ipAddress
								if (displayName.isBlank()) displayName = candidate.hostName
							}
							.padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
						Spacer(modifier = Modifier.width(8.dp))
						Column {
							Text(candidate.hostName, fontWeight = FontWeight.Medium)
							Text(candidate.ipAddress, style = MaterialTheme.typography.bodySmall)
						}
					}
					HorizontalDivider()
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					onSave(
						SmbConnectionConfig(
							id = initialValue?.id ?: "",
							displayName = displayName.ifBlank { host },
							host = host.trim(),
							port = portText.toIntOrNull() ?: 445,
							defaultShareName = shareName.trim(),
							authMode = authMode,
							username = username,
							password = password,
							domain = domain,
							lastConnectedAt = initialValue?.lastConnectedAt ?: 0L
						)
					)
				},
				enabled = host.isNotBlank()
			) { Text(if (initialValue == null) "Add" else "Save") }
		},
		dismissButton = {
			Row {
				if (initialValue != null && onDelete != null) {
					TextButton(onClick = { onDelete(initialValue) }) { Text("Delete") }
				}
				TextButton(onClick = onDismiss) { Text("Cancel") }
			}
		}
	)
}

