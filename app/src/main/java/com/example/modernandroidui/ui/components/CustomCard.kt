package com.example.modernandroidui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.modernandroidui.R

@Composable
fun CustomCard(
    title: String,
    description: String,
    imageResId: Int?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            imageResId?.let {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier
                        .size(128.dp)
                        .fillMaxWidth(),
                    alignment = Alignment.Center
                )
            } ?: run {
                // Placeholder handling
                Image(
                    painter = painterResource(id = R.drawable.placeholder),
                    contentDescription = null,
                    modifier = Modifier
                        .size(128.dp)
                        .fillMaxWidth(),
                    alignment = Alignment.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}