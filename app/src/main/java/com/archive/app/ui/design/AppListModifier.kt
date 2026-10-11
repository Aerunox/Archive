package com.archive.app.ui.design

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * List modifier per the Miuix docs: lists use `Modifier.overScrollVertical()`
 * for the HyperOS bounce effect (added before any nestedScroll).
 */
fun Modifier.appListModifier(): Modifier = this.then(Modifier.overScrollVertical())
