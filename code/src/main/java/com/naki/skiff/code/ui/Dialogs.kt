package com.naki.skiff.code.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.naki.skiff.code.R
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.fs.sftp.HostKeyDecision
import com.naki.skiff.fs.sftp.HostKeyPrompt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The native dialogs the open flow needs before there is a page to show anything in. They stay
 * native even once the page exists: a password field and a host key decision should not pass
 * through a WebView that renders remote content.
 */

/**
 * Shows the dialog [build] makes and waits for [finish], or [dismissed] if it goes away unanswered.
 * [required] fields keep the positive button disabled while any of them is empty.
 */
private suspend fun <T> Activity.await(
    dismissed: T,
    required: List<EditText> = emptyList(),
    build: AlertDialog.Builder.(finish: (T) -> Unit) -> Unit,
): T =
    suspendCancellableCoroutine { cont ->
        var answered = false
        val finish: (T) -> Unit = { value ->
            if (!answered) {
                answered = true
                cont.resume(value)
            }
        }
        val dialog = AlertDialog.Builder(this).apply { build(finish) }.show()
        if (required.isNotEmpty()) {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val update = { positive.isEnabled = required.all { it.text.isNotEmpty() } }
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = update()
            }
            required.forEach { it.addTextChangedListener(watcher) }
            update()
        }
        // A button's listener runs before the dismissal, so this only fires for back or outside taps.
        dialog.setOnDismissListener { finish(dismissed) }
        cont.invokeOnCancellation { dialog.dismiss() }
    }

suspend fun Activity.showError(title: String, message: String) {
    await(Unit) { finish ->
        setTitle(title)
        setMessage(message)
        setPositiveButton(android.R.string.ok) { _, _ -> finish(Unit) }
    }
}

/** Yes/no with a positive action label. */
suspend fun Activity.confirm(title: String, message: String, action: String): Boolean =
    await(false) { finish ->
        setTitle(title)
        setMessage(message)
        setPositiveButton(action) { _, _ -> finish(true) }
        setNegativeButton(android.R.string.cancel) { _, _ -> finish(false) }
    }

data class UnknownServerAnswer(val user: String, val password: String, val saveAsProfile: Boolean)

/** Shows the address the link points at, and asks what connecting to it needs. Null is "don't open". */
suspend fun Activity.confirmUnknownServer(request: OpenRequest.UnknownServer): UnknownServerAnswer? {
    val form = Form(this)
    form.text(getString(R.string.unknown_server_body, "${request.host}:${request.port}", request.path))
    val user = form.field(getString(R.string.field_user), request.user.orEmpty(), password = false)
    // A user named by the link is shown but not editable: changing it is opening a different link.
    if (request.user != null) user.isEnabled = false
    val password = form.field(getString(R.string.field_password), "", password = true)
    val save = form.checkbox(getString(R.string.save_as_profile), checked = true)

    return await(null, required = listOf(user, password)) { finish ->
        setTitle(R.string.unknown_server_title)
        setView(form.root)
        setPositiveButton(R.string.action_connect) { _, _ ->
            finish(UnknownServerAnswer(user.text.toString().trim(), password.text.toString(), save.isChecked))
        }
        setNegativeButton(android.R.string.cancel) { _, _ -> finish(null) }
    }
}

/** Null is "cancel". [retry] says the last one was refused. */
suspend fun Activity.askPassword(target: String, retry: Boolean): String? {
    val form = Form(this)
    form.text(getString(if (retry) R.string.password_wrong_body else R.string.password_body, target))
    val password = form.field(getString(R.string.field_password), "", password = true)
    return await(null, required = listOf(password)) { finish ->
        setTitle(R.string.password_title)
        setView(form.root)
        setPositiveButton(R.string.action_connect) { _, _ -> finish(password.text.toString()) }
        setNegativeButton(android.R.string.cancel) { _, _ -> finish(null) }
    }
}

/**
 * Trust on first use. A changed key gets the louder title and wording, because that is the case
 * where clicking through costs something. Not suspending: the prompter's state drives it, so the
 * caller keeps the dialog to dismiss it if the question goes away.
 */
fun Activity.hostKeyDialog(prompt: HostKeyPrompt, answer: (HostKeyDecision) -> Unit): AlertDialog {
    val changed = prompt.previousFingerprint != null
    val target = "${prompt.host}:${prompt.port}"
    val form = Form(this)
    form.text(getString(if (changed) R.string.hostkey_body_changed else R.string.hostkey_body_new, target))
    form.text("${prompt.keyType}\n${prompt.fingerprint}", selectable = true)
    prompt.previousFingerprint?.let { form.text(getString(R.string.hostkey_previous) + "\n" + it, selectable = true) }

    var answered = false
    val dialog = AlertDialog.Builder(this)
        .setTitle(if (changed) R.string.hostkey_title_changed else R.string.hostkey_title_new)
        .setView(form.root)
        .setPositiveButton(R.string.action_trust) { _, _ -> answered = true; answer(HostKeyDecision.Accept) }
        .setNegativeButton(android.R.string.cancel) { _, _ -> answered = true; answer(HostKeyDecision.Reject) }
        .create()
    dialog.setOnDismissListener { if (!answered) answer(HostKeyDecision.Reject) }
    if (changed) {
        dialog.setOnShowListener {
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(Color.rgb(0xE5, 0x48, 0x4D))
        }
    }
    return dialog
}

/** A padded vertical column of views for a dialog body. */
private class Form(private val activity: Activity) {
    private val pad = (20 * activity.resources.displayMetrics.density).toInt()

    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
    }

    fun text(value: String, selectable: Boolean = false) {
        root.addView(TextView(activity).apply {
            text = value
            setTextIsSelectable(selectable)
            setPadding(0, 0, 0, pad / 2)
        })
    }

    fun field(hint: String, value: String, password: Boolean): EditText = EditText(activity).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.addView(this)
    }

    fun checkbox(label: String, checked: Boolean): CheckBox = CheckBox(activity).apply {
        text = label
        isChecked = checked
        // The theme's accent is a dark grey on the dark dialog, so a checked box could not be told
        // from an unchecked one. The label's colour is the theme's contrast to the background.
        buttonTintList = textColors
        root.addView(this)
    }
}
