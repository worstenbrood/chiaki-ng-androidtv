package com.chiaking.tv.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.BuildConfig
import com.chiaking.tv.R
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.AboutSettingsPanel() {
    val ctx = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch(_: Exception) { }
    }
    SettingsCard(SurfaceFrosted) {
        TvInfoRow(stringResource(R.string.chiaki_ng_core), UPSTREAM_CHIAKI_VERSION)
        TvActionRow(
            stringResource(R.string.about_chiaki_ng),
            stringResource(R.string.about_chiaki_ng_summary),
            { openUrl("https://github.com/chiaki-ng/chiaki-ng") }
        )
        TvInfoRow(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
        TvActionRow(
            stringResource(R.string.about_project),
            stringResource(R.string.about_project_summary),
            { openUrl("https://github.com/worstenbrood/chiaki-ng-androidtv") }
        )
        TvActionRow(
            stringResource(R.string.about_license),
            stringResource(R.string.about_license_summary),
            { openUrl("https://github.com/chiaki-ng/chiaki-ng/blob/master/LICENSE") }
        )
    }
}
