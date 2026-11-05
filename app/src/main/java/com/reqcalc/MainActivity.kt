package com.reqcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var onlyVerify by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            msg = "…"
            try {
                entries = withContext(Dispatchers.Default) {
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Engine.parse(String(bytes, Charsets.ISO_8859_1))
                }
                msg = ""
            } catch (e: Exception) { msg = e.message ?: "Error" }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(Engine.toCsv(entries).toByteArray()) }
    }
    val s = Engine.summarize(entries)
    val shown = entries.filter { (q.isBlank() || it.ref.contains(q) || it.time.contains(q)) && (!onlyVerify || it.status == Status.VERIFY) }
    Column(Modifier.systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button({ open.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(R.string.import_doc), fontSize = 20.sp) }
        if (msg.isNotEmpty()) Text(msg)
        if (entries.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("${stringResource(R.string.grand)}: ${s.grand}", fontSize = 28.sp)
                Text("${stringResource(R.string.detected)}: ${s.detected} • ${stringResource(R.string.valid)}: ${s.valid} • ${stringResource(R.string.verify)}: ${s.verify} • ${stringResource(R.string.no_dispense)}: ${s.noDispense}")
            } }
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search)) }, singleLine = true)
            Row { Switch(onlyVerify, { onlyVerify = it }); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.only_verify)) }
            Button({ save.launch("REQ_report.csv") }) { Text(stringResource(R.string.export_csv)) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown) { e ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${e.ref}  ${e.time}  •  REQ ${e.req}")
                        Text(if (e.numbers.size == 4) "${e.numbers.joinToString(" + ")} = ${e.total}" else "— ${e.note}", fontSize = 18.sp)
                        if (e.status != Status.VALID) Text("${e.status}: ${e.note}", color = MaterialTheme.colorScheme.error)
                    } }
                }
            }
        }
    }
}
