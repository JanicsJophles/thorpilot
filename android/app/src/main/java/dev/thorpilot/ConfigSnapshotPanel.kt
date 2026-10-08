package dev.thorpilot

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.util.concurrent.Executors

/** Local-only document workflow. Neither file contents nor paths leave the device. */
class ConfigSnapshotPanel(context: Context, private val onPickDocument: () -> Unit) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val store by lazy { ConfigSnapshotStore(java.io.File(app.filesDir, "config-snapshots")) }
    private val names = app.getSharedPreferences("config-snapshot-names", Context.MODE_PRIVATE)
    private var backups = emptyList<ConfigSnapshot>()
    private var busy = false
    private var root: LinearLayout? = null
    private var activeDialog: AlertDialog? = null
    private val preferences = app.getSharedPreferences("config-snapshot-selection", Context.MODE_PRIVATE)
    private var selected: AzaharConfigDocument? = restoreSelection()
    private var message = "Choose config.ini from the folder you configured in Azahar. A copied file can be backed up, but restoring it will not change Azahar."
    private var snapshotName = preferences.getString("backup-name", "Before graphics experiment").orEmpty().take(80)
    private fun restoreSelection(): AzaharConfigDocument? {
        val uri = preferences.getString("document-uri", null)?.let(Uri::parse) ?: return null
        val readable = runCatching { app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } }.getOrDefault(false)
        if (!readable) { preferences.edit().remove("document-uri").apply(); return null }
        return AzaharConfigDocument(app, uri)
    }
    private fun location(uri: Uri): String = runCatching {
        "${uri.authority.orEmpty()} / ${android.provider.DocumentsContract.getDocumentId(uri)}"
    }.getOrElse { uri.toString() }
    private fun version(): String = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo("org.azahar_emu.azahar", 0).versionName.orEmpty()
    }.getOrDefault("")
    private fun supported() = version() in setOf("2126.0", "2126.0-vanilla")
    fun createView(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; root = this; render(); if (!busy) task { if (selected == null) "Backups loaded. Choose config.ini to capture or compare." else "Selected config restored. Capture or compare when Azahar is closed." }
    }
    fun onDocumentSelected(uri: Uri) {
        if (busy || closed) return
        selected = AzaharConfigDocument(app, uri)
        preferences.edit().putString("document-uri", uri.toString()).apply()
        message = "Selected config.ini. Its contents and location will be checked when you save or compare."
        render()
    }
    fun acceptDocument(uri: Uri) = onDocumentSelected(uri)
    fun close() { closed = true; activeDialog?.dismiss(); activeDialog = null; root = null; worker.shutdown() }
    private fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    private fun label(parent: LinearLayout, text: String, size: Float = 13f, strong: Boolean = false) {
        parent.addView(TextView(parent.context).apply {
            this.text = text; textSize = size; setTextColor(if (strong) 0xfff4f2ec.toInt() else 0xffa9a69e.toInt())
            if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(context, 5), 0, dp(context, 9))
        })
    }
    private fun button(parent: LinearLayout, title: String, enabled: Boolean = true, action: () -> Unit) {
        parent.addView(Button(parent.context).apply {
            text = title; textSize = 14f; isAllCaps = false; setTextColor(0xffffb547.toInt())
            background = PilotGlass(dp(context, 16).toFloat()); isEnabled = enabled && !busy
            alpha = if (isEnabled) 1f else .5f; setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(parent.context, 48)).apply { topMargin = dp(parent.context, 8) })
    }
    private fun task(onComplete: () -> Unit = {}, action: () -> String) {
        if (closed || busy) return
        busy = true; message = "Working locally…"; render()
        worker.execute {
            val outcome = runCatching(action)
            val result = outcome.getOrElse { it.message ?: "The operation failed. The selected document may need to be chosen again." }
            val loaded = runCatching { store.snapshots() }
            main.post { if (!closed) { loaded.onSuccess { backups = it }; busy = false; message = if (loaded.isFailure) "$result\nBackup list could not be read: ${loaded.exceptionOrNull()?.message}" else result; render(); if (outcome.isSuccess) onComplete() } }
        }
    }
    private fun render() {
        val host = root ?: return
        host.removeAllViews()
        label(host, "A way back", 24f, true)
        label(host, "Keep your working settings before trying something new.")
        label(host, "Azahar ${version().ifBlank { "not detected" }} · Local backups only", 12f)
        if (!supported()) label(host, "Restore is available only for the reviewed Azahar 2126.0 / 2126.0-vanilla build. Other versions can still be backed up.", 12f)
        val card = LinearLayout(host.context).apply {
            orientation = LinearLayout.VERTICAL; background = PilotGlass(dp(context, 20).toFloat())
            setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 16))
        }
        host.addView(card, LinearLayout.LayoutParams(-1, -2))
        label(card, if (selected == null) "Select your config" else "Selected config", 17f, true)
        selected?.let { label(card, location(it.uri), 12f) }
        label(card, "Close Azahar before capturing or restoring. This file contains global settings for every game. Thorpilot cannot confirm which file Azahar uses or whether it is closed.", 12f)
        button(card, if (selected == null) "Choose config.ini" else "Choose a different config.ini") { onPickDocument() }
        label(card, "Backup name", 13f, true)
        card.addView(EditText(host.context).apply {
            setText(snapshotName); contentDescription = "Backup name"; textSize = 14f
            setTextColor(0xfff4f2ec.toInt()); isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(80))
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { snapshotName = s.toString().take(80); preferences.edit().putString("backup-name", snapshotName).apply() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }); isEnabled = !busy
        })
        button(card, "Save exact backup", selected != null) { capture() }
        label(card, message, 13f)
        label(host, "Your backups", 18f, true)
        renderBackups(host)
    }
    // Store integration below.
    private fun capture() {
        val document = selected ?: return
        val title = snapshotName.trim().ifBlank { "Configuration backup" }.take(80)
        val currentVersion = version()
        task {
            val copy = store.backup(document.uri.toString(), currentVersion) { document.validatedRead().inputStream() }
            check(names.edit().putString(copy.id, title).commit()) { "Backup saved, but its name could not be saved." }
            "Saved exact backup: ${copy.sizeBytes} bytes. SHA-256 ${copy.sha256.take(12)}… No settings changed."
        }
    }
    private fun renderBackups(host: LinearLayout) {
        if (backups.isEmpty()) label(host, "Save a working configuration before your first graphics experiment. Copies stay in Thorpilot's private storage.", 12f)
        else label(host, "${backups.size} of 20 copies. Recovery copies are kept until you explicitly remove them.", 12f)
        backups.forEach { copy ->
            val title = names.getString(copy.id, null) ?: if (copy.recoveryOf != null) "Before restore · recovery" else "Configuration backup"
            label(host, title, 16f, true)
            label(host, "${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(copy.createdAt))} · Azahar ${copy.emulatorVersion.ifBlank { "unknown" }} · ${copy.sizeBytes} bytes", 12f)
            button(host, "Compare / restore · ${copy.sha256.take(8)}", selected != null) { compare(copy) }
            button(host, "Remove backup") {
                AlertDialog.Builder(host.context).setTitle("Remove this local backup?")
                    .setMessage("$title\nThe selected config.ini will not be changed.")
                    .setNegativeButton("Keep", null).setPositiveButton("Remove") { _, _ -> task {
                        store.delete(copy.id); names.edit().remove(copy.id).commit(); "Backup removed."
                    } }.show()
            }
        }
    }
    private fun compare(copy: ConfigSnapshot) {
        val document = selected ?: return
        var preview: ConfigRestorePreview? = null
        task(onComplete = { preview?.let { showPreview(document, it) } }) {
            val compared = store.preview(copy.id, document.uri.toString()) { document.read().inputStream() }
            preview = compared
            if (compared.sameBytes) "The selected config already matches this backup." else "Comparison ready. Review before restoring."
        }
    }
    private fun hasWriteGrant(uri: Uri): Boolean = runCatching {
        app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }.getOrDefault(false)
    private fun showPreview(document: AzaharConfigDocument, preview: ConfigRestorePreview) {
        val c = root?.context ?: return
        val currentVersion = version()
        val versionAllowed = supported() && preview.snapshot.emulatorVersion == currentVersion
        val writable = hasWriteGrant(document.uri)
        val allowed = versionAllowed && writable
        val content = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 22), dp(c, 8), dp(c, 22), 0) }
        label(content, "Selected location: ${location(document.uri)}", 12f)
        label(content, "Backup: Azahar ${preview.snapshot.emulatorVersion.ifBlank { "unknown" }}\nInstalled: ${currentVersion.ifBlank { "not detected" }}", 13f, true)
        label(content, "Selected file: ${preview.currentSizeBytes} bytes\nSHA-256 ${preview.currentSha256}\n\nBackup: ${preview.snapshot.sizeBytes} bytes\nSHA-256 ${preview.snapshot.sha256}", 11f)
        if (preview.sameBytes) label(content, "Exact match. No restore is needed.")
        else label(content, "Restores the entire selected file, including global settings for every game. A recovery copy is saved first. External writes are not atomic; successful readback verifies only the bytes at that moment.", 12f)
        if (!writable) label(content, "This document has read-only access. Choose config.ini again and grant write access before restoring. Backups and comparisons remain available.", 12f)
        if (!versionAllowed) label(content, "Restore requires matching, reviewed Azahar 2126.0 or 2126.0-vanilla versions. You can still keep this backup.", 12f)
        val stopped = CheckBox(c).apply { text = "I closed Azahar completely"; textSize = 13f }
        val correct = CheckBox(c).apply { text = "This is Azahar's active config.ini; I understand this replaces global settings"; textSize = 13f }
        if (!preview.sameBytes && allowed) { content.addView(stopped); content.addView(correct) }
        val scroll = ScrollView(c).apply { addView(content) }
        val builder = AlertDialog.Builder(c).setTitle("Compare selected config").setView(scroll).setNegativeButton("Cancel", null)
        if (!preview.sameBytes && allowed) builder.setPositiveButton("Restore backup", null)
        val dialog = builder.create()
        activeDialog?.dismiss(); activeDialog = dialog
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        dialog.setOnShowListener {
            if (!preview.sameBytes && allowed) {
                val restore = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                fun update() { restore.isEnabled = stopped.isChecked && correct.isChecked && !busy }
                update(); stopped.setOnCheckedChangeListener { _, _ -> update() }; correct.setOnCheckedChangeListener { _, _ -> update() }
                restore.setOnClickListener {
                    dialog.dismiss()
                    task {
                        check(hasWriteGrant(document.uri)) { "Write access is no longer available. Choose config.ini again before restoring." }
                        check(version() == currentVersion && supported()) { "Azahar version changed. Review a new comparison." }
                        val result = store.restore(preview, { document.read().inputStream() }, { document.write(it) })
                        if (result.changed) "Restore readback verified. SHA-256 ${result.verifiedSha256.take(12)}… Recovery copy retained. Reopen Azahar to test."
                        else "Already an exact match. Nothing was written."
                    }
                }
            }
        }
        dialog.show()
    }
}
