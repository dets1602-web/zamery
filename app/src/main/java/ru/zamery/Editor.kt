package ru.zamery

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    store: Store,
    original: Entry?,
    history: List<Entry>,
    onClose: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(original?.date ?: LocalDate.now()) }
    val fields = remember {
        mutableStateMapOf<Metric, String>().apply {
            Metric.entries.forEach { put(it, original?.value(it)?.let(::fmt) ?: "") }
        }
    }
    val photos = remember { mutableStateListOf<String>().apply { original?.photos?.let { addAll(it) } } }
    val added = remember { mutableSetOf<String>() }      // фото, добавленные в этот раз
    var busy by remember { mutableStateOf(false) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    fun cancel() {
        added.forEach { store.photoFile(it).delete() }
        onClose()
    }
    BackHandler { cancel() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingCamera
        pendingCamera = null
        if (name != null) {
            val f = store.photoFile(name)
            if (ok && f.length() > 0) {
                scope.launch {
                    busy = true
                    normalizePhoto(f)
                    photos.add(name); added.add(name)
                    busy = false
                }
            } else f.delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            uris.forEachIndexed { i, u ->
                val name = "p_${System.currentTimeMillis()}_$i.jpg"
                if (storePhoto(context, u, store.photoFile(name))) { photos.add(name); added.add(name) }
            }
            busy = false
        }
    }

    fun takePhoto() {
        val name = "p_${System.currentTimeMillis()}.jpg"
        val f = store.photoFile(name)
        f.createNewFile()
        val uri = FileProvider.getUriForFile(context, "ru.zamery.app.files", f)
        pendingCamera = name
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCamera = null; f.delete()
            Toast.makeText(context, "Не нашлось приложения камеры", Toast.LENGTH_LONG).show()
        }
    }

    val parsed = Metric.entries.associateWith { parseNum(fields[it] ?: "") }
    val bad = Metric.entries.filter { (fields[it] ?: "").isNotBlank() && parsed[it] == null }
    val hasSomething = parsed.values.any { it != null } || photos.isNotEmpty()
    val canSave = hasSomething && bad.isEmpty() && !busy

    Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (original == null) "Новый замер" else "Замер",
                color = t.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { cancel() }, modifier = Modifier.height(48.dp)) {
                Text("Отмена", fontSize = 16.sp, color = t.primary)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { showDate = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Дата: ", color = t.muted, fontSize = 16.sp)
                Text(dateLong(date), color = t.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("Изменить", color = t.primary, fontSize = 15.sp)
            }

            Metric.entries.forEachIndexed { i, m ->
                val prev = history.lastOrNull { it.value(m) != null }?.value(m)
                OutlinedTextField(
                    value = fields[m] ?: "",
                    onValueChange = { v -> fields[m] = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text("${m.title}, ${m.unit}", fontSize = 16.sp) },
                    placeholder = if (prev != null) ({ Text("было ${fmt(prev)}", fontSize = 16.sp) }) else null,
                    isError = m in bad,
                    supportingText = if (m in bad) ({ Text("Число, например 82,4") }) else null,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = if (i == 0) 24.sp else 18.sp, color = t.text,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (i == Metric.entries.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = t.primary, focusedLabelColor = t.primary, cursorColor = t.primary,
                        unfocusedContainerColor = t.surface, focusedContainerColor = t.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text("Фото", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            if (photos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    photos.toList().forEach { name ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PhotoImage(store.photoFile(name), Modifier.width(96.dp).height(128.dp).clip(RoundedCornerShape(10.dp)), maxPx = 300)
                            TextButton(onClick = {
                                photos.remove(name)
                                if (name in added) { store.photoFile(name).delete(); added.remove(name) }
                            }, modifier = Modifier.height(48.dp)) { Text("Убрать", color = t.danger, fontSize = 15.sp) }
                        }
                    }
                }
            }
            if (busy) Text("Обрабатываю фото…", color = t.muted, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { takePhoto() }, enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp),
                ) { Text("Сфотографировать", fontSize = 16.sp, color = t.primary) }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp),
                ) { Text("Из галереи", fontSize = 16.sp, color = t.primary) }
            }

            if (original != null) {
                TextButton(onClick = { askDelete = true }, modifier = Modifier.padding(top = 16.dp).height(48.dp)) {
                    Text("Удалить замер", color = t.danger, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
        }

        Button(
            onClick = {
                val keep = photos.toList()
                original?.photos?.filter { it !in keep }?.forEach { store.photoFile(it).delete() }
                onSave(
                    Entry(
                        id = original?.id ?: System.currentTimeMillis(),
                        date = date,
                        values = parsed.filterValues { it != null }.mapValues { it.value!! },
                        photos = keep,
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.onPrimary),
        ) { Text("Сохранить", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("Выбрать", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена", fontSize = 16.sp) } },
        ) { DatePicker(state = state) }
    }

    if (askDelete && original != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Удалить замер?") },
            text = { Text("Замер за ${dateLong(original.date).lowercase()} и его фото удалятся без возврата.") },
            confirmButton = {
                TextButton(onClick = {
                    added.forEach { store.photoFile(it).delete() }
                    onDelete(original)
                }) { Text("Удалить", color = t.danger, fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Оставить", fontSize = 16.sp) } },
        )
    }
}
