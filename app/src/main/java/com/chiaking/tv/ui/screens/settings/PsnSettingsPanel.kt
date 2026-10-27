package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.ui.components.PsnAccountAvatar
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.PsnSettingsPanel() {
    val extras = viewModel.psnAccounts.filter { it.rid != viewModel.primaryAccountId }
    SettingsCard(SurfaceFrosted) {
        if(viewModel.psnLoggedIn) {
            val primary = viewModel.psnAccounts.firstOrNull { it.rid == viewModel.primaryAccountId }
            TvInfoRow(stringResource(R.string.psn_primary_account),
                primary?.onlineId?.ifBlank { primary.rid } ?: "",
                leadingContent = {
                    PsnAccountAvatar(primary?.avatarUrl.orEmpty(), Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                })
            TvActionRow(
                stringResource(R.string.sign_out),
                stringResource(R.string.psn_sign_out_keep_extras),
                {
                    viewModel.signOut()
                }, focusRequester = fr(1), onFocus = fb(1)
            )
        } else {
            TvActionRow(
                stringResource(R.string.sign_in_psn),
                stringResource(R.string.sign_in_psn_summary),
                { onAddPsn() }, focusRequester = fr(1), onFocus = fb(1)
            )
        }
        TvInfoRow(stringResource(R.string.psn_extra_accounts),
            stringResource(R.string.psn_extra_count, extras.size, PsnAccountRepository.MAX_EXTRA_ACCOUNTS))
        if(extras.size < PsnAccountRepository.MAX_EXTRA_ACCOUNTS) {
            TvActionRow(
                stringResource(R.string.coop_add_account),
                stringResource(R.string.coop_add_account_summary),
                { onAddCoop() }, focusRequester = fr(2), onFocus = fb(2)
            )
        } else {
            TvInfoRow(stringResource(R.string.coop_add_account), stringResource(R.string.psn_extra_limit))
        }
        extras.forEachIndexed { index, account ->
            key(account.rid) {
                TvActionRow(
                    account.onlineId.ifBlank { stringResource(R.string.psn_extra_account, index + 1) },
                    stringResource(R.string.psn_remove_account_summary, account.rid),
                    { onRemovePsn(account.rid) },
                    focusRequester = fr(index + 3), onFocus = fb(index + 3),
                    leadingContent = {
                        PsnAccountAvatar(account.avatarUrl, Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                    }
                )
            }
        }
    }
}
