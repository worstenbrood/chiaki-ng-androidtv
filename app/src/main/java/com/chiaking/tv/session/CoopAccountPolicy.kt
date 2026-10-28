package com.chiaking.tv.session

import com.chiaking.tv.common.psn.PsnAccountSummary
import com.chiaking.tv.common.psn.psnUserId

/** The owner belongs to the session/registration, independently of the default for future PSN connections. */
internal fun coopAccountOptions(accounts: List<PsnAccountSummary>, ownerId: String?, assigned: Set<String>): List<CoopAccountOption> =
    accounts.filter { it.rid != ownerId }.sortedBy { it.rid }.map {
        CoopAccountOption(it.rid, it.rid !in assigned && psnUserId(it.rid) != null, it.onlineId, it.avatarUrl)
    }
