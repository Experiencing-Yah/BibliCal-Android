package com.experiencingyah.bibliCal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

sealed class PassagesRequest {
    data class Week(val index: Int, val name: String) : PassagesRequest()
    data class ThirteenthMonth(val has13: Boolean) : PassagesRequest()
    data class Error(val message: String) : PassagesRequest()
}

@Composable
fun PassagesIntegrationScreen(
    request: PassagesRequest,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "PassAges Integration",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        when (request) {
            is PassagesRequest.Week -> {
                Text(
                    "Current parsha week",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${request.name} (Week ${request.index})",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "This week index follows the PassAges 1–54 ordering.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is PassagesRequest.ThirteenthMonth -> {
                Text(
                    "Current Hebrew year",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (request.has13) "Has a 13th month (leap year)" else "Does not have a 13th month",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "PassAges uses this to determine 54 vs 50 weeks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is PassagesRequest.Error -> {
                Text(
                    "Unable to prepare data",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    request.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (request !is PassagesRequest.Error) {
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Text("Send to PassAges", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Back to BibliCal", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
