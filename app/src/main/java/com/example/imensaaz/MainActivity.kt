package com.example.imensaaz

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 14, 17)
    private val panel = Color.rgb(18, 29, 32)
    private val green = Color.rgb(54, 220, 135)
    private val muted = Color.rgb(185, 198, 198)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var report: TextView
    private lateinit var scanButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(page)

        page.addView(label("ایمن‌ساز", 29, Color.WHITE, true))
        page.addView(label("محافظ سبک بررسی فایل‌ها", 14, muted, false).apply {
            setPadding(0, 0, 0, dp(20))
        })

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = rounded(panel, 22)
        }
        hero.addView(label("⬡", 58, green, true))
        hero.addView(label("محافظت فایل", 22, Color.WHITE, true))
        status = label("برای شروع، یک فایل انتخاب کن", 14, muted, false)
        hero.addView(status)
        page.addView(hero, fullWidth())

        scanButton = actionButton("انتخاب فایل برای بررسی")
        scanButton.setOnClickListener {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(picker, PICK_FILE)
        }
        val scanParams = fullWidth().apply { topMargin = dp(16) }
        page.addView(scanButton, scanParams)

        val reportCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel, 18)
        }
        reportCard.addView(label("گزارش آخرین بررسی", 18, Color.WHITE, true))
        report = label(
            "هنوز گزارشی ثبت نشده است.\n\nبا انتخاب فایل، نام، اندازه، نوع و اثرانگشت SHA-256 نمایش داده می‌شود.",
            13, muted, false
        )
        report.gravity = Gravity.START
        reportCard.addView(report)
        page.addView(reportCard, fullWidth().apply { topMargin = dp(14) })

        page.addView(label(
            "محدودیت مهم: این نسخه موتور ضدبدافزار یا پایگاه امضای ویروس ندارد. " +
            "هش SHA-256 به‌تنهایی سالم یا آلوده بودن فایل را مشخص نمی‌کند. " +
            "هیچ فایلی حذف یا تغییر داده نمی‌شود.",
            12, muted, false
        ).apply { setPadding(dp(4), dp(18), dp(4), 0) })

        setContentView(scroll)
    }

    @Deprecated("Deprecated Android callback retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        scanButton.isEnabled = false
        status.text = "در حال بررسی فایل…"
        report.text = "لطفاً صبر کن؛ فایل در پس‌زمینه خوانده می‌شود."
        worker.execute {
            val result = runCatching { inspect(uri) }
            runOnUiThread {
                scanButton.isEnabled = true
                result.onSuccess {
                    status.text = "بررسی اولیه کامل شد"
                    report.text = it
                }.onFailure {
                    status.text = "بررسی انجام نشد"
                    report.text = it.localizedMessage ?: "خطای ناشناخته هنگام خواندن فایل"
                }
            }
        }
    }

    private fun inspect(uri: Uri): String {
        val nameAndSize = queryFileInfo(uri)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "امکان باز کردن فایل وجود ندارد." }
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                total += count
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val displayName = nameAndSize.first
        val sizeText = formatBytes(if (nameAndSize.second >= 0) nameAndSize.second else total)
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val typeNote = if (ext == "apk") {
            "نوع فایل: بسته نصب اندروید (APK). این بررسی، کد برنامه را اجرا نمی‌کند."
        } else {
            "نوع فایل بر اساس پسوند: ${if (ext.isBlank()) "نامشخص" else ext}"
        }
        return "نام فایل: $displayName\n" +
            "اندازه: $sizeText\n" +
            "$typeNote\n\n" +
            "SHA-256:\n$hash\n\n" +
            "نتیجه امنیتی: نامشخص\n" +
            "این نسخه تطبیق هش با بانک بدافزار ندارد؛ بنابراین نمی‌تواند پاک بودن فایل را تأیید کند."
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "فایل انتخاب‌شده"
        var size = -1L
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) {
                if (nameIndex >= 0) name = it.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes بایت"
        bytes < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), dp(7))
        }

    private fun actionButton(value: String) = Button(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.BLACK)
        background = rounded(green, 16)
        isAllCaps = false
    }

    private fun rounded(color: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun fullWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object { private const val PICK_FILE = 42 }
}
