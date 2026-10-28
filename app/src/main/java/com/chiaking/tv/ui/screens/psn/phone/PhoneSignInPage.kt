package com.chiaking.tv.ui.screens.psn.phone

import com.chiaking.tv.common.psn.PhoneSignInStatus
import com.chiaking.tv.ui.theme.PHONE_SIGN_IN_CSS

internal data class PhoneSignInText(
    val title: String, val instructions: String, val signIn: String, val paste: String,
    val send: String, val working: String, val success: String, val failed: String,
    val invalid: String, val retry: String, val language: String = "en",
    val signInStep: String = "Sign in to PlayStation",
    val pasteStep: String = "Paste the redirect URL",
    val pasteInstructions: String = "Copy the full URL from the address bar after signing in. Return here and paste it below.",
    val notice: String = "Keep this page open. Use the same trusted network as your TV.",
)

/** No external assets, scripts, cookies or credentials; Sony always opens in its own browser tab. */
internal object PhoneSignInPage {
    fun render(text: PhoneSignInText, path: String, loginUrl: String, status: PhoneSignInStatus,
        invalid: Boolean = false): String {
        val content = when(status) {
            PhoneSignInStatus.WAITING -> """
                <ol class="steps">
                  <li><h2>${escape(text.signInStep)}</h2>
                    <p>${escape(text.instructions)}</p>
                    <a class="button" href="${escape(loginUrl)}" target="_blank" rel="noopener noreferrer">${escape(text.signIn)}</a>
                  </li>
                  <li><h2>${escape(text.pasteStep)}</h2>
                    <p id="redirect-help">${escape(text.pasteInstructions)}</p>
                    <form method="post" action="${escape(path)}submit">
                  <label for="redirect">${escape(text.paste)}</label>
                  <textarea id="redirect" name="redirect" rows="3" maxlength="8192" required aria-describedby="redirect-help" autocapitalize="off" autocomplete="off" spellcheck="false"></textarea>
                  ${if(invalid) "<p role=\"alert\">${escape(text.invalid)}</p>" else ""}
                  <button type="submit">${escape(text.send)}</button>
                    </form>
                  </li>
                </ol>
                <p class="notice">${escape(text.notice)}</p>
            """.trimIndent()
            PhoneSignInStatus.PROCESSING -> "<p role=\"status\">${escape(text.working)}</p>"
            PhoneSignInStatus.SUCCESS -> "<p role=\"status\">${escape(text.success)}</p>"
            PhoneSignInStatus.FAILED -> "<p role=\"alert\">${escape(text.failed)}</p><a href=\"${escape(path)}\">${escape(text.retry)}</a>"
        }
        return """<!doctype html><html lang="${escape(text.language)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="same-origin">${if(status == PhoneSignInStatus.PROCESSING) "<meta http-equiv=\"refresh\" content=\"2\">" else ""}
            <title>${escape(text.title)}</title><style>
            ${PHONE_SIGN_IN_CSS}
            </style></head><body><main><h1>${escape(text.title)}</h1>$content</main></body></html>""".trimIndent()
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
