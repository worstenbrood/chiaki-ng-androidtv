// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
#ifndef CHIAKI_TV_STREAMTRANSPORT_H
#define CHIAKI_TV_STREAMTRANSPORT_H

#include <chiaki/streamconnection.h>

// All accesses are protected by the stream connection's state_mutex.
static inline bool stream_transport_wait_finished(void *user)
{
    ChiakiStreamConnection *connection = user;
    return connection->state_finished || connection->state_failed ||
        connection->should_stop || connection->remote_disconnected;
}

static inline void stream_transport_disconnected_locked(ChiakiStreamConnection *connection)
{
    connection->state_failed = true;
    chiaki_cond_signal(&connection->state_cond);
}

#endif
