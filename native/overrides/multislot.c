// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Local Remote Play co-op implementation. Packet offsets verified against Asobi.
#include <chiaki/multislot.h>
#include <chiaki/ctrl.h>
#include <chiaki/time.h>
#include <takion.pb.h>
#include <pb_encode.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#define SLOT_COUNT 7
#define HISTORY_COUNT 40
#define HISTORY_RETAIN 20
#define FEEDBACK_MS 200
#ifndef CHIAKI_COOP_JOIN_TIMEOUT_MS
#define CHIAKI_COOP_JOIN_TIMEOUT_MS 30000
#endif

typedef struct {
    ChiakiControllerState state;
    bool active, dirty, reserved, cancelled;
    int status, failure;
    uint64_t rid, last_state_ms;
} Slot;

typedef struct Sender {
    struct Sender *next;
    ChiakiFeedbackSender *owner;
    ChiakiTakion *takion;
    ChiakiMutex mutex;
    ChiakiCond cond;
    ChiakiThread thread;
    bool stopping;
    Slot slots[SLOT_COUNT];
    ChiakiFeedbackHistoryBuffer history;
    uint8_t packets[CHIAKI_FEEDBACK_HISTORY_PACKET_QUEUE_SIZE][CHIAKI_FEEDBACK_HISTORY_PACKET_BUF_SIZE];
    size_t packet_sizes[CHIAKI_FEEDBACK_HISTORY_PACKET_QUEUE_SIZE], begin, count;
    ChiakiSeqNum16 state_seq, history_seq;
    uint64_t history_revision;
    uint8_t last_history[CHIAKI_FEEDBACK_HISTORY_PACKET_BUF_SIZE];
    size_t last_history_size;
    uint64_t last_history_ms, join_deadline;
    uint8_t pending_slot;
} Sender;

// Registry is only an ABI-preserving lookup. State, counters and lifetime belong to a session.
static pthread_mutex_t registry_mutex = PTHREAD_MUTEX_INITIALIZER;
static Sender *senders;

static Sender *lock_sender(ChiakiFeedbackSender *owner)
{
    pthread_mutex_lock(&registry_mutex);
    Sender *s = senders;
    while(s && s->owner != owner) s = s->next;
    if(s) chiaki_mutex_lock(&s->mutex);
    pthread_mutex_unlock(&registry_mutex);
    return s;
}

static Sender *lock_session(ChiakiSession *session)
{
    chiaki_mutex_lock(&session->stream_connection.feedback_sender_mutex);
    Sender *s = session->stream_connection.feedback_sender_active
        ? lock_sender(&session->stream_connection.feedback_sender) : NULL;
    if(!s) chiaki_mutex_unlock(&session->stream_connection.feedback_sender_mutex);
    return s;
}
static void unlock_session(ChiakiSession *session, Sender *s)
{
    chiaki_mutex_unlock(&s->mutex);
    chiaki_mutex_unlock(&session->stream_connection.feedback_sender_mutex);
}

static ChiakiErrorCode controller_connection(ChiakiSession *session, uint8_t slot, bool connected)
{
    tkproto_TakionMessage msg = {0};
    msg.type = tkproto_TakionMessage_PayloadType_CONTROLLERCONNECTION;
    msg.has_controller_connection_payload = true;
    msg.controller_connection_payload.has_connected = true;
    msg.controller_connection_payload.connected = connected;
    msg.controller_connection_payload.has_controller_id = true;
    msg.controller_connection_payload.controller_id = slot;
    msg.controller_connection_payload.has_controller_type = true;
    msg.controller_connection_payload.controller_type = session->connect_info.enable_dualsense
        ? tkproto_ControllerConnectionPayload_ControllerType_DUALSENSE
        : tkproto_ControllerConnectionPayload_ControllerType_DUALSHOCK4;
    uint8_t buf[128];
    pb_ostream_t stream = pb_ostream_from_buffer(buf, sizeof(buf));
    if(!pb_encode(&stream, tkproto_TakionMessage_fields, &msg)) return CHIAKI_ERR_UNKNOWN;
    return chiaki_takion_send_message_data(&session->stream_connection.takion, 1, 1, buf, stream.bytes_written, NULL);
}

static void queue_history(Sender *s)
{
    if(!s->history.len) return;
    size_t index = (s->begin + s->count) % CHIAKI_FEEDBACK_HISTORY_PACKET_QUEUE_SIZE;
    size_t size = sizeof(s->packets[index]);
    if(chiaki_feedback_history_buffer_format(&s->history, s->packets[index], &size) != CHIAKI_ERR_SUCCESS) return;
    s->packet_sizes[index] = size;
    if(s->count == CHIAKI_FEEDBACK_HISTORY_PACKET_QUEUE_SIZE)
        s->begin = (s->begin + 1) % CHIAKI_FEEDBACK_HISTORY_PACKET_QUEUE_SIZE;
    else s->count++;
    if(s->history.len > HISTORY_RETAIN) s->history.len = HISTORY_RETAIN;
}

static void push_event(Sender *s, ChiakiFeedbackHistoryEvent *event)
{
    chiaki_feedback_history_buffer_push(&s->history, event);
    s->history_revision++;
}

static void record_button(Sender *s, uint8_t slot, uint64_t button, uint8_t value)
{
    ChiakiFeedbackHistoryEvent event;
    if(chiaki_feedback_history_event_set_button(&event, button, value) != CHIAKI_ERR_SUCCESS) return;
    event.buf[0] = 0x80 | slot;
    push_event(s, &event);
}

static void record_state(Sender *s, uint8_t slot, const ChiakiControllerState *old, const ChiakiControllerState *now)
{
    uint64_t revision = s->history_revision;
    for(size_t i = 0; i < CHIAKI_CONTROLLER_TOUCHES_MAX; i++) {
        const ChiakiControllerTouch *a = &old->touches[i], *b = &now->touches[i];
        // A changed pointer ID needs both a release and a new contact.
        if(a->id >= 0 && a->id != b->id) {
            ChiakiFeedbackHistoryEvent e;
            chiaki_feedback_history_event_set_touchpad(&e, false, a->id, a->x, a->y);
            e.buf[0] |= slot;
            push_event(s, &e);
        }
        if(b->id >= 0 && (a->id != b->id || a->x != b->x || a->y != b->y)) {
            ChiakiFeedbackHistoryEvent e;
            chiaki_feedback_history_event_set_touchpad(&e, true, b->id, b->x, b->y);
            e.buf[0] |= slot;
            push_event(s, &e);
        }
    }
    for(unsigned i = 0; i < CHIAKI_CONTROLLER_BUTTONS_COUNT; i++) {
        uint64_t bit = UINT64_C(1) << i;
        if((old->buttons ^ now->buttons) & bit) record_button(s, slot, bit, now->buttons & bit ? 0xff : 0);
    }
    if(old->l2_state != now->l2_state) record_button(s, slot, CHIAKI_CONTROLLER_ANALOG_BUTTON_L2, now->l2_state);
    if(old->r2_state != now->r2_state) record_button(s, slot, CHIAKI_CONTROLLER_ANALOG_BUTTON_R2, now->r2_state);
    if(s->history_revision != revision) queue_history(s);
}

static void discard_slot_history(Sender *s, uint8_t slot)
{
    ChiakiFeedbackHistoryEvent events[HISTORY_COUNT];
    size_t count = 0;
    for(size_t i = 0; i < s->history.len; i++) {
        ChiakiFeedbackHistoryEvent e = s->history.events[(s->history.begin + i) % s->history.size];
        if((e.buf[0] & 0xf) != slot) events[count++] = e;
    }
    s->history.len = 0;
    while(count) chiaki_feedback_history_buffer_push(&s->history, &events[--count]);
    s->begin = s->count = 0;
    s->last_history_size = 0;
    // Re-queue surviving events once so another player's pending release is not lost.
    queue_history(s);
}

static bool pending(void *user)
{
    Sender *s = user;
    if(s->stopping || s->count) return true;
    for(unsigned i = 0; i < SLOT_COUNT; i++)
        if(s->slots[i].active && s->slots[i].dirty) return true;
    return false;
}

static void *sender_thread(void *user)
{
    Sender *s = user;
    chiaki_mutex_lock(&s->mutex);
    while(!s->stopping) {
        if(!pending(s)) chiaki_cond_timedwait_pred(&s->cond, &s->mutex, FEEDBACK_MS, pending, s);
        if(s->stopping) break;
        uint64_t now = chiaki_time_now_monotonic_ms();
        if(s->pending_slot && now >= s->join_deadline) {
            Slot *slot = &s->slots[s->pending_slot];
            if(!slot->cancelled) { slot->cancelled = true; slot->status = slot->failure = -6; }
            // Keep the slot reserved until its host reply; a delayed success must be removed.
        }
        for(uint8_t i = 0; i < SLOT_COUNT; i++) {
            Slot *slot = &s->slots[i];
            if(!slot->active || (!slot->dirty && now - slot->last_state_ms < FEEDBACK_MS)) continue;
            ChiakiControllerState *state = &slot->state;
            ChiakiFeedbackState fs = {
                .left_x = state->left_x, .left_y = state->left_y,
                .right_x = state->right_x, .right_y = state->right_y,
                .gyro_x = state->gyro_x, .gyro_y = state->gyro_y, .gyro_z = state->gyro_z,
                .accel_x = state->accel_x, .accel_y = state->accel_y, .accel_z = state->accel_z,
                .orient_x = state->orient_x, .orient_y = state->orient_y,
                .orient_z = state->orient_z, .orient_w = state->orient_w
            };
            chiaki_takion_send_feedback_state_slot(s->takion, s->state_seq++, &fs, i);
            slot->dirty = false;
            slot->last_state_ms = now;
        }
        // Repeat recent releases while idle as well: UDP loss must not leave a held button.
        if(!s->count && s->last_history_size && now - s->last_history_ms >= FEEDBACK_MS) {
            // A retransmission keeps its sequence: old presses must not look like new events.
            chiaki_takion_send_feedback_history_slot(s->takion, (ChiakiSeqNum16)(s->history_seq - 1), s->last_history, s->last_history_size, 0);
            s->last_history_ms = now;
        }
        if(s->count) {
            size_t index = s->begin;
            chiaki_takion_send_feedback_history_slot(s->takion, s->history_seq++, s->packets[index], s->packet_sizes[index], 0);
            memcpy(s->last_history, s->packets[index], s->packet_sizes[index]);
            s->last_history_size = s->packet_sizes[index];
            s->begin = (s->begin + 1) % CHIAKI_FEEDBACK_HISTORY_PACKET_QUEUE_SIZE;
            s->count--;
            s->last_history_ms = now;
        }
    }
    chiaki_mutex_unlock(&s->mutex);
    return NULL;
}

ChiakiErrorCode chiaki_multislot_sender_init(ChiakiFeedbackSender *owner, ChiakiTakion *takion)
{
    Sender *s = calloc(1, sizeof(*s));
    if(!s) return CHIAKI_ERR_MEMORY;
    s->owner = owner; s->takion = takion;
    for(unsigned i = 0; i < SLOT_COUNT; i++) chiaki_controller_state_set_idle(&s->slots[i].state);
    s->slots[0].active = s->slots[0].dirty = true;
    ChiakiErrorCode err = chiaki_feedback_history_buffer_init(&s->history, HISTORY_COUNT);
    if(err != CHIAKI_ERR_SUCCESS) goto free_sender;
    err = chiaki_mutex_init(&s->mutex, false);
    if(err != CHIAKI_ERR_SUCCESS) goto free_history;
    err = chiaki_cond_init(&s->cond);
    if(err != CHIAKI_ERR_SUCCESS) goto free_mutex;
    err = chiaki_thread_create(&s->thread, sender_thread, s);
    if(err != CHIAKI_ERR_SUCCESS) goto free_cond;
    pthread_mutex_lock(&registry_mutex);
    s->next = senders; senders = s;
    pthread_mutex_unlock(&registry_mutex);
    chiaki_thread_set_name(&s->thread, "Chiaki Slot Feedback");
    return CHIAKI_ERR_SUCCESS;
free_cond: chiaki_cond_fini(&s->cond);
free_mutex: chiaki_mutex_fini(&s->mutex);
free_history: chiaki_feedback_history_buffer_fini(&s->history);
free_sender: free(s); return err;
}

void chiaki_multislot_sender_fini(ChiakiFeedbackSender *owner)
{
    pthread_mutex_lock(&registry_mutex);
    Sender **link = &senders;
    while(*link && (*link)->owner != owner) link = &(*link)->next;
    Sender *s = *link;
    if(s) { *link = s->next; chiaki_mutex_lock(&s->mutex); }
    pthread_mutex_unlock(&registry_mutex);
    if(!s) return;
    s->stopping = true;
    chiaki_cond_signal(&s->cond);
    chiaki_mutex_unlock(&s->mutex);
    chiaki_thread_join(&s->thread, NULL);
    chiaki_cond_fini(&s->cond); chiaki_mutex_fini(&s->mutex);
    chiaki_feedback_history_buffer_fini(&s->history);
    free(s);
}

ChiakiErrorCode chiaki_multislot_sender_set_state(ChiakiFeedbackSender *owner, uint8_t slot, const ChiakiControllerState *state)
{
    if(slot >= SLOT_COUNT) return CHIAKI_ERR_INVALID_DATA;
    Sender *s = lock_sender(owner);
    if(!s) return CHIAKI_ERR_UNINITIALIZED;
    Slot *pad = &s->slots[slot];
    if(pad->active && !chiaki_controller_state_equals(&pad->state, (ChiakiControllerState *)state)) {
        record_state(s, slot, &pad->state, state);
        pad->state = *state; pad->dirty = true;
        chiaki_cond_signal(&s->cond);
    }
    chiaki_mutex_unlock(&s->mutex);
    return CHIAKI_ERR_SUCCESS;
}

void chiaki_multislot_set_controller_state(ChiakiSession *session, uint8_t slot, ChiakiControllerState *state)
{
    if(slot < 1 || slot >= SLOT_COUNT) return;
    chiaki_mutex_lock(&session->stream_connection.feedback_sender_mutex);
    if(session->stream_connection.feedback_sender_active)
        chiaki_multislot_sender_set_state(&session->stream_connection.feedback_sender, slot, state);
    chiaki_mutex_unlock(&session->stream_connection.feedback_sender_mutex);
}

ChiakiErrorCode chiaki_session_add_user(ChiakiSession *session, uint8_t slot, uint8_t ctrl_type, uint64_t rid)
{
    if(slot < 1 || slot >= SLOT_COUNT || !rid || !chiaki_target_is_ps5(session->target)) return CHIAKI_ERR_INVALID_DATA;
    Sender *s = lock_session(session);
    if(!s) return CHIAKI_ERR_UNINITIALIZED;
    Slot *pad = &s->slots[slot];
    ChiakiErrorCode err = CHIAKI_ERR_MUTEX_LOCKED;
    if(s->pending_slot || pad->reserved) goto done;
    for(unsigned i = 1; i < SLOT_COUNT; i++) if(s->slots[i].reserved && s->slots[i].rid == rid) goto done;
    uint8_t payload[16] = {0};
    for(unsigned i = 0; i < 8; i++) payload[i] = (uint8_t)(rid >> (8 * i));
    payload[8] = slot;
    // CTRLADD_EX uses the control-channel type (3), not the Takion
    // CONTROLLERCONNECTION enum (DS4=8 / DualSense=12).
    payload[9] = ctrl_type ? ctrl_type : 3;
    pad->reserved = true; pad->cancelled = false; pad->rid = rid; pad->status = 1; pad->failure = 0;
    s->pending_slot = slot; s->join_deadline = chiaki_time_now_monotonic_ms() + CHIAKI_COOP_JOIN_TIMEOUT_MS;
    CHIAKI_LOGI(session->log, "Co-op requesting join: slot=%u control_type=%u", slot, payload[9]);
    err = chiaki_ctrl_send_message(&session->ctrl, 0x68, payload, sizeof(payload));
    if(err != CHIAKI_ERR_SUCCESS) { pad->reserved = false; pad->status = pad->failure = -5; s->pending_slot = 0; }
done:
    unlock_session(session, s);
    return err;
}

static ChiakiErrorCode leave_locked(ChiakiSession *session, Sender *s, uint8_t slot)
{
    Slot *pad = &s->slots[slot];
    ChiakiControllerState idle;
    chiaki_controller_state_set_idle(&idle);
    if(pad->active) record_state(s, slot, &pad->state, &idle);
    pad->state = idle;
    pad->active = false; pad->status = 3;
    // Keep neutral/release history retryable until the host acknowledges removal.
    chiaki_cond_signal(&s->cond);
    ChiakiErrorCode err = chiaki_ctrl_send_message(&session->ctrl, 0x09, &slot, 1);
    controller_connection(session, slot, false);
    if(err != CHIAKI_ERR_SUCCESS) pad->status = pad->failure = -5; // reservation remains until acknowledged
    return err;
}

ChiakiErrorCode chiaki_session_remove_user(ChiakiSession *session, uint8_t slot)
{
    if(slot < 1 || slot >= SLOT_COUNT) return CHIAKI_ERR_INVALID_DATA;
    Sender *s = lock_session(session);
    if(!s) return CHIAKI_ERR_UNINITIALIZED;
    Slot *pad = &s->slots[slot];
    ChiakiErrorCode err = CHIAKI_ERR_SUCCESS;
    if(pad->reserved) {
        if(s->pending_slot == slot) { pad->cancelled = true; pad->status = pad->failure = -8; }
        else if(pad->status != 3) err = leave_locked(session, s, slot);
    }
    unlock_session(session, s);
    return err;
}

int chiaki_multislot_status(ChiakiSession *session, uint8_t slot)
{
    if(slot < 1 || slot >= SLOT_COUNT) return -1;
    Sender *s = lock_session(session);
    if(!s) return 0;
    int status = s->slots[slot].failure ? s->slots[slot].failure : s->slots[slot].status;
    unlock_session(session, s);
    return status;
}

void chiaki_multislot_ctrl_received(ChiakiSession *session, uint16_t type, const uint8_t *payload, size_t size)
{
    Sender *s = lock_session(session);
    if(!s) return;
    uint8_t slot = 0, result = 0;
    if(type == 0x8068 || type == 0x8009) {
        if(size < 2) goto done;
        slot = payload[0]; result = payload[1];
    } else if(type == 0x8008) {
        if(!size) goto done;
        slot = s->pending_slot; result = payload[0];
    } else if(type == 0x61 || type == 0x64) {
        slot = type == 0x61 && size ? payload[0] : s->pending_slot;
        if(slot && slot < SLOT_COUNT && slot == s->pending_slot) {
            s->slots[slot].cancelled = true;
            s->slots[slot].status = s->slots[slot].failure = type == 0x61 ? -4 : -3;
        }
        goto done;
    } else goto done;
    if(slot < 1 || slot >= SLOT_COUNT) goto done;
    Slot *pad = &s->slots[slot];
    if(type == 0x8009) {
        if(!pad->reserved || (pad->status != 3 && pad->status != -5)) goto done;
        if(result == 0) {
            pad->reserved = false; pad->status = 0; pad->rid = 0;
            discard_slot_history(s, slot);
        }
        else pad->status = pad->failure = -7;
        goto done;
    }
    if(s->pending_slot != slot || !pad->reserved) goto done;
    CHIAKI_LOGI(session->log, "Co-op join reply: slot=%u result=%u", slot, result);
    s->pending_slot = 0;
    if(result == 0) {
        if(pad->cancelled) leave_locked(session, s, slot);
        else {
            ChiakiErrorCode err = controller_connection(session, slot, true);
            if(err == CHIAKI_ERR_SUCCESS) {
                pad->active = pad->dirty = true; pad->status = 2;
                chiaki_controller_state_set_idle(&pad->state);
                chiaki_cond_signal(&s->cond);
            } else { leave_locked(session, s, slot); pad->status = pad->failure = -5; }
        }
    } else {
        pad->reserved = false;
        if(!pad->cancelled) pad->status = pad->failure = (result == 2 || result == 5) ? -2 : result == 3 ? -3 : -1;
    }
done:
    unlock_session(session, s);
}
