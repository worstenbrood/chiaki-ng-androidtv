// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Local override — multi-user slot helpers (see docs/EASY_SIGNIN_PLAN.md).
// Sends CTRLADD_EX (0x68) + a per-slot CONTROLLERCONNECTION so the console can seat an
// extra player; the actual seating needs a PSN Easy Sign-in (Phase 2).
#include <chiaki/multislot.h>
#include <chiaki/ctrl.h>
#include <chiaki/streamconnection.h>
#include <takion.pb.h>
#include <pb_encode.h>
#include <string.h>

#define CTRL_MESSAGE_TYPE_CTRLADD_EX 0x68
#define CTRL_MESSAGE_TYPE_CONTROLLER_DEL 0x09
#define CTRLADD_EX_CTRL_TYPE_DS5 0x0c
#define CTRLADD_EX_CTRL_TYPE_DS4 0x08

static ChiakiErrorCode send_controller_connection_slot(ChiakiStreamConnection *sc, uint8_t slot, bool connected)
{
	ChiakiSession *session = sc->session;
	tkproto_TakionMessage msg;
	memset(&msg, 0, sizeof(msg));
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

	uint8_t buf[2048];
	pb_ostream_t stream = pb_ostream_from_buffer(buf, sizeof(buf));
	if(!pb_encode(&stream, tkproto_TakionMessage_fields, &msg))
	{
		CHIAKI_LOGE(sc->log, "Multislot controller connection protobuf encoding failed");
		return CHIAKI_ERR_UNKNOWN;
	}
	return chiaki_takion_send_message_data(&sc->takion, 1, 1, buf, stream.bytes_written, NULL);
}

CHIAKI_EXPORT ChiakiErrorCode chiaki_session_add_user(ChiakiSession *session, uint8_t slot, uint8_t ctrl_type, uint64_t rid)
{
	if(slot < 1)
		return CHIAKI_ERR_INVALID_DATA;

	if(ctrl_type == 0)
		ctrl_type = session->connect_info.enable_dualsense ? CTRLADD_EX_CTRL_TYPE_DS5 : CTRLADD_EX_CTRL_TYPE_DS4;

	uint8_t payload[16] = { 0 };
	// layout per Asobi's sender stores: [u64 rid LE][u8 playerId][u8 ctrlType][6x0]
	for(int i = 0; i < 8; i++)
		payload[i] = (uint8_t)(rid >> (8 * i));
	payload[8] = slot;
	payload[9] = ctrl_type;

	CHIAKI_LOGI(session->log, "Multislot: sending CTRLADD_EX slot=%u ctrlType=%#x rid=%llu", slot, ctrl_type,
			(unsigned long long)rid);
	ChiakiErrorCode err = chiaki_ctrl_send_message(&session->ctrl, CTRL_MESSAGE_TYPE_CTRLADD_EX, payload, sizeof(payload));
	if(err != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(session->log, "Multislot failed to send CTRLADD_EX for slot %u", slot);
		return err;
	}

	err = send_controller_connection_slot(&session->stream_connection, slot, true);
	CHIAKI_LOGI(session->log, "Multislot: slot %u add sent (cc_err=%d)", slot, err);
	return err;
}

CHIAKI_EXPORT ChiakiErrorCode chiaki_session_remove_user(ChiakiSession *session, uint8_t slot)
{
	if(slot < 1)
		return CHIAKI_ERR_INVALID_DATA;
	uint8_t payload = slot;
	ChiakiErrorCode err = chiaki_ctrl_send_message(&session->ctrl, CTRL_MESSAGE_TYPE_CONTROLLER_DEL, &payload, 1);
	send_controller_connection_slot(&session->stream_connection, slot, false);
	return err;
}
