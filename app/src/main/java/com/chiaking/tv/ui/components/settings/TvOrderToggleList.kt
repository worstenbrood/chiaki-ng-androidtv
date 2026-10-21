// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import com.chiaking.tv.ui.components.OnOffChip
import com.chiaking.tv.ui.components.tvRow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.chiaking.tv.R
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsCyan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One row of a [TvOrderToggleList]: a stable [key], a display [label], its on/off state and whether it can be toggled. */
data class TvOrderEntry<T>(val key: T, val label: String, val enabled: Boolean, val toggleable: Boolean = true)

private const val ORDER_MOVE_HOLD_MS = 550L

/**
 * Editable, ordered on/off list for TV/D-pad.
 *
 * Each row toggles its value with Cross. Hold Cross (or press Menu) to enter move mode,
 * then Up/Down reorders the focused row; Cross/Menu/Back exits move mode. Disabled rows
 * stay in place, dimmed. [onToggle] and [onMove] receive the row index and delta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvOrderToggleList(
	entries: List<TvOrderEntry<T>>,
	onToggle: (Int) -> Unit,
	onMove: (Int, Int) -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {},
	summary: String? = null
)
{
	var moveKey by remember { mutableStateOf<Any?>(null) }
	var pendingFocusKey by remember { mutableStateOf<Any?>(null) }
	val requesters = remember { mutableStateMapOf<Any, FocusRequester>() }
	val bringers = remember { mutableStateMapOf<Any, BringIntoViewRequester>() }

	LaunchedEffect(pendingFocusKey, entries)
	{
		val key = pendingFocusKey ?: return@LaunchedEffect
		val idx = entries.indexOfFirst { it.key == key }
		val req = if(idx == 0 && focusRequester != null) focusRequester else requesters[key]
		try { req?.requestFocus() } catch(e: Exception) { }
		bringers[key]?.bringIntoView()
		pendingFocusKey = null
	}

	if(summary != null)
		Text(
			summary,
			style = MaterialTheme.typography.bodySmall,
			color = OnSurfaceMuted,
			modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 4.dp)
		)

	entries.forEachIndexed { index, entry ->
		key(entry.key)
		{
			val req = if(index == 0 && focusRequester != null) focusRequester
				else requesters.getOrPut(entry.key as Any) { FocusRequester() }
			TvOrderRow(
				entry = entry,
				moveMode = moveKey == entry.key,
				focusRequester = req,
				bringer = bringers.getOrPut(entry.key as Any) { BringIntoViewRequester() },
				onToggle = { onToggle(index) },
				onEnterMove = { moveKey = entry.key },
				onExitMove = { if(moveKey == entry.key) moveKey = null },
				onMove = { delta ->
					if(index + delta in entries.indices)
					{
						pendingFocusKey = entry.key
						onMove(index, delta)
					}
				},
				onFocus = { focused ->
					if(focused) onFocus(true)
					else if(moveKey == entry.key) moveKey = null
				}
			)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun <T> TvOrderRow(
	entry: TvOrderEntry<T>,
	moveMode: Boolean,
	focusRequester: FocusRequester?,
	bringer: BringIntoViewRequester,
	onToggle: () -> Unit,
	onEnterMove: () -> Unit,
	onExitMove: () -> Unit,
	onMove: (Int) -> Unit,
	onFocus: (Boolean) -> Unit
)
{
	val focused = remember { mutableStateOf(false) }
	val scope = rememberCoroutineScope()
	var holdJob by remember { mutableStateOf<Job?>(null) }
	var holdFired by remember { mutableStateOf(false) }

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.bringIntoViewRequester(bringer)
			.onPreviewKeyEvent { ev ->
				val down = ev.type == KeyEventType.KeyDown
				val up = ev.type == KeyEventType.KeyUp
				val select = ev.key == Key.ButtonA || ev.key == Key.DirectionCenter || ev.key == Key.Enter
				when {
					moveMode && down && (ev.key == Key.DirectionUp || ev.key == Key.DirectionDown) ->
						{ onMove(if(ev.key == Key.DirectionUp) -1 else 1); true }
					moveMode && ev.key == Key.Back ->
						{ if(up) onExitMove(); true }
					moveMode && select ->
						{
							if(up)
							{
								if(holdFired) { holdFired = false; true }
								else { onExitMove(); true }
							}
							else true
						}
					moveMode && down && ev.key == Key.Menu ->
						{ onExitMove(); true }
					!moveMode && down && ev.key == Key.Menu ->
						{ onEnterMove(); true }
					!moveMode && down && select && ev.nativeKeyEvent.repeatCount == 0 ->
						{
							holdFired = false
							holdJob?.cancel()
							holdJob = scope.launch {
								delay(ORDER_MOVE_HOLD_MS)
								holdFired = true
								onEnterMove()
							}
							false
						}
					!moveMode && up && select ->
						{
							holdJob?.cancel()
							if(holdFired) { holdFired = false; true } else false
						}
					else -> false
				}
			}
			.tvRow(focused, if(entry.toggleable) onToggle else onEnterMove, focusRequester, onFocus),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			entry.label,
			style = MaterialTheme.typography.titleLarge,
			color = if(entry.enabled) OnSurfaceLight else OnSurfaceMuted,
			modifier = Modifier.weight(1f)
		)
		Spacer(Modifier.width(12.dp))
		if(moveMode)
		{
			Icon(Icons.Default.KeyboardArrowUp, null, tint = PsCyan, modifier = Modifier.size(20.dp))
			Text(
				stringResource(R.string.move),
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Bold,
				color = PsCyan,
				modifier = Modifier.padding(horizontal = 4.dp)
			)
			Icon(Icons.Default.KeyboardArrowDown, null, tint = PsCyan, modifier = Modifier.size(20.dp))
		}
		else if(!entry.toggleable)
		{
			Icon(
				Icons.Default.Menu,
				null,
				tint = if(focused.value) OnSurfaceLight else OnSurfaceMuted,
				modifier = Modifier.size(20.dp)
			)
		}
		else
		{
			OnOffChip(entry.enabled, focused.value)
		}
	}
}
