package com.example.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Safely moves a complete local POS database through Android's document picker. */
object DatabaseBackupManager {
    private const val DATABASE_NAME = "power_feul_pos.db"

    suspend fun export(context: Context, database: AppDatabase, destination: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Room normally uses WAL. Checkpoint it so the main file is a complete backup.
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                val source = context.getDatabasePath(DATABASE_NAME)
                require(source.exists()) { "قاعدة البيانات المحلية غير موجودة" }
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة")
            }
        }

    suspend fun restore(context: Context, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val temporary = File.createTempFile("power-feul-restore-", ".db", context.cacheDir)
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                } ?: error("تعذر قراءة ملف النسخة الاحتياطية")
                require(isValidDatabase(temporary)) { "الملف المحدد ليس نسخة قاعدة بيانات صالحة لهذا التطبيق" }
                val target = context.getDatabasePath(DATABASE_NAME)
                target.parentFile?.mkdirs()

                // Stage the replacement before touching the live database.
                val staged = File(target.parentFile, "$DATABASE_NAME.restore")
                temporary.copyTo(staged, overwrite = true)
                AppDatabase.closeAndClearInstance()

                // Keep the current data recoverable if the user selected the wrong backup.
                if (target.exists()) {
                    target.copyTo(File(target.parentFile, "$DATABASE_NAME.pre-restore"), overwrite = true)
                }
                File("${target.path}-wal").delete()
                File("${target.path}-shm").delete()
                staged.copyTo(target, overwrite = true)
                staged.delete()
            } finally {
                temporary.delete()
            }
        }
    }

    private fun isValidDatabase(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val integrityOk = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val isPowerFeulBackup = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'users'", null
            ).use { it.moveToFirst() }
            integrityOk && isPowerFeulBackup
        }
    }.getOrDefault(false)
}
