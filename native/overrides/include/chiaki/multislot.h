// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Local override header — multi-user slot helpers (see docs/EASY_SIGNIN_PLAN.md).
#ifndef CHIAKI_MULTISLOT_H
#define CHIAKI_MULTISLOT_H

#include <chiaki/common.h>
#include <chiaki/session.h>

#ifdef __cplusplus
extern "C" {
#endif

CHIAKI_EXPORT ChiakiErrorCode chiaki_session_add_user(ChiakiSession *session, uint8_t slot, uint8_t ctrl_type, uint64_t rid);
CHIAKI_EXPORT ChiakiErrorCode chiaki_session_remove_user(ChiakiSession *session, uint8_t slot);

#ifdef __cplusplus
}
#endif

#endif
