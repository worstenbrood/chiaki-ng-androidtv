// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Local override header — multi-user slot helpers (see docs/EASY_SIGNIN_PLAN.md).
#ifndef CHIAKI_MULTISLOT_H
#define CHIAKI_MULTISLOT_H

#include <chiaki/common.h>
#include <chiaki/session.h>
#include <chiaki/controller.h>
#include <chiaki/feedback.h>
#include <chiaki/takion.h>

#ifdef __cplusplus
extern "C" {
#endif

CHIAKI_EXPORT ChiakiErrorCode chiaki_session_add_user(ChiakiSession *session, uint8_t slot, uint8_t ctrl_type, uint64_t rid);
CHIAKI_EXPORT ChiakiErrorCode chiaki_session_remove_user(ChiakiSession *session, uint8_t slot);
CHIAKI_EXPORT void chiaki_multislot_set_controller_state(ChiakiSession *session, uint8_t slot, ChiakiControllerState *state);

// Session-scoped sender lifecycle. No public upstream struct layout changes.
ChiakiErrorCode chiaki_multislot_sender_init(ChiakiFeedbackSender *sender, ChiakiTakion *takion);
void chiaki_multislot_sender_fini(ChiakiFeedbackSender *sender);
ChiakiErrorCode chiaki_multislot_sender_set_state(ChiakiFeedbackSender *sender, uint8_t slot, const ChiakiControllerState *state);
void chiaki_multislot_ctrl_received(ChiakiSession *session, uint16_t type, const uint8_t *payload, size_t size);
// 0 idle; 1 joining; 2 joined; 3 leaving; negative values are user-visible failures.
int chiaki_multislot_status(ChiakiSession *session, uint8_t slot);

// defined in the takion.c override
ChiakiErrorCode chiaki_takion_send_feedback_state_slot(ChiakiTakion *takion, ChiakiSeqNum16 seq_num, ChiakiFeedbackState *feedback_state, uint8_t slot);
ChiakiErrorCode chiaki_takion_send_feedback_history_slot(ChiakiTakion *takion, ChiakiSeqNum16 seq_num, uint8_t *payload, size_t payload_size, uint8_t slot);

#ifdef __cplusplus
}
#endif

#endif
