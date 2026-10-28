package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.ui.components.PsnAccountAvatar
import com.chiaking.tv.ui.components.settings.SettingsCard
import com.chiaking.tv.ui.components.settings.TvActionRow
import com.chiaking.tv.ui.components.settings.TvInfoRow
import com.chiaking.tv.ui.theme.SurfaceFrosted

@Composable
internal fun SettingsPanelScope.PsnSettingsPanel() {
    val accounts = viewModel.psnAccounts
    val canAdd = accounts.size < PsnAccountRepository.MAX_ACCOUNTS
    SettingsCard(SurfaceFrosted) {
        TvInfoRow(stringResource(R.string.psn_accounts),
            stringResource(R.string.psn_account_count, accounts.size, PsnAccountRepository.MAX_ACCOUNTS))
        if(viewModel.accountChangesLocked) TvInfoRow(stringResource(R.string.psn_accounts), stringResource(R.string.psn_account_locked))
        if(canAdd) TvActionRow(stringResource(R.string.psn_add_account), stringResource(R.string.psn_add_account_summary),
            onAddPsn, focusRequester = fr(1), onFocus = fb(1))
        else TvInfoRow(stringResource(R.string.psn_add_account), stringResource(R.string.psn_account_limit))
        accounts.forEachIndexed { index, account ->
            key(account.rid) {
                val row = index + if(canAdd) 2 else 1
                TvActionRow(account.onlineId.ifBlank { stringResource(R.string.psn_account_number, index + 1) },
                    stringResource(if(account.rid == viewModel.connectionAccountId) R.string.psn_default_account_summary else R.string.psn_manage_account_summary),
                    { onManagePsn(account.rid) }, focusRequester = fr(row), onFocus = fb(row),
                    leadingContent = {
                        PsnAccountAvatar(account.avatarUrl, Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                    })
            }
        }
    }
}
