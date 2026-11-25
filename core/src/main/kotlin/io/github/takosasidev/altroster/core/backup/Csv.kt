package io.github.takosasidev.altroster.core.backup

/** RFC 4180 の CSV の読み書き。バックアップの中身にだけ使う。 */
object Csv {
    const val EOL = "\r\n"

    /** カンマ・引用符・改行・前後の空白があれば引用符で囲む。null は空欄。 */
    fun escape(field: String?): String {
        val value = field ?: return ""
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value != value.trim()
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun row(fields: List<String?>): String = fields.joinToString(",", postfix = EOL, transform = ::escape)

    /** 行ごとの欄のリスト。引用符の中の改行とカンマは欄の中身として読む。 */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var started = false

        fun endRow() {
            fields.add(field.toString())
            field.setLength(0)
            rows.add(fields)
            fields = mutableListOf()
            started = false
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            started = true
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (started) endRow()
        return rows
    }
}
