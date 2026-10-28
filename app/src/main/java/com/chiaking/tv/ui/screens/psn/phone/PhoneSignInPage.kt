package com.chiaking.tv.ui.screens.psn.phone

import com.chiaking.tv.common.psn.PhoneSignInStatus
import com.chiaking.tv.ui.theme.PHONE_SIGN_IN_CSS

internal data class PhoneSignInText(
    val title: String, val instructions: String, val signIn: String, val paste: String,
    val send: String, val working: String, val success: String, val failed: String,
    val invalid: String, val retry: String, val language: String = "en",
)

/** No external assets, scripts, cookies or credentials; Sony always opens in its own browser tab. */
internal object PhoneSignInPage {
    fun render(text: PhoneSignInText, path: String, loginUrl: String, status: PhoneSignInStatus,
        invalid: Boolean = false): String {
        val content = when(status) {
            PhoneSignInStatus.WAITING -> """
                <p>${escape(text.instructions)}</p>
                <a class="button" href="${escape(loginUrl)}" target="_blank" rel="noopener noreferrer">${escape(text.signIn)}</a>
                <form method="post" action="${escape(path)}submit">
                  <label for="redirect">${escape(text.paste)}</label>
                  <textarea id="redirect" name="redirect" rows="5" maxlength="8192" required autocapitalize="off" autocomplete="off" spellcheck="false"></textarea>
                  ${if(invalid) "<p role=\"alert\">${escape(text.invalid)}</p>" else ""}
                  <button type="submit">${escape(text.send)}</button>
                </form>
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
