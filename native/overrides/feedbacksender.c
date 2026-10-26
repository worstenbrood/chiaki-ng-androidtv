// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Preserve upstream ABI; one session-owned sender multiplexes all controller slots.
#include <chiaki/multislot.h>

CHIAKI_EXPORT ChiakiErrorCode chiaki_feedback_sender_init(ChiakiFeedbackSender *sender, ChiakiTakion *takion)
{
    return chiaki_multislot_sender_init(sender, takion);
}
CHIAKI_EXPORT void chiaki_feedback_sender_fini(ChiakiFeedbackSender *sender)
{
    chiaki_multislot_sender_fini(sender);
}
CHIAKI_EXPORT ChiakiErrorCode chiaki_feedback_sender_set_controller_state(ChiakiFeedbackSender *sender, ChiakiControllerState *state)
{
    return chiaki_multislot_sender_set_state(sender, 0, state);
}
