package com.example.data.export

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportSummary(
    val tables: Int,
    val rows: Int
)

object DataExportEngine {
    val allTables = listOf(
        "users", "categories", "raw_materials", "products", "recipes", "recipe_items",
        "mixtures", "mixture_items", "suppliers", "purchases", "purchase_items",
        "inventory_transactions", "customers", "debt_transactions", "shifts",
        "shift_cash_movements", "shift_handovers", "sales", "sale_items", "expenses",
        "waste_transactions", "stock_counts", "stock_adjustments", "audit_logs",
        "cafeteria_settings"
    )

    suspend fun exportCsvZip(
        context: Context,
        database: AppDatabase,
        destination: Uri,
        selectedTables: Set<String> = allTables.toSet(),
        startTime: Long? = null,
        endTime: Long? = null
    ): Result<ExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            require(startTime == null || endTime == null || startTime <= endTime) { "نطاق التاريخ غير صالح" }
            val tables = normalizeTables(selectedTables)
            val db = database.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").close()

            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    var rows = 0
                    tables.forEach { table ->
                        val data = readTable(db, table, startTime, endTime)
                        rows += data.rows.size
                        zip.putNextEntry(ZipEntry(table + ".csv"))
                        zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        val writer = OutputStreamWriter(zip, StandardCharsets.UTF_8)
                        writer.write(data.columns.joinToString(",") { csv(it) })
                        writer.write("\n")
                        data.rows.forEach { row ->
                            writer.write(row.joinToString(",") { csv(it) })
                            writer.write("\n")
                        }
                        writer.flush()
                        zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("manifest.txt"))
                    zip.write(
                        ("POWER FEUL POS export\n" +
                            "tables=" + tables.size + "\n" +
                            "rows=" + rows + "\n" +
                            "createdAt=" + System.currentTimeMillis() + "\n").toByteArray(StandardCharsets.UTF_8)
                    )
                    zip.closeEntry()
                    ExportSummary(tables.size, rows)
                }
            } ?: error("تعذر فتح الملف للتصدير")
        }
    }

    suspend fun exportXlsx(
        context: Context,
        database: AppDatabase,
        destination: Uri,
        selectedTables: Set<String> = allTables.toSet(),
        startTime: Long? = null,
        endTime: Long? = null
    ): Result<ExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            require(startTime == null || endTime == null || startTime <= endTime) { "نطاق التاريخ غير صالح" }
            val tables = normalizeTables(selectedTables)
            val db = database.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").close()

            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    val sheets = tables.map { table ->
                        val data = readTable(db, table, startTime, endTime)
                        SheetData(table, data.columns, data.rows)
                    }
                    writeXlsxPackage(zip, sheets)
                    ExportSummary(sheets.size, sheets.sumOf { it.rows.size })
                }
            } ?: error("تعذر فتح ملف Excel للتصدير")
        }
    }

    private data class TableData(val columns: List<String>, val rows: List<List<String>>)
    private data class SheetData(val name: String, val columns: List<String>, val rows: List<List<String>>)

    private fun normalizeTables(selectedTables: Set<String>): List<String> {
        val selected = selectedTables.intersect(allTables.toSet())
        return if (selected.isEmpty()) allTables else allTables.filter { it in selected }
    }

    private fun readTable(
        db: SupportSQLiteDatabase,
        table: String,
        startTime: Long?,
        endTime: Long?
    ): TableData {
        val quoted = quote(table)
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(" + quoted + ")").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) columns += cursor.getString(nameIndex)
            }
        }

        val dateColumn = columns.firstOrNull {
            it == "createdAt" || it == "updatedAt" || it == "purchaseDate" ||
                it == "date" || it == "timestamp" || it == "startTime" ||
                it == "endTime" || it == "lastTransactionDate"
        }

        val sql: String
        val args: Array<String>?
        if (dateColumn != null && startTime != null && endTime != null) {
            sql = "SELECT * FROM " + quoted + " WHERE " + quote(dateColumn) + " BETWEEN ? AND ?"
            args = arrayOf(startTime.toString(), endTime.toString())
        } else {
            sql = "SELECT * FROM " + quoted
            args = null
        }

        val rows = mutableListOf<List<String>>()
        db.query(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                rows += buildList(cursor.columnCount) { index ->
                    if (cursor.isNull(index)) "" else cursor.getString(index).orEmpty()
                }
            }
        }
        return TableData(columns, rows)
    }

    private fun writeXlsxPackage(zip: ZipOutputStream, sheets: List<SheetData>) {
        add(zip, "[Content_Types].xml", contentTypes(sheets.size))
        add(zip, "_rels/.rels", rootRels())
        add(zip, "xl/workbook.xml", workbookXml(sheets))
        add(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
        sheets.forEachIndexed { index, sheet ->
            add(zip, "xl/worksheets/sheet" + (index + 1) + ".xml", sheetXml(sheet))
        }
    }

    private fun add(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(count: Int): String {
        val overrides = (1..count).joinToString("") {
            "<Override PartName=\"/xl/worksheets/sheet" + it +
                ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            overrides + "</Types>"
    }

    private fun rootRels(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbookXml(sheets: List<SheetData>): String {
        val sheetXml = sheets.mapIndexed { index, sheet ->
            "<sheet name=\"" + xml(sheet.name.take(31)) + "\" sheetId=\"" + (index + 1) +
                "\" r:id=\"rId" + (index + 1) + "\"/>"
        }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets>" + sheetXml + "</sheets></workbook>"
    }

    private fun workbookRels(count: Int): String {
        val rels = (1..count).joinToString("") {
            "<Relationship Id=\"rId" + it +
                "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
                "Target=\"worksheets/sheet" + it + ".xml\"/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            rels + "</Relationships>"
    }

    private fun sheetXml(sheet: SheetData): String {
        val rows = sequenceOf(sheet.columns).plus(sheet.rows.asSequence())
        val body = rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { colIndex, value ->
                "<c r=\"" + columnName(colIndex) + (rowIndex + 1) +
                    "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + xml(value) +
                    "</t></is></c>"
            }.joinToString("")
            "<row r=\"" + (rowIndex + 1) + "\">" + cells + "</row>"
        }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<sheetData>" + body + "</sheetData></worksheet>"
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            out.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return out.reverse().toString()
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun xml(value: String): String {
        val cleaned = value.filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }
        return cleaned
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}