package com.example.data.importdata

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.export.DataExportEngine
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

enum class ImportConflictStrategy {
    SKIP,
    REPLACE,
    ABORT
}

data class ImportTablePreview(
    val table: String,
    val rows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val duplicateRows: Int,
    val error: String? = null
)

data class ImportPreview(
    val tables: List<ImportTablePreview>,
    val totalRows: Int,
    val totalValidRows: Int,
    val totalInvalidRows: Int,
    val totalDuplicates: Int
)

data class ImportResult(
    val importedRows: Int,
    val skippedRows: Int,
    val replacedRows: Int
)

class DataImportEngine(
    private val database: AppDatabase
) {
    suspend fun previewCsvZip(
        context: Context,
        uri: Uri
    ): Result<ImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val tables = readZipTables(context, uri)
            val db = database.openHelper.readableDatabase
            val previews = tables.map { table ->
                previewTable(db, table.key, table.value)
            }
            ImportPreview(
                tables = previews,
                totalRows = previews.sumOf { it.rows },
                totalValidRows = previews.sumOf { it.validRows },
                totalInvalidRows = previews.sumOf { it.invalidRows },
                totalDuplicates = previews.sumOf { it.duplicateRows }
            )
        }
    }

    suspend fun importCsvZip(
        context: Context,
        uri: Uri,
        conflictStrategy: ImportConflictStrategy,
        confirmed: Boolean
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(confirmed) { "يجب تأكيد عملية الاستيراد صراحةً" }

            val tables = readZipTables(context, uri)
                .filterKeys { it != "users" }
                .toList()
                .sortedBy { tableIndex(it.first) }

            val db = database.openHelper.writableDatabase
            db.execSQL("PRAGMA foreign_keys = ON")
            db.beginTransaction()
            var imported = 0
            var skipped = 0
            var replaced = 0

            try {
                for ((tableName, rows) in tables) {
                    val schema = readSchema(db, tableName)
                    validateHeader(tableName, rows.firstOrNull()?.keys?.toList() ?: emptyList(), schema)
                    for (row in rows) {
                        validateRow(tableName, row, schema)
                        val result = insertRow(db, tableName, row, conflictStrategy)
                        when (result) {
                            RowInsertResult.INSERTED -> imported++
                            RowInsertResult.SKIPPED -> skipped++
                            RowInsertResult.REPLACED -> {
                                imported++
                                replaced++
                            }
                        }
                    }
                }

                db.query("PRAGMA foreign_key_check").use { cursor ->
                    if (cursor.moveToFirst()) {
                        error("فشل التحقق من العلاقات بين الجداول؛ تم إلغاء الاستيراد")
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            ImportResult(imported, skipped, replaced)
        }
    }

    private enum class RowInsertResult {
        INSERTED,
        SKIPPED,
        REPLACED
    }

    private data class TableSchema(
        val columns: List<String>,
        val pkColumns: List<String>
    )

    private data class ImportedRow(
        val columns: List<String>,
        val values: List<String>
    ) {
        fun asMap(): Map<String, String> = columns.zip(values).toMap()
    }

    private fun readZipTables(
        context: Context,
        uri: Uri
    ): Map<String, List<ImportedRow>> {
        val result = linkedMapOf<String, MutableList<ImportedRow>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(".csv", ignoreCase = true)) {
                        zip.closeEntry()
                        continue
                    }
                    val table = entry.name.substringBeforeLast(".")
                    require(table in DataExportEngine.allTables) {
                        "الجدول غير مسموح للاستيراد: " + table
                    }
                    if (table == "users") {
                        zip.closeEntry()
                        continue
                    }

                    val reader = BufferedReader(
                        InputStreamReader(zip, StandardCharsets.UTF_8)
                    )
                    val csvRows = mutableListOf<List<String>>()
                    var record = mutableListOf<String>()
                    var field = StringBuilder()
                    var quoted = false

                    fun finishField() {
                        record += field.toString()
                        field = StringBuilder()
                    }

                    fun finishRecord() {
                        finishField()
                        if (record.size == 1 && record[0].isBlank()) {
                            record = mutableListOf()
                        } else if (record.isNotEmpty()) {
                            csvRows += record
                            record = mutableListOf()
                        }
                    }

                    while (true) {
                        val line = reader.readLine() ?: break
                        var i = 0
                        while (i < line.length) {
                            val ch = line[i]
                            when {
                                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                                    field.append('"')
                                    i++
                                }
                                ch == '"' -> {
                                    quoted = !quoted
                                }
                                ch == ',' && !quoted -> finishField()
                                else -> field.append(ch)
                            }
                            i++
                        }
                        if (!quoted) {
                            finishRecord()
                        } else {
                            field.append('
')
                        }
                    }

                    require(!quoted) { "ملف CSV غير مكتمل الاقتباس: " + table }
                    require(csvRows.isNotEmpty()) { "ملف CSV فارغ: " + table }

                    val header = csvRows.first()
                    val dataRows = csvRows.drop(1).map { values ->
                        require(values.size == header.size) {
                            "عدد أعمدة السجل لا يطابق العنوان في " + table
                        }
                        ImportedRow(header, values)
                    }
                    result[table] = dataRows.toMutableList()
                    zip.closeEntry()
                }
            }
        } ?: error("تعذر فتح ملف الاستيراد")
        return result
    }

    private fun previewTable(
        db: SupportSQLiteDatabase,
        table: String,
        rows: List<ImportedRow>
    ): ImportTablePreview {
        return runCatching {
            require(table in DataExportEngine.allTables) { "جدول غير مسموح: " + table }
            require(table != "users") { "لا يُستورد جدول المستخدمين من CSV" }
            val schema = readSchema(db, table)
            val header = rows.firstOrNull()?.columns ?: emptyList()
            validateHeader(table, header, schema)
            val seen = HashSet<String>()
            var valid = 0
            var invalid = 0
            var duplicates = 0

            rows.forEach { row ->
                try {
                    validateRow(table, row, schema)
                    val key = pkKey(row.asMap(), schema.pkColumns)
                    if (key != null && !seen.add(key)) {
                        duplicates++
                    }
                    valid++
                } catch (_: IllegalArgumentException) {
                    invalid++
                }
            }

            ImportTablePreview(table, rows.size, valid, invalid, duplicates)
        }.getOrElse {
            ImportTablePreview(table, rows.size, 0, rows.size, 0, it.message)
        }
    }

    private fun readSchema(
        db: SupportSQLiteDatabase,
        table: String
    ): TableSchema {
        val columns = mutableListOf<String>()
        val pkPairs = mutableListOf<Pair<Int, String>>()

        db.query("PRAGMA table_info(" + quote(table) + ")").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val pkIndex = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    columns += name
                    if (pkIndex >= 0 && cursor.getInt(pkIndex) > 0) {
                        pkPairs += cursor.getInt(pkIndex) to name
                    }
                }
            }
        }

        require(columns.isNotEmpty()) { "الجدول غير موجود في قاعدة البيانات: " + table }
        return TableSchema(
            columns = columns,
            pkColumns = pkPairs.sortedBy { it.first }.map { it.second }
        )
    }

    private fun validateHeader(
        table: String,
        header: List<String>,
        schema: TableSchema
    ) {
        require(header.isNotEmpty()) { "عنوان CSV فارغ: " + table }
        require(header.none { it in setOf("pinHash", "pinSalt", "apiKey", "geminiApiKey") }) {
            "ملف الاستيراد يحتوي أعمدة أسرار غير مسموحة: " + table
        }
        require(header.distinct().size == header.size) {
            "عناوين الأعمدة مكررة في " + table
        }
        require(header.all { it in schema.columns }) {
            "ملف " + table + " يحتوي عموداً غير موجود في Schema"
        }
    }

    private fun validateRow(
        table: String,
        row: ImportedRow,
        schema: TableSchema
    ) {
        validateHeader(table, row.columns, schema)
        require(row.values.size == row.columns.size) {
            "عدد القيم غير صحيح في " + table
        }
        if (schema.pkColumns.isNotEmpty()) {
            require(pkKey(row.asMap(), schema.pkColumns) != null) {
                "المفتاح الأساسي مفقود في " + table
            }
        }
    }

    private fun pkKey(
        row: Map<String, String>,
        pkColumns: List<String>
    ): String? {
        if (pkColumns.isEmpty()) return null
        val values = pkColumns.map { row[it].orEmpty() }
        if (values.any { it.isBlank() }) return null
        return values.joinToString("\u001F")
    }

    private fun insertRow(
        db: SupportSQLiteDatabase,
        table: String,
        row: ImportedRow,
        strategy: ImportConflictStrategy
    ): RowInsertResult {
        val content = android.content.ContentValues()
        row.asMap().forEach { (column, value) ->
            content.put(column, value)
        }

        val conflict = when (strategy) {
            ImportConflictStrategy.SKIP -> SQLiteDatabase.CONFLICT_IGNORE
            ImportConflictStrategy.REPLACE -> SQLiteDatabase.CONFLICT_REPLACE
            ImportConflictStrategy.ABORT -> SQLiteDatabase.CONFLICT_ABORT
        }

        val result = db.insert(table, conflict, content)
        if (result == -1L) {
            if (strategy == ImportConflictStrategy.ABORT) {
                error("تعذر إدخال سجل إلى " + table)
            }
            return RowInsertResult.SKIPPED
        }
        return if (strategy == ImportConflictStrategy.REPLACE) {
            RowInsertResult.REPLACED
        } else {
            RowInsertResult.INSERTED
        }
    }

    private fun tableIndex(table: String): Int {
        val index = DataExportEngine.allTables.indexOf(table)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun quote(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}