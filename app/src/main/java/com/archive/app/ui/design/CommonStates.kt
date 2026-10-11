package com.archive.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.archive.app.ui.theme.LocalPurrColors

/** Theme-neutral states (loading / empty / error) shared by every screen. */

@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalPurrColors.current.primary)
    }
}

@Composable
fun AppLoadingItem(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalPurrColors.current.primary)
    }
}

@Composable
fun AppEmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(message, style = AppTextStyle.SECONDARY, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(message, style = AppTextStyle.SECONDARY, textAlign = TextAlign.Center)
        if (onRetry != null) {
            AppButton("重试", onRetry, Modifier.padding(top = 16.dp), primary = false)
        }
    }
}

/** Simple segmented tab used by screens that switch between two lists. */
@Composable
fun CommonTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) colors.primary.copy(alpha = 0.14f)
                else colors.surfaceContainer.copy(alpha = 0.7f),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            style = AppTextStyle.SMALL,
            color = if (selected) colors.primary else colors.textSecondary,
        )
    }
}

