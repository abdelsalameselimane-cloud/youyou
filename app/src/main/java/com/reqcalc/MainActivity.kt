package com.reqcalc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent { App(store) }
    }
}

private fun num(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
private fun amt(d: Double): String = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(d)

@Composable
fun App(store: Store) {
    val dark = isSystemInDarkTheme()
    var lang by remember { mutableStateOf(store.lang ?: I18n.detect()) }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalTokens provides (if (dark) DarkTokens else LightTokens),
            LocalStrings provides I18n.dict.getValue(lang),
            LocalLayoutDirection provides (if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr),
        ) { Root(store, lang) { lang = it; store.lang = it } }
    }
}

@Composable
fun Root(store: Store, lang: String, onLang: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf(store.loadEntries()) }
    var screen by rememberSaveable { mutableStateOf(0) }          // 0 = home, 1 = results
    var query by rememberSaveable { mutableStateOf("") }
    var onlyVerify by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var homeErr by remember { mutableStateOf<String?>(null) }     // "open" | "none"
    var saveErr by remember { mutableStateOf(false) }
    var excelErr by remember { mutableStateOf(false) }
    var csvLang by remember { mutableStateOf("fr") }               // Excel notes language: French by default
    var toast by remember { mutableStateOf<String?>(null) }
    var langOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var editIdx by remember { mutableStateOf(0) }

    // Keep the last session on the device.
    LaunchedEffect(entries) { withContext(Dispatchers.IO) { store.saveEntries(entries) } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; homeErr = null
            val parsed: List<Entry>? = try {
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                if (bytes == null) null else withContext(Dispatchers.Default) { Engine.parse(String(bytes, Charsets.ISO_8859_1)) }
            } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
            busy = false
            when {
                parsed == null -> homeErr = "open"
                parsed.isEmpty() -> homeErr = "none"
                else -> { entries = parsed; query = ""; onlyVerify = false; saveErr = false; screen = 1 }
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            saving = true; saveErr = false
            val data = Engine.toCsv(entries, csvLang).toByteArray()
            val ok = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(data); true } ?: false } catch (e: Exception) { false }
            }
            saving = false
            if (ok) toast = "saved" else saveErr = true
        }
    }

    // Writes a temporary CSV and opens it straight in Excel (or any app that can read CSV).
    val openExcel: () -> Unit = {
        scope.launch {
            saving = true; excelErr = false
            val data = Engine.toCsv(entries, csvLang).toByteArray()
            val uri = withContext(Dispatchers.IO) {
                try {
                    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
                    val f = File(dir, "REQ_report.csv"); f.writeBytes(data)
                    FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                } catch (e: Exception) { null }
            }
            saving = false
            if (uri == null) { excelErr = true; return@launch }
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/csv").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                ctx.startActivity(Intent(view).setPackage("com.microsoft.office.excel"))
            } catch (e: ActivityNotFoundException) {
                try { ctx.startActivity(Intent.createChooser(view, null)) } catch (e2: Exception) { excelErr = true }
            } catch (e: SecurityException) { excelErr = true }
        }
    }

    // Back goes one step; it leaves the app only from the home screen.
    BackHandler(enabled = screen == 1) { screen = 0 }
    BackHandler(enabled = langOpen || editOpen) { langOpen = false; editOpen = false }

    val toastText = toast?.let { t(it) }

    Box(Modifier.fillMaxSize()) {
        Page {
            if (screen == 1 && entries.isNotEmpty()) {
                Results(
                    entries = entries, query = query, onQuery = { query = it },
                    onlyVerify = onlyVerify, onOnlyVerify = { onlyVerify = it },
                    saving = saving, saveErr = saveErr, excelErr = excelErr, onExcel = openExcel, csvLang = csvLang, onCsvLang = { csvLang = it },
                    onBack = { screen = 0 }, onExport = { export.launch("REQ_report.csv") },
                    onEdit = { editIdx = it; editOpen = true },
                )
            } else {
                Home(
                    hasLast = entries.isNotEmpty(),
                    lastDesc = t("last_desc", "n" to entries.size.toString(), "t" to num(Engine.summarize(entries).grand)),
                    busy = busy, err = homeErr,
                    onPick = { pick.launch(arrayOf("*/*")) }, onLast = { screen = 1 }, onLang = { langOpen = true },
                )
            }
        }
        Sheet(langOpen, { langOpen = false }) {
            Txt(t("language"), Type.card)
            I18n.langs.forEach { (code, name) -> LangRow(name, code == lang) { onLang(code); langOpen = false } }
        }
        Sheet(editOpen, { editOpen = false }) {
            entries.getOrNull(editIdx)?.let { e ->
                EditForm(e, onCancel = { editOpen = false }, onSave = { n ->
                    entries = entries.toMutableList().also { it[editIdx] = Engine.edit(it[editIdx], n) }
                    editOpen = false; toast = "updated"
                })
            }
        }
        Toast(toastText) { toast = null }
    }
}

// ── Home ────────────────────────────────────────────────────
@Composable
fun ColumnScope.Home(
    hasLast: Boolean, lastDesc: String, busy: Boolean, err: String?,
    onPick: () -> Unit, onLast: () -> Unit, onLang: () -> Unit,
) {
    val c = tk()
    Row(Modifier.padding(vertical = Sp.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.l)) {
        Logo()
        Column(Modifier.weight(1f)) {
            Txt(stringResource(R.string.app_name), Type.page, maxLines = 1)
            Txt(t("tagline"), Type.small, c.mu)
        }
        IconBtn(Ic.Globe, t("language"), onLang)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Sp.xl), verticalArrangement = Arrangement.spacedBy(Sp.l)) {
        if (busy) Banner(Kind.RUN, t("reading"), t("reading_desc"))
        else if (err != null) Banner(Kind.ERR, t("err_$err"), t("err_${err}_desc"), action = { SecondaryButton(t("try_again"), onPick) })
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.l)) {
                Tile(Ic.Upload)
                Column(Modifier.weight(1f)) {
                    Txt(t("import_title"), Type.card)
                    Txt(t("import_desc"), Type.small, c.mu)
                }
            }
            Spacer(Modifier.height(Sp.xl))
            PrimaryButton(t("choose_file"), onPick, icon = Ic.Upload, enabled = !busy, loading = busy)
        }
        if (hasLast && !busy) AppCard {
            Row(
                Modifier.clip(Rd.tile).clickable(onClick = onLast),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.l),
            ) {
                Tile(Ic.File)
                Column(Modifier.weight(1f)) {
                    Txt(t("last_title"), Type.bodyB)
                    Txt(lastDesc, Type.small, c.mu)
                }
                Glyph(Ic.Chevron, c.mu, mirror = true)
            }
        }
    }
}

@Composable
fun LangRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val c = tk()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Rd.input)
            .background(if (selected) c.pr.copy(alpha = 0.12f) else Color.Transparent, Rd.input)
            .border(1.dp, if (selected) c.pr else c.bd, Rd.input)
            .clickable(onClick = onClick).padding(horizontal = Sp.l, vertical = Sp.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(name, if (selected) Type.bodyB else Type.body, modifier = Modifier.weight(1f))
        if (selected) Glyph(Ic.Check, c.pr)
    }
}

// ── Results ─────────────────────────────────────────────────
@Composable
fun ColumnScope.Results(
    entries: List<Entry>, query: String, onQuery: (String) -> Unit,
    onlyVerify: Boolean, onOnlyVerify: (Boolean) -> Unit,
    saving: Boolean, saveErr: Boolean, excelErr: Boolean, onExcel: () -> Unit, csvLang: String, onCsvLang: (String) -> Unit,
    onBack: () -> Unit, onExport: () -> Unit, onEdit: (Int) -> Unit,
) {
    val c = tk()
    val s = Engine.summarize(entries)
    val shown = entries.withIndex().filter {
        val e = it.value
        (query.isBlank() || e.ref.contains(query.trim()) || e.time.contains(query.trim()) || e.total.toString() == query.trim()) &&
            (!onlyVerify || e.status == Status.VERIFY)
    }
    Row(Modifier.padding(vertical = Sp.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.l)) {
        IconBtn(Ic.Back, t("back"), onBack, mirror = true)
        Txt(t("results"), Type.page, modifier = Modifier.weight(1f))
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Sp.l), contentPadding = PaddingValues(bottom = Sp.xl)) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(t("grand"), Type.small, c.mu)
                        Txt(num(s.grand), Type.big)
                        Txt(t("grand_desc"), Type.small, c.mu)
                    }
                    CopyBtn(s.grand.toString(), t("copy_total"))
                }
                Spacer(Modifier.height(Sp.l))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.bd))
                Spacer(Modifier.height(Sp.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                    Stat(s.detected, t("detected"), c.tx)
                    Stat(s.valid, t("valid"), c.ok)
                    Stat(s.verify, t("verify"), if (s.verify > 0) c.er else c.mu)
                    Stat(s.noDispense, t("no_dispense"), c.mu)
                }
                Spacer(Modifier.height(Sp.xl))
                Txt(t("csv_lang"), Type.small, c.mu)
                Spacer(Modifier.height(Sp.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
                    Chip(t("lang_fr"), csvLang == "fr") { onCsvLang("fr") }
                    Chip(t("lang_en"), csvLang == "en") { onCsvLang("en") }
                }
                Spacer(Modifier.height(Sp.l))
                PrimaryButton(
                    if (saving) t("exporting") else t("export_csv"), onExport,
                    icon = Ic.Download, enabled = !saving, loading = saving,
                )
                Spacer(Modifier.height(Sp.m))
                SecondaryButton(t("open_excel"), onExcel, icon = Ic.Table, enabled = !saving)
            }
        }
        if (saveErr) item {
            Banner(Kind.ERR, t("err_save"), t("err_save_desc"), action = { SecondaryButton(t("try_again"), onExport) })
        }
        if (excelErr) item { Banner(Kind.ERR, t("err_excel"), t("err_excel_desc")) }
        item {
            if (s.verify > 0) Banner(Kind.ERR, t("verify_banner", "n" to s.verify.toString()), t("verify_banner_desc"))
            else Banner(Kind.OK, t("all_ok"), t("all_ok_desc"))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.m)) {
                Field(query, onQuery, t("search"), leading = Ic.Search)
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
                    Chip(t("f_all"), !onlyVerify) { onOnlyVerify(false) }
                    Chip(t("f_verify"), onlyVerify) { onOnlyVerify(true) }
                }
            }
        }
        if (shown.isEmpty()) item {
            AppCard {
                Txt(t("empty_filter"), Type.body, c.mu, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Sp.l))
                SecondaryButton(t("clear_filters"), { onQuery(""); onOnlyVerify(false) })
            }
        }
        items(shown, key = { it.index }) { iv -> EntryCard(iv.value) { onEdit(iv.index) } }
    }
}

@Composable
fun RowScope.Stat(n: Int, label: String, color: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(n.toString(), Type.card, color)
        Txt(label, Type.tiny, tk().mu, align = TextAlign.Center)
    }
}

@Composable
fun EntryCard(e: Entry, onEdit: () -> Unit) {
    val c = tk()
    val tint = when (e.status) { Status.VALID -> c.ok; Status.VERIFY -> c.er; Status.NO_DISPENSE -> c.mu }
    val icon = when (e.status) { Status.VALID -> Ic.CheckCircle; Status.VERIFY -> Ic.Alert; Status.NO_DISPENSE -> Ic.Ban }
    val title = if (e.ref.all { it.isDigit() }) "#${e.ref}" else e.ref
    val sub = listOf(e.time, "REQ ${amt(e.req)}").filter { it.isNotEmpty() }.joinToString(" · ")
    val hasFour = e.numbers.size == 4
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.l)) {
            Tile(icon, tint)
            Column(Modifier.weight(1f)) {
                Txt(ltr(title), Type.bodyB, maxLines = 1)
                Txt(ltr(sub), Type.small, c.mu, maxLines = 1)
            }
            Badge(t("st_${e.status.name}"), tint)
        }
        if (hasFour) {
            Spacer(Modifier.height(Sp.l))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
                Txt(ltr(e.numbers.joinToString(" + ") + " = " + num(e.total)), Type.bodyB, modifier = Modifier.weight(1f))
                CopyBtn(e.total.toString(), t("copy_total"))
                IconBtn(Ic.Edit, t("edit"), onEdit)
            }
        }
        if (e.code.isNotEmpty()) {
            Spacer(Modifier.height(Sp.s))
            Txt(t("n_${e.code}", "a" to e.arg), Type.small, if (e.status == Status.VERIFY) c.er else c.mu)
        }
        if (!hasFour && e.status == Status.VERIFY) {
            Spacer(Modifier.height(Sp.l))
            SecondaryButton(t("edit"), onEdit, icon = Ic.Edit)
        }
    }
}

// ── Edit sheet ──────────────────────────────────────────────
@Composable
fun ColumnScope.EditForm(e: Entry, onCancel: () -> Unit, onSave: (List<Int>) -> Unit) {
    val c = tk()
    var f by remember(e) { mutableStateOf(List(4) { e.numbers.getOrNull(it)?.toString() ?: "" }) }
    var bad by remember(e) { mutableStateOf(false) }
    Txt(t("edit_title"), Type.card)
    Txt(t("edit_desc"), Type.small, c.mu)
    for (row in 0..1) {
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
            for (col in 0..1) {
                val i = row * 2 + col
                Column(Modifier.weight(1f)) {
                    Txt(t("note_n", "n" to (i + 1).toString()), Type.small, c.mu)
                    Field(f[i], { v -> bad = false; f = f.toMutableList().also { l -> l[i] = v.filter { ch -> ch in '0'..'9' }.take(7) } }, "0", keyboard = KeyboardType.Number)
                }
            }
        }
    }
    if (bad) Txt(t("err_edit"), Type.small, c.er)
    Txt(t("original"), Type.small, c.mu)
    Box(Modifier.fillMaxWidth().heightIn(max = 120.dp).background(c.input, Rd.input).border(1.dp, c.bd, Rd.input).verticalScroll(rememberScrollState()).padding(Sp.m)) {
        Txt(ltr(e.raw), Type.mono, c.mu)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
        SecondaryButton(t("cancel"), onCancel, Modifier.weight(1f))
        PrimaryButton(t("save"), {
            val n = f.map { it.trim().toIntOrNull() }
            if (n.any { it == null }) bad = true else onSave(n.map { it!! })
        }, Modifier.weight(1f))
    }
}
