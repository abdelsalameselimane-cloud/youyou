package com.reqcalc

enum class Status { VALID, NO_DISPENSE, VERIFY }
data class Entry(val ref: String, val time: String, val req: Double, val numbers: List<Int>,
                 val total: Int, val status: Status, val note: String, val raw: String)
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
            var ref = "REQ %03d".format(k + 1); var time = ""
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
            var status: Status; var note = ""
            if (nums.size == 4) {
                status = Status.VALID
                val np = NP.find(raw)
                if (np != null && np.groupValues[1].split(",").sumOf { it.toIntOrNull() ?: 0 } != nums.sum()) {
                    status = Status.VERIFY; note = "Sum differs from NOTES PRESENTED"
                }
            } else if (bad.isNotEmpty()) { status = Status.VERIFY; note = "Expected 4 numbers: $bad" }
            else if (amt == 0.0 || rc != null) { status = Status.NO_DISPENSE; note = if (rc != null) "Declined RC $rc" else "No cash" }
            else { status = Status.VERIFY; note = "REQ > 0 but no four-number line" }
            if (seen.add("$ref|$time|$amt|${nums.joinToString("-")}"))
                out.add(Entry(ref, time, amt, nums, nums.sum(), status, note, raw))
        }
        return out
    }

    fun summarize(e: List<Entry>) = Summary(e.size, e.count { it.status == Status.VALID },
        e.count { it.status == Status.VERIFY }, e.count { it.status == Status.NO_DISPENSE },
        e.filter { it.status == Status.VALID }.sumOf { it.total })

    fun edit(e: Entry, n: List<Int>) = e.copy(numbers = n, total = n.sum(),
        status = if (n.size == 4) Status.VALID else Status.VERIFY, note = "Edited")

    fun toCsv(e: List<Entry>): String {
        val sb = StringBuilder("\uFEFFRef,Time,REQ,N1,N2,N3,N4,Total,Status,Note\n")
        for (x in e) sb.append("${x.ref},${x.time},${x.req},${(0..3).joinToString(",") { x.numbers.getOrNull(it)?.toString() ?: "" }},${x.total},${x.status},\"${x.note}\"\n")
        sb.append("GRAND TOTAL,,,,,,,${summarize(e).grand},,\n")
        return sb.toString()
    }
}
