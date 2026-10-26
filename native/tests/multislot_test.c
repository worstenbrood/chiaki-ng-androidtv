// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
#include <chiaki/multislot.h>
#include <pb_decode.h>
#include <takion.pb.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static pthread_mutex_t capture_mutex = PTHREAD_MUTEX_INITIALIZER;
static unsigned state_count, history_count, controls, connections;
static uint16_t last_state_seq, last_history_seq;
static bool have_state, have_history;
static uint8_t previous_history[768];
static size_t previous_size;
static bool saw_release[7];
static bool saw_button[7], saw_touch_down, saw_touch_up, saw_trigger;
static uint8_t last_control[16];
static uint16_t last_control_type;
static unsigned joined_connections;

ChiakiErrorCode chiaki_ctrl_send_message(ChiakiCtrl *ctrl, uint16_t type, const uint8_t *payload, size_t size)
{
    (void)ctrl;
    assert(size <= sizeof(last_control));
    if(type == 0x68) assert(size == 16);
    memcpy(last_control, payload, size); last_control_type = type; controls++;
    return CHIAKI_ERR_SUCCESS;
}
ChiakiErrorCode chiaki_takion_send_message_data(ChiakiTakion *takion, uint8_t flags, uint16_t channel, uint8_t *buf, size_t size, ChiakiSeqNum32 *seq)
{
    (void)takion; (void)seq;
    assert(flags == 1 && channel == 1);
    tkproto_TakionMessage msg = {0};
    pb_istream_t stream = pb_istream_from_buffer(buf, size);
    assert(pb_decode(&stream, tkproto_TakionMessage_fields, &msg));
    assert(msg.type == tkproto_TakionMessage_PayloadType_CONTROLLERCONNECTION);
    assert(msg.controller_connection_payload.controller_id > 0 && msg.controller_connection_payload.controller_id < 7);
    if(msg.controller_connection_payload.connected) joined_connections++;
    connections++;
    return CHIAKI_ERR_SUCCESS;
}
ChiakiErrorCode chiaki_takion_send_feedback_state_slot(ChiakiTakion *tk, ChiakiSeqNum16 seq, ChiakiFeedbackState *state, uint8_t slot)
{
    (void)tk; (void)state;
    pthread_mutex_lock(&capture_mutex);
    assert(slot < 7);
    if(have_state) assert(seq == (uint16_t)(last_state_seq + 1));
    have_state = true; last_state_seq = seq; state_count++;
    pthread_mutex_unlock(&capture_mutex);
    return CHIAKI_ERR_SUCCESS;
}
ChiakiErrorCode chiaki_takion_send_feedback_history_slot(ChiakiTakion *tk, ChiakiSeqNum16 seq, uint8_t *buf, size_t size, uint8_t slot)
{
    (void)tk;
    pthread_mutex_lock(&capture_mutex);
    assert(slot == 0 && size <= sizeof(previous_history));
    if(have_history) {
        assert(seq == last_history_seq || seq == (uint16_t)(last_history_seq + 1));
        if(seq == last_history_seq) assert(size == previous_size && memcmp(previous_history, buf, size) == 0);
    }
    have_history = true; last_history_seq = seq;
    memcpy(previous_history, buf, size); previous_size = size; history_count++;
    for(size_t pos = 0; pos < size;) {
        uint8_t kind = buf[pos] & 0xf0, player = buf[pos] & 0xf;
        assert(player < 7);
        if(kind == 0x80) {
            uint8_t button = buf[pos + 1];
            saw_button[player] = true;
            if(button == 0x88 && buf[pos + 2] == 0) saw_release[player] = true;
            if(player == 1 && button == 0x86 && buf[pos + 2] == 128) saw_trigger = true;
            pos += button < 0x8c ? 3 : 2;
        } else {
            assert(kind == 0xc0 || kind == 0xd0);
            if(player == 1 && kind == 0xd0) saw_touch_down = true;
            if(player == 1 && kind == 0xc0) saw_touch_up = true;
            pos += 5;
        }
        assert(pos <= size);
    }
    pthread_mutex_unlock(&capture_mutex);
    return CHIAKI_ERR_SUCCESS;
}
static void setup(ChiakiSession *session)
{
    memset(session, 0, sizeof(*session));
    session->target = CHIAKI_TARGET_PS5_1;
    assert(chiaki_mutex_init(&session->stream_connection.feedback_sender_mutex, false) == CHIAKI_ERR_SUCCESS);
    assert(chiaki_feedback_sender_init(&session->stream_connection.feedback_sender, &session->stream_connection.takion) == CHIAKI_ERR_SUCCESS);
    session->stream_connection.feedback_sender_active = true;
}
static void teardown(ChiakiSession *session)
{
    chiaki_mutex_lock(&session->stream_connection.feedback_sender_mutex);
    session->stream_connection.feedback_sender_active = false;
    chiaki_feedback_sender_fini(&session->stream_connection.feedback_sender);
    chiaki_mutex_unlock(&session->stream_connection.feedback_sender_mutex);
    chiaki_mutex_fini(&session->stream_connection.feedback_sender_mutex);
}
static void reply(ChiakiSession *s, uint16_t type, uint8_t slot, uint8_t result)
{
    uint8_t payload[] = { slot, result };
    chiaki_multislot_ctrl_received(s, type, payload, sizeof(payload));
}
int main(void)
{
    ChiakiSession s;
    setup(&s);
    assert(chiaki_session_add_user(&s, 0, 0, 42) == CHIAKI_ERR_INVALID_DATA);
    assert(chiaki_session_add_user(&s, 7, 0, 42) == CHIAKI_ERR_INVALID_DATA);
    assert(chiaki_session_add_user(&s, 1, 0, 0) == CHIAKI_ERR_INVALID_DATA);
    assert(chiaki_session_add_user(&s, 1, 0, UINT64_C(0x0102030405060708)) == CHIAKI_ERR_SUCCESS);
    // Asobi: 0x34e598 passes control type 3 through the transport vtable
    // at 0xca2668 to the CTRLADD_EX builder at 0x454f04.
    const uint8_t expected_join[16] = {8, 7, 6, 5, 4, 3, 2, 1, 1, 3, 0, 0, 0, 0, 0, 0};
    assert(last_control_type == 0x68 && memcmp(last_control, expected_join, sizeof(expected_join)) == 0);
    assert(chiaki_multislot_status(&s, 1) == 1 && connections == 0);
    assert(chiaki_session_add_user(&s, 2, 0, 43) == CHIAKI_ERR_MUTEX_LOCKED);
    reply(&s, 0x8068, 2, 0); // unrelated/stale reply cannot seat a player
    assert(chiaki_multislot_status(&s, 1) == 1 && connections == 0);
    reply(&s, 0x8068, 1, 0);
    assert(chiaki_multislot_status(&s, 1) == 2 && joined_connections == 1);
    assert(chiaki_session_add_user(&s, 2, 0, UINT64_C(0x0102030405060708)) == CHIAKI_ERR_MUTEX_LOCKED);
    s.connect_info.enable_dualsense = true;
    assert(chiaki_session_add_user(&s, 2, 0, 43) == CHIAKI_ERR_SUCCESS);
    assert(last_control[9] == 3); // independent of Takion's DualSense type
    s.connect_info.enable_dualsense = false;
    reply(&s, 0x8068, 2, 0);
    ChiakiControllerState state;
    chiaki_controller_state_set_idle(&state);
    state.buttons = CHIAKI_CONTROLLER_BUTTON_CROSS;
    chiaki_feedback_sender_set_controller_state(&s.stream_connection.feedback_sender, &state);
    state.l2_state = 128; state.touches[0].id = 0; state.touches[0].x = 960; state.touches[0].y = 471;
    chiaki_multislot_set_controller_state(&s, 1, &state);
    chiaki_multislot_set_controller_state(&s, 2, &state);
    chiaki_controller_state_set_idle(&state);
    // Releases immediately after presses must survive sender scheduling/coalescing.
    chiaki_feedback_sender_set_controller_state(&s.stream_connection.feedback_sender, &state);
    chiaki_multislot_set_controller_state(&s, 1, &state);
    chiaki_multislot_set_controller_state(&s, 2, &state);
    usleep(600000);
    pthread_mutex_lock(&capture_mutex);
    assert(saw_button[0] && saw_button[1] && saw_button[2] && saw_trigger && saw_touch_down && saw_touch_up);
    assert(history_count >= 2 && state_count >= 3);
    uint16_t old_history_seq = last_history_seq;
    pthread_mutex_unlock(&capture_mutex);
    state.left_x = 12345; // stick-only input must not create new button events
    chiaki_multislot_set_controller_state(&s, 1, &state);
    usleep(250000);
    pthread_mutex_lock(&capture_mutex);
    assert(last_history_seq == old_history_seq);
    pthread_mutex_unlock(&capture_mutex);
    state.buttons = CHIAKI_CONTROLLER_BUTTON_CROSS;
    chiaki_multislot_set_controller_state(&s, 1, &state);
    usleep(50000);
    pthread_mutex_lock(&capture_mutex); saw_release[1] = false; pthread_mutex_unlock(&capture_mutex);
    assert(chiaki_session_remove_user(&s, 1) == CHIAKI_ERR_SUCCESS);
    usleep(50000);
    pthread_mutex_lock(&capture_mutex); assert(saw_release[1]); pthread_mutex_unlock(&capture_mutex);
    reply(&s, 0x8009, 1, 0);
    assert(chiaki_multislot_status(&s, 1) == 0);
    assert(chiaki_session_add_user(&s, 3, 0, 44) == CHIAKI_ERR_SUCCESS);
    chiaki_session_remove_user(&s, 3);
    unsigned accepted = joined_connections;
    reply(&s, 0x8068, 3, 0); // late success after unplug: remove, never enable feedback
    assert(joined_connections == accepted && last_control_type == 9 && last_control[0] == 3);
    reply(&s, 0x8009, 3, 0);
    assert(chiaki_session_add_user(&s, 3, 0, 44) == CHIAKI_ERR_SUCCESS);
    reply(&s, 0x8068, 3, 2);
    assert(chiaki_multislot_status(&s, 3) == -2);
    assert(chiaki_session_add_user(&s, 3, 0, 44) == CHIAKI_ERR_SUCCESS);
    reply(&s, 0x61, 3, 0);
    assert(chiaki_multislot_status(&s, 3) == -4);
    reply(&s, 0x8068, 3, 0);
    assert(joined_connections == accepted && chiaki_multislot_status(&s, 3) == -4);
    reply(&s, 0x8009, 3, 0);
    assert(chiaki_session_add_user(&s, 3, 0, 44) == CHIAKI_ERR_SUCCESS);
    chiaki_multislot_ctrl_received(&s, 0x64, NULL, 0);
    assert(chiaki_multislot_status(&s, 3) == -3);
    reply(&s, 0x8068, 3, 3);
    assert(chiaki_session_add_user(&s, 3, 0, 44) == CHIAKI_ERR_SUCCESS);
    uint8_t malformed = 0;
    chiaki_multislot_ctrl_received(&s, 0x8068, &malformed, 1);
    assert(chiaki_multislot_status(&s, 3) == 1);
    uint8_t legacy_ok = 0;
    chiaki_multislot_ctrl_received(&s, 0x8008, &legacy_ok, 1);
    assert(chiaki_multislot_status(&s, 3) == 2);
    chiaki_session_remove_user(&s, 3); reply(&s, 0x8009, 3, 0);
    assert(chiaki_session_add_user(&s, 3, 0, 44) == CHIAKI_ERR_SUCCESS);
    usleep(1300000);
    assert(chiaki_multislot_status(&s, 3) == -6);
    reply(&s, 0x8068, 3, 0); reply(&s, 0x8009, 3, 0);
    teardown(&s);
    unsigned stopped_count = state_count;
    usleep(250000);
    assert(state_count == stopped_count); // no sender may access a closed/freed Takion
    assert(chiaki_multislot_sender_set_state(&s.stream_connection.feedback_sender, 0, &state) == CHIAKI_ERR_UNINITIALIZED);
    // Reusing the same session address starts fresh counters and slot reservations.
    have_state = have_history = false;
    setup(&s);
    assert(chiaki_multislot_status(&s, 1) == 0);
    assert(chiaki_session_add_user(&s, 1, 0, 42) == CHIAKI_ERR_SUCCESS);
    teardown(&s);
    puts("multislot: join, refusal, cancellation, timeout, history, sequences and teardown passed");
    return 0;
}
