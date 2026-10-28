package com.chiaking.tv.ui.screens.psn

import android.content.Context
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PhoneSignInServer
import com.chiaking.tv.common.psn.PhoneSignInStatus
import com.chiaking.tv.ui.screens.psn.phone.PhoneSignInPage
import com.chiaking.tv.ui.screens.psn.phone.PhoneSignInText
import com.chiaking.tv.common.psn.PsnAccountError
import com.chiaking.tv.common.psn.PsnAccountException
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.common.psn.PsnAuth
import com.chiaking.tv.common.psn.PsnLoginAttempt
import com.chiaking.tv.common.psn.PsnLoginException
import com.chiaking.tv.common.psn.PsnLoginPhase
import com.chiaking.tv.platform.network.localNetworkAddress
import com.metallic.chiaki.lib.Holepunch
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PsnLoginState(
    val loginUrl: String? = null,
    val phoneUrl: String? = null,
    val phoneUnavailable: Boolean = false,
    val busy: Boolean = false,
    val phase: PsnLoginPhase? = null,
    val error: Int? = null,
    val success: String? = null,
)

/** Dialog-owned coordinator: both phone and TV input share one attempt and one login worker. */
internal class PsnLoginController(context: Context, scope: CoroutineScope) : Closeable {
    private val appContext = context.applicationContext
    private val accounts = PsnAccountRepository.get(appContext)
    private val mutableState = MutableStateFlow(PsnLoginState())
    val state = mutableState.asStateFlow()
    private val codes = Channel<String>(1)
    @Volatile private var attempt: PsnLoginAttempt? = null
    @Volatile private var server: PhoneSignInServer? = null
    @Volatile private var closed = false
    private val prepareJob: Job
    private val loginJob: Job

    init {
        prepareJob = scope.launch {
            try {
                val current = withContext(Dispatchers.IO) {
                    val uid = Holepunch.generateClientDeviceUid()
                    if(uid.isBlank()) throw PsnLoginException(PsnLoginPhase.GENERATING_UID)
                    PsnLoginAttempt(uid)
                }
                attempt = current
                mutableState.update { it.copy(loginUrl = current.loginUrl) }
                val text = with(appContext) { PhoneSignInText(
                    getString(R.string.psn_login_title), getString(R.string.psn_phone_page_instructions),
                    getString(R.string.psn_sign_in), getString(R.string.psn_phone_paste),
                    getString(R.string.psn_phone_send), getString(R.string.psn_working),
                    getString(R.string.psn_phone_success), getString(R.string.psn_phone_failed),
                    getString(R.string.psn_phone_invalid), getString(R.string.psn_phone_retry),
                    resources.configuration.locales[0].language,
                ) }
                try {
                    withContext(Dispatchers.IO) {
                        val address = localNetworkAddress(appContext) ?: error("No LAN address")
                        val local = PhoneSignInServer(address, current,
                            { status, invalid, path -> PhoneSignInPage.render(text, path, current.loginUrl, status, invalid) },
                            ::submit)
                        server = local
                        // Disposal can happen while socket creation is still in progress.
                        if(closed) { local.close(); return@withContext }
                        mutableState.update { it.copy(phoneUrl = local.url) }
                        local.serve()
                    }
                    if(!closed) mutableState.update { it.copy(phoneUrl = null, phoneUnavailable = true) }
                } catch(e: CancellationException) { throw e }
                catch(_: Exception) { mutableState.update { it.copy(phoneUrl = null, phoneUnavailable = true) } }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { mutableState.update { it.copy(error = R.string.psn_uid_failed) } }
        }
        loginJob = scope.launch {
            for(code in codes) {
                if(closed) break
                try {
                    val current = attempt ?: continue
                    val id = accounts.loginWithCode(code, current.deviceUid) { phase ->
                        mutableState.update { it.copy(phase = phase) }
                    }
                    server?.status?.set(PhoneSignInStatus.SUCCESS)
                    mutableState.update { it.copy(success = id) }
                } catch(e: CancellationException) { throw e }
                catch(e: PsnAccountException) {
                    fail(if(e.reason == PsnAccountError.LIMIT_REACHED) R.string.psn_account_limit else R.string.psn_account_locked)
                } catch(_: Exception) { fail(R.string.psn_signin_failed) }
                finally { mutableState.update { it.copy(busy = false, phase = null) } }
            }
        }
    }

    fun submitManual(input: String) {
        val code = if(input.trim().startsWith("https://")) attempt?.phoneCode(input) else PsnAuth.parseRedirect(input)
        if(code == null) mutableState.update { it.copy(error = R.string.psn_bad_code) }
        else submit(code)
    }

    private fun submit(code: String): Boolean {
        if(closed || attempt == null) return false
        while(true) {
            val previous = mutableState.value
            if(previous.busy || previous.success != null) return false
            if(mutableState.compareAndSet(previous, previous.copy(busy = true, error = null))) break
        }
        server?.status?.set(PhoneSignInStatus.PROCESSING)
        if(codes.trySend(code).isSuccess) return true
        mutableState.update { it.copy(busy = false) }
        return false
    }

    private fun fail(message: Int) {
        server?.status?.set(PhoneSignInStatus.FAILED)
        mutableState.update { it.copy(error = message) }
    }

    override fun close() {
        closed = true
        server?.close()
        prepareJob.cancel()
        loginJob.cancel()
        codes.close()
    }
}
