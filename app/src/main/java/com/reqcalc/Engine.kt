package com.reqcalc

enum class Status { VALID, NO_DISPENSE, VERIFY }
data class Entry(val ref: String, val time: String, val req: Double, val numbers: List<Int>,
                 val total: Int, val status: Status, val note: String, val raw: String,
                 val code: String = "", val arg: String = "")
data class Summary(val detected: Int, val valid: Int, val verify: Int, val noDispense: Int, val grand: Int)

object Engine {
    private val ESC = Regex("\u001B\\[[0-9;]*[A-Za-z]|\u001B[()][0-9A-Za-z]|\u001B[A-Za-z]\\d*|[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val REQ = Regex("REQ\\s*:\\s*([\\d.,]+)")
    private const val SEP = "\\s*[-–—,;|]\\s*"
    private val FOUR = Regex("^\\s*(\\d+)$SEP(\\d+)$SEP(\\d+)$SEP(\\d+)\\s*$")
    private val ANY = Regex("^\\s*\\d+($SEP\\d+)+\\s*$")
    private val HEADER = Regex("^\\s*(\\d+)\\s+\\d\\d/\\d\\d/\\d\\d")
    private val TIME = Regex("^\\s*(\\d\\d:\\d\\d:\\d\\d)\\s+[A-Z ]+?\\s*$")
    private val RC = Regex("\\bRC\\s+(\\d{3})\\b")
    private val NP = Regex("NOTES PRESENTED ([\\d,]+)")

    fun clean(t: String): String {
        val ar = "٠١٢٣٤٥٦٧٨٩"
        return ESC.replace(t, "").replace("\r\n", "\n").replace('\r', '\n')
            .map { c -> val i = ar.indexOf(c); if (i >= 0) ('0' + i) else c }.joinToString("")
    }

    fun parse(input: String): List<Entry> {
        val l = clean(input).split("\n")
        val idx = l.indices.filter { REQ.containsMatchIn(l[it]) }
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        idx.forEachIndexed { k, i ->
            val end = minOf(idx.getOrElse(k + 1) { l.size }, i + 14)
            var ref = String.format(java.util.Locale.US, "REQ %03d", k + 1); var time = ""
            for (j in i - 1 downTo maxOf(0, i - 4)) {
                val h = HEADER.find(l[j]); if (h != null) { ref = h.groupValues[1]; break }
                val t = TIME.find(l[j]); if (t != null) time = t.groupValues[1]
            }
            val amt = REQ.find(l[i])!!.groupValues[1].replace(",", "").trimEnd('.').toDoubleOrNull() ?: 0.0
            var nums = emptyList<Int>(); var bad = ""
            for (j in i + 1 until minOf(end, i + 4)) {
                if (l[j].isBlank()) continue
                val m = FOUR.find(l[j])
                if (m != null) nums = m.groupValues.drop(1).map { it.toInt() }
                else if (ANY.matches(l[j])) bad = l[j].trim()
                break
            }
            val raw = l.subList(i, end).joinToString("\n").trim()
            val rc = RC.find(raw)?.groupValues?.get(1)
            var status: Status; var note = ""; var code = ""; var arg = ""
            if (nums.size == 4) {
                status = Status.VALID
                val np = NP.find(raw)
                if (np != null && np.groupValues[1].split(",").sumOf { it.toIntOrNull() ?: 0 } != nums.sum()) {
                    status = Status.VERIFY; note = "Sum differs from NOTES PRESENTED"; code = "SUM"
                }
            } else if (bad.isNotEmpty()) { status = Status.VERIFY; note = "Expected 4 numbers: $bad"; code = "BAD"; arg = bad }
            else if (amt == 0.0 || rc != null) { status = Status.NO_DISPENSE; note = if (rc != null) "Declined RC $rc" else "No cash"
                code = if (rc != null) "DECLINED" else "NOCASH"; arg = rc ?: "" }
            else { status = Status.VERIFY; note = "REQ > 0 but no four-number line"; code = "NOLINE" }
            if (seen.add("$ref|$time|$amt|${nums.joinToString("-")}"))
                out.add(Entry(ref, time, amt, nums, nums.sum(), status, note, raw, code, arg))
        }
        return out
    }

    fun summarize(e: List<Entry>) = Summary(e.size, e.count { it.status == Status.VALID },
        e.count { it.status == Status.VERIFY }, e.count { it.status == Status.NO_DISPENSE },
        e.filter { it.status == Status.VALID }.sumOf { it.total })

    fun edit(e: Entry, n: List<Int>) = e.copy(numbers = n, total = n.sum(),
        status = if (n.size == 4) Status.VALID else Status.VERIFY, note = "Edited", code = "EDITED", arg = "")

    /** Text of the Note column in the chosen file language ("fr" default, or "en"). */
    fun noteText(x: Entry, lang: String): String = if (lang == "en") x.note else when (x.code) {
        "SUM" -> "La somme diffère de NOTES PRESENTED"
        "BAD" -> "Quatre nombres attendus : ${x.arg}"
        "DECLINED" -> "Refusé RC ${x.arg}"
        "NOCASH" -> "Aucun retrait"
        "NOLINE" -> "REQ > 0 mais aucune ligne de quatre nombres"
        "EDITED" -> "Modifié"
        else -> x.note
    }

    /** CSV for Excel. [lang] is "fr" (default) or "en" and applies to headers, statuses and notes. */
    fun toCsv(e: List<Entry>, lang: String = "fr"): String {
        val fr = lang != "en"
        val head = if (fr) "Réf,Heure,REQ,N1,N2,N3,N4,Total,Statut,Note" else "Ref,Time,REQ,N1,N2,N3,N4,Total,Status,Note"
        val sb = StringBuilder("\uFEFF$head\n")
        for (x in e) {
            val st = if (!fr) x.status.toString() else when (x.status) {
                Status.VALID -> "VALIDE"; Status.NO_DISPENSE -> "SANS DISTRIBUTION"; Status.VERIFY -> "À VÉRIFIER"
            }
            val note = noteText(x, lang).replace("\"", "\"\"")
            sb.append("${x.ref},${x.time},${x.req},${(0..3).joinToString(",") { x.numbers.getOrNull(it)?.toString() ?: "" }},${x.total},$st,\"$note\"\n")
        }
        sb.append("${if (fr) "TOTAL GÉNÉRAL" else "GRAND TOTAL"},,,,,,,${summarize(e).grand},,\n")
        return sb.toString()
    }
}
