// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.regist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.MacAddress
import com.chiaking.tv.common.db.RegisteredHost
import com.metallic.chiaki.lib.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistExecuteViewModel(val database: AppDatabase): ViewModel()
{
	enum class State
	{
		IDLE,
		RUNNING,
		STOPPED,
		FAILED,
		SUCCESSFUL,
		SUCCESSFUL_DUPLICATE,
	}

	private val _state = MutableStateFlow<State>(State.IDLE)
	val state: StateFlow<State> get() = _state.asStateFlow()

	private val log = ChiakiRxLog(ChiakiLog.Level.ALL.value)
	private var regist: Regist? = null
	@Volatile private var closed = false

	val logText: StateFlow<String> get() = log.logText

	var host: RegistHost? = null
		private set

	private var assignManualHostId: Long? = null

	fun start(info: RegistInfo, assignManualHostId: Long?)
	{
		if(regist != null)
			return
		try
		{
			regist = Regist(info, log.log, this::registEvent)
			this.assignManualHostId = assignManualHostId
			_state.value = State.RUNNING
		}
		catch(error: CreateError)
		{
			log.log.e("Failed to create Regist: ${error.errorCode}")
			_state.value = State.FAILED
		}
	}

	fun stop()
	{
		regist?.stop()
	}

	private fun registEvent(event: RegistEvent)
	{
		if(closed) return
		when(event)
		{
			is RegistEventCanceled -> _state.value = State.STOPPED
			is RegistEventFailed -> _state.value = State.FAILED
			is RegistEventSuccess -> registSuccess(event.host)
		}
	}

	private fun registSuccess(host: RegistHost)
	{
		this.host = host
		viewModelScope.launch {
			val existing = withContext(Dispatchers.IO) {
				database.registeredHostDao().getByMac(MacAddress(host.serverMac))
			}
			if(existing != null)
				_state.value = State.SUCCESSFUL_DUPLICATE
			else
				saveHost()
		}
	}

	fun saveHost()
	{
		val host = host ?: return
		val assignManualHostId = assignManualHostId
		viewModelScope.launch(Dispatchers.IO) {
			val dao = database.registeredHostDao()
			val manualHostDao = database.manualHostDao()
			val registeredHost = RegisteredHost(host)
			dao.deleteByMac(registeredHost.serverMac)
			val registeredHostId = dao.insert(registeredHost)
			if(assignManualHostId != null)
				manualHostDao.assignRegisteredHost(assignManualHostId, registeredHostId)
			_state.value = State.SUCCESSFUL
		}
	}

	override fun onCleared()
	{
		closed = true
		super.onCleared()
		regist?.stop()
		regist?.dispose()
		regist = null
	}
}
