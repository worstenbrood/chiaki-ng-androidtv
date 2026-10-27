package com.chiaking.tv.ui.screens.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.session.CoopAccountChoice
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.ui.components.TvFocusCard
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay

/** A Compose overlay keeps the existing video surface and Remote Play session attached. */
@Composable
fun CoopAccountPicker(choice: CoopAccountChoice, session: StreamSession) {
    BackHandler { session.cancelCoopAccountChoice(choice.id) }
    val focus = remember(choice.id) { List(choice.accounts.size + 1) { FocusRequester() } }
    LaunchedEffect(choice.id, choice.selectedIndex) {
        // Retry after composition has attached the new cards, just like the stream HUD.
        repeat(5) {
            try {
                focus.getOrNull(choice.selectedIndex)?.requestFocus()
                return@LaunchedEffect
            } catch(_: IllegalStateException) { delay(50) }
        }
    }
    Box(Modifier.fillMaxSize().background(VideoBlack.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.coop_choose_account, choice.slot + 1),
                style = MaterialTheme.typography.headlineSmall, color = OnSurfaceLight)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.coop_choose_account_hint),
                style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            if(choice.accounts.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.coop_account_required, choice.slot + 1),
                    style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 36.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                choice.accounts.forEachIndexed { index, account ->
                    key(account.rid) {
                        TvFocusCard(
                            focusRequester = focus[index],
                            containerColor = if(account.available) SurfaceCard else SurfaceFrosted,
                            onFocusChange = { if(it) session.focusCoopAccount(choice.id, index) },
                            onClick = if(account.available) ({ session.chooseCoopAccount(choice.id, account.rid) }) else null
                        ) {
                            Column(Modifier.width(200.dp).height(210.dp).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Person, null, Modifier.size(72.dp), tint = OnSurfaceMuted)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.psn_extra_account, index + 1),
                                    style = MaterialTheme.typography.titleMedium, color = OnSurfaceLight,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                Text(if(account.available) account.rid else stringResource(R.string.coop_account_unavailable),
                                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                TvFocusCard(focusRequester = focus.last(),
                    onFocusChange = { if(it) session.focusCoopAccount(choice.id, choice.accounts.size) },
                    onClick = { session.cancelCoopAccountChoice(choice.id) }) {
                    Column(Modifier.width(200.dp).height(210.dp).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(48.dp), tint = OnSurfaceMuted)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium, color = OnSurfaceLight)
                    }
                }
            }
        }
    }
}
