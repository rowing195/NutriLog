package com.watson.nutrilog.ui

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watson.nutrilog.R

@Composable
fun PhotoConfirmationDialog(uri: Uri, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by rememberSaveable(uri.toString()) { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.photo_confirm_title),
                    closeLabel = stringResource(R.string.cancel),
                    onClose = onDismiss,
                )
            },
        ) { inner ->
            Column(
                Modifier.fillMaxSize().padding(inner).imePadding()
                    .dismissKeyboardOnTap()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    },
                    update = { it.setImageURI(uri) },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                NutriTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.photo_note_label),
                    placeholder = stringResource(R.string.photo_note_hint),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                StampButton(
                    label = stringResource(R.string.photo_confirm_send),
                    onClick = { onConfirm(note) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
