package com.reqcalc

// Translation dictionary (Arabic / English / French) + local persistence.

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

val LocalStrings = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Look up a string; "{n}"-style placeholders are filled from [args]. */
@Composable
fun t(key: String, vararg args: Pair<String, String>): String {
    var s = LocalStrings.current[key] ?: key
    for ((k, v) in args) s = s.replace("{$k}", v)
    return s
}

object I18n {
    val langs = listOf("ar" to "العربية", "en" to "English", "fr" to "Français")

    /** Device language if supported, otherwise Arabic (the app's original default). */
    fun detect(): String = Locale.getDefault().language.let { l -> if (langs.any { it.first == l }) l else "ar" }

    val dict: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "tagline" to "Add up the notes from your ATM log.",
            "language" to "Language", "back" to "Back",
            "import_title" to "Import a document", "import_desc" to "Choose the text file that holds your REQ log.",
            "choose_file" to "Choose file",
            "last_title" to "Continue where you left off", "last_desc" to "{n} entries · total {t}",
            "reading" to "Reading your file…", "reading_desc" to "This only takes a few seconds.",
            "err_open" to "We couldn't open that file", "err_open_desc" to "Check that the file still exists, then choose it again.",
            "err_none" to "No REQ found", "err_none_desc" to "This file has no REQ lines. Choose the right log file and try again.",
            "try_again" to "Try again",
            "results" to "Results", "grand" to "Grand total", "grand_desc" to "Counts valid entries only.",
            "detected" to "REQ found", "valid" to "Valid", "verify" to "To check", "no_dispense" to "No cash",
            "verify_banner" to "{n} to check", "verify_banner_desc" to "They stay out of the total until you fix them.",
            "all_ok" to "Everything adds up", "all_ok_desc" to "No entry needs a check.",
            "search" to "Search by number or time", "f_all" to "All", "f_verify" to "To check",
            "empty_filter" to "Nothing matches your search.", "clear_filters" to "Clear filters",
            "export_csv" to "Export to Excel (CSV)", "exporting" to "Saving…",
            "saved" to "File saved", "updated" to "Entry updated",
            "err_save" to "We couldn't save the file", "err_save_desc" to "Choose another location and try again.",
            "copy_total" to "Copy total", "edit" to "Edit",
            "edit_title" to "Edit the numbers", "edit_desc" to "Type the four note counts for this REQ.",
            "note_n" to "Note {n}", "save" to "Save", "cancel" to "Cancel",
            "err_edit" to "Enter four whole numbers.", "original" to "Original text",
            "st_VALID" to "Valid", "st_VERIFY" to "To check", "st_NO_DISPENSE" to "No cash",
            "n_SUM" to "The sum differs from NOTES PRESENTED. Check the numbers.",
            "n_BAD" to "Expected four numbers but found: {a}",
            "n_DECLINED" to "The ATM declined this request (code {a}). Nothing was paid out.",
            "n_NOCASH" to "No cash was requested.",
            "n_NOLINE" to "Cash was requested but no line of four numbers was found.",
            "n_EDITED" to "You edited this entry.",
        ),
        "fr" to mapOf(
            "tagline" to "Additionnez les billets de votre journal DAB.",
            "language" to "Langue", "back" to "Retour",
            "import_title" to "Importer un document", "import_desc" to "Choisissez le fichier texte qui contient votre journal REQ.",
            "choose_file" to "Choisir un fichier",
            "last_title" to "Reprendre où vous étiez", "last_desc" to "{n} entrées · total {t}",
            "reading" to "Lecture du fichier…", "reading_desc" to "Cela ne prend que quelques secondes.",
            "err_open" to "Impossible d'ouvrir ce fichier", "err_open_desc" to "Vérifiez que le fichier existe toujours, puis choisissez-le à nouveau.",
            "err_none" to "Aucun REQ trouvé", "err_none_desc" to "Ce fichier ne contient aucune ligne REQ. Choisissez le bon fichier et réessayez.",
            "try_again" to "Réessayer",
            "results" to "Résultats", "grand" to "Total général", "grand_desc" to "Seules les entrées valides sont comptées.",
            "detected" to "REQ trouvés", "valid" to "Valides", "verify" to "À vérifier", "no_dispense" to "Sans billets",
            "verify_banner" to "{n} à vérifier", "verify_banner_desc" to "Elles restent hors du total tant que vous ne les corrigez pas.",
            "all_ok" to "Tout est cohérent", "all_ok_desc" to "Aucune entrée à vérifier.",
            "search" to "Rechercher un numéro ou une heure", "f_all" to "Toutes", "f_verify" to "À vérifier",
            "empty_filter" to "Aucun résultat pour cette recherche.", "clear_filters" to "Effacer les filtres",
            "export_csv" to "Exporter vers Excel (CSV)", "exporting" to "Enregistrement…",
            "saved" to "Fichier enregistré", "updated" to "Entrée mise à jour",
            "err_save" to "Impossible d'enregistrer le fichier", "err_save_desc" to "Choisissez un autre emplacement et réessayez.",
            "copy_total" to "Copier le total", "edit" to "Modifier",
            "edit_title" to "Modifier les nombres", "edit_desc" to "Saisissez les quatre nombres de billets pour ce REQ.",
            "note_n" to "Cassette {n}", "save" to "Enregistrer", "cancel" to "Annuler",
            "err_edit" to "Saisissez quatre nombres entiers.", "original" to "Texte d'origine",
            "st_VALID" to "Valide", "st_VERIFY" to "À vérifier", "st_NO_DISPENSE" to "Sans billets",
            "n_SUM" to "La somme diffère de NOTES PRESENTED. Vérifiez les nombres.",
            "n_BAD" to "Quatre nombres attendus, trouvé : {a}",
            "n_DECLINED" to "Le DAB a refusé cette demande (code {a}). Aucun billet distribué.",
            "n_NOCASH" to "Aucun retrait demandé.",
            "n_NOLINE" to "Un retrait a été demandé mais aucune ligne de quatre nombres n'a été trouvée.",
            "n_EDITED" to "Vous avez modifié cette entrée.",
        ),
        "ar" to mapOf(
            "tagline" to "اجمع أوراق النقد من سجل جهاز الصرف.",
            "language" to "اللغة", "back" to "رجوع",
            "import_title" to "استيراد مستند", "import_desc" to "اختر الملف النصي الذي يحتوي سجل REQ.",
            "choose_file" to "اختيار ملف",
            "last_title" to "أكمل من حيث توقفت", "last_desc" to "{n} عملية · المجموع {t}",
            "reading" to "جارٍ قراءة الملف…", "reading_desc" to "يستغرق هذا بضع ثوانٍ فقط.",
            "err_open" to "تعذّر فتح هذا الملف", "err_open_desc" to "تأكد أن الملف ما زال موجودًا ثم اخترْه مرة أخرى.",
            "err_none" to "لم نجد أي REQ", "err_none_desc" to "لا يحتوي هذا الملف على أسطر REQ. اختر ملف السجل الصحيح وحاول مرة أخرى.",
            "try_again" to "حاول مرة أخرى",
            "results" to "النتائج", "grand" to "المجموع العام", "grand_desc" to "يشمل العمليات الصالحة فقط.",
            "detected" to "عدد REQ", "valid" to "صالحة", "verify" to "للتحقق", "no_dispense" to "بدون صرف",
            "verify_banner" to "{n} تحتاج تحققًا", "verify_banner_desc" to "تبقى خارج المجموع حتى تصحّحها.",
            "all_ok" to "كل شيء متطابق", "all_ok_desc" to "لا توجد عملية تحتاج تحققًا.",
            "search" to "ابحث برقم العملية أو الوقت", "f_all" to "الكل", "f_verify" to "للتحقق",
            "empty_filter" to "لا توجد نتائج مطابقة لبحثك.", "clear_filters" to "مسح التصفية",
            "export_csv" to "تصدير إلى Excel (CSV)", "exporting" to "جارٍ الحفظ…",
            "saved" to "تم حفظ الملف", "updated" to "تم تحديث العملية",
            "err_save" to "تعذّر حفظ الملف", "err_save_desc" to "اختر مكانًا آخر وحاول مرة أخرى.",
            "copy_total" to "نسخ المجموع", "edit" to "تعديل",
            "edit_title" to "تعديل الأرقام", "edit_desc" to "اكتب أعداد الأوراق الأربعة لهذه العملية.",
            "note_n" to "الفئة {n}", "save" to "حفظ", "cancel" to "إلغاء",
            "err_edit" to "أدخل أربعة أعداد صحيحة.", "original" to "النص الأصلي",
            "st_VALID" to "صالحة", "st_VERIFY" to "للتحقق", "st_NO_DISPENSE" to "بدون صرف",
            "n_SUM" to "المجموع يختلف عن NOTES PRESENTED. تحقق من الأرقام.",
            "n_BAD" to "كان المتوقع أربعة أرقام لكن وُجد: {a}",
            "n_DECLINED" to "رفض الجهاز هذا الطلب (الرمز {a}). لم تُصرف أي أوراق.",
            "n_NOCASH" to "لم يُطلب أي مبلغ.",
            "n_NOLINE" to "طُلب مبلغ لكن لم نجد سطر الأرقام الأربعة.",
            "n_EDITED" to "قمت بتعديل هذه العملية.",
        ),
    )
}

/** Saves the language and the last session so they survive the app being closed. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("reqcalc", Context.MODE_PRIVATE)

    var lang: String?
        get() = p.getString("lang", null)
        set(v) { p.edit().putString("lang", v).apply() }

    fun saveEntries(list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) arr.put(JSONObject().apply {
            put("ref", e.ref); put("time", e.time); put("req", e.req)
            put("nums", JSONArray(e.numbers)); put("status", e.status.name)
            put("note", e.note); put("raw", e.raw); put("code", e.code); put("arg", e.arg)
        })
        p.edit().putString("entries", arr.toString()).apply()
    }

    fun loadEntries(): List<Entry> = try {
        val arr = JSONArray(p.getString("entries", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val n = o.getJSONArray("nums")
            val nums = (0 until n.length()).map { n.getInt(it) }
            Entry(o.getString("ref"), o.getString("time"), o.getDouble("req"), nums, nums.sum(),
                Status.valueOf(o.getString("status")), o.getString("note"), o.getString("raw"),
                o.optString("code"), o.optString("arg"))
        }
    } catch (e: Exception) { emptyList() }
}
