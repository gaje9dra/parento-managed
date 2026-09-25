package com.parento.managed.enrollment

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.parento.managed.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnrollmentScreen(
    private val context: Context,
    private val onStart: (String, String, Long, String) -> Unit,
    private val onEnroll: (String) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.resources.getDimensionPixelSize(R.dimen.screen_padding))
    }

    private val title = MaterialTextView(context).apply {
        text = context.getString(R.string.enrollment_title)
        textSize = 24f
    }
    private val description = MaterialTextView(context).apply {
        text = context.getString(R.string.enrollment_description)
        textSize = 15f
    }

    private val enrollmentId = field(R.string.enrollment_id_label)
    private val secret = field(R.string.enrollment_secret_label, secret = true)
    private val expiry = field(R.string.enrollment_expiry_label)
    private val deviceName = field(R.string.device_name_label)

    private val startButton = MaterialButton(context).apply {
        text = context.getString(R.string.start_enrollment)
        setOnClickListener { submitStart() }
    }
    private val enrollButton = MaterialButton(context).apply {
        text = context.getString(R.string.complete_enrollment)
        setOnClickListener { onEnroll(deviceName.edit.text.toString().trim()) }
    }
    private val cancelButton = MaterialButton(context).apply {
        text = context.getString(R.string.cancel_enrollment)
        setOnClickListener { onCancel() }
    }
    private val status = MaterialTextView(context).apply { textSize = 15f }

    init {
        root.addView(title)
        root.addView(description)
        root.addView(enrollmentId.layout)
        root.addView(secret.layout)
        root.addView(expiry.layout)
        root.addView(deviceName.layout)
        root.addView(startButton)
        root.addView(enrollButton)
        root.addView(cancelButton)
        root.addView(status)
        setPendingVisible(false)
    }

    fun view(): View = root

    fun setPending(pending: PendingEnrollment?) {
        if (pending == null) {
            setPendingVisible(false)
            status.text = context.getString(R.string.enrollment_not_started)
            return
        }
        enrollmentId.edit.setText(pending.enrollmentId)
        secret.edit.setText(pending.authorizationSecret)
        expiry.edit.setText(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .format(Date(pending.expiresAtEpochMillis)),
        )
        setPendingVisible(true)
        status.text = context.getString(R.string.enrollment_authorization_saved)
    }

    fun setBusy(busy: Boolean) {
        startButton.isEnabled = !busy
        enrollButton.isEnabled = !busy
        cancelButton.isEnabled = !busy
        enrollmentId.edit.isEnabled = !busy
        secret.edit.isEnabled = !busy
        expiry.edit.isEnabled = !busy
        deviceName.edit.isEnabled = !busy
    }

    fun setMessage(message: String?) {
        if (message != null) status.text = message
    }

    private fun submitStart() {
        val id = enrollmentId.edit.text.toString().trim()
        val token = secret.edit.text.toString().trim()
        val expires = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .parse(expiry.edit.text.toString().trim())?.time
        }.getOrNull()
        if (id.isBlank() || token.length != 43 || expires == null) {
            status.text = context.getString(R.string.enrollment_input_invalid)
            return
        }
        if (deviceName.edit.text.toString().trim().isBlank()) {
            status.text = context.getString(R.string.device_name_required)
            return
        }
        onStart(id, token, expires, deviceName.edit.text.toString().trim())
    }

    private fun setPendingVisible(pending: Boolean) {
        startButton.visibility = if (pending) View.GONE else View.VISIBLE
        enrollButton.visibility = if (pending) View.VISIBLE else View.GONE
        cancelButton.visibility = if (pending) View.VISIBLE else View.GONE
    }

    private fun field(labelRes: Int, secret: Boolean = false): Field {
        val layout = TextInputLayout(context).apply {
            hint = context.getString(labelRes)
        }
        val edit = TextInputEditText(context).apply {
            inputType = if (secret) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        layout.addView(edit)
        return Field(layout, edit)
    }

    private data class Field(
        val layout: TextInputLayout,
        val edit: TextInputEditText,
    )

}
