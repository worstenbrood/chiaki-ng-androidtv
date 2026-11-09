// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Run on Android against the production static library; no PS5/account required.
#include "../overrides/streamtransport.h"
#include <chiaki/takion.h>
#include <assert.h>
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

typedef struct {
    ChiakiStreamConnection connection;
    bool connected;
    unsigned disconnects;
} Events;

typedef struct {
    int socket;
    bool reject_handshake;
    atomic_bool stop;
} Peer;

static void write32(uint8_t *buf, uint32_t value) { value = htonl(value); memcpy(buf, &value, 4); }
static void write16(uint8_t *buf, uint16_t value) { value = htons(value); memcpy(buf, &value, 2); }

static void *peer_thread(void *user)
{
    Peer *peer = user;
    uint8_t packet[1500];
    uint32_t client_tag = 0;
    while(!atomic_load(&peer->stop)) {
        struct sockaddr_in from;
        socklen_t from_size = sizeof(from);
        ssize_t length = recvfrom(peer->socket, packet, sizeof(packet), 0, (struct sockaddr *)&from, &from_size);
        if(length < 17 || packet[0] != 0) continue;
        uint8_t response[65] = {0};
        size_t response_size;
        if(peer->reject_handshake) {
            response_size = 1; // Fail all handshake attempts without waiting for network timeouts.
        } else if(packet[13] == 1 && length >= 33) {
            memcpy(&client_tag, packet + 17, 4);
            client_tag = ntohl(client_tag);
            response[13] = 2; // INIT_ACK: 16-byte header, 16-byte init, 32-byte cookie.
            write16(response + 15, 52);
            write32(response + 17, 0x12345678);
            write32(response + 21, 0x19000);
            write16(response + 25, 100);
            write16(response + 27, 100);
            write32(response + 29, 0x12345678);
            response_size = sizeof(response);
        } else if(packet[13] == 10) {
            response[13] = 11; // COOKIE_ACK.
            write16(response + 15, 4);
            response_size = 17;
        } else continue;
        write32(response + 1, client_tag);
        assert(sendto(peer->socket, response, response_size, 0, (struct sockaddr *)&from, from_size) == (ssize_t)response_size);
    }
    return NULL;
}

static void event_callback(ChiakiTakionEvent *event, void *user)
{
    Events *events = user;
    ChiakiStreamConnection *connection = &events->connection;
    assert(chiaki_mutex_lock(&connection->state_mutex) == CHIAKI_ERR_SUCCESS);
    if(event->type == CHIAKI_TAKION_EVENT_TYPE_CONNECTED) {
        events->connected = true;
        chiaki_cond_signal(&connection->state_cond);
    } else if(event->type == CHIAKI_TAKION_EVENT_TYPE_DISCONNECT) {
        events->disconnects++;
        stream_transport_disconnected_locked(connection);
    }
    assert(chiaki_mutex_unlock(&connection->state_mutex) == CHIAKI_ERR_SUCCESS);
}

static bool connected(void *user) { return ((Events *)user)->connected; }

static void run_case(bool reject_handshake, bool owns_socket)
{
    Events events = {0};
    assert(chiaki_mutex_init(&events.connection.state_mutex, false) == CHIAKI_ERR_SUCCESS);
    assert(chiaki_cond_init(&events.connection.state_cond) == CHIAKI_ERR_SUCCESS);
    assert(!stream_transport_wait_finished(&events.connection));
    Peer peer = { .socket = socket(AF_INET, SOCK_DGRAM, 0), .reject_handshake = reject_handshake };
    assert(peer.socket >= 0);
    struct timeval timeout = { .tv_usec = 100000 };
    assert(setsockopt(peer.socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) == 0);
    struct sockaddr_in address = { .sin_family = AF_INET, .sin_addr.s_addr = htonl(INADDR_LOOPBACK) };
    assert(bind(peer.socket, (struct sockaddr *)&address, sizeof(address)) == 0);
    socklen_t address_size = sizeof(address);
    assert(getsockname(peer.socket, (struct sockaddr *)&address, &address_size) == 0);
    ChiakiThread peer_worker;
    assert(chiaki_thread_create(&peer_worker, peer_thread, &peer) == CHIAKI_ERR_SUCCESS);
    ChiakiTakion takion = {0};
    ChiakiTakionConnectInfo info = {
        .sa = (struct sockaddr *)&address, .sa_len = sizeof(address),
        .protocol_version = 12, .close_socket = owns_socket,
        .cb = event_callback, .cb_user = &events,
    };
    assert(chiaki_takion_connect(&takion, &info, NULL) == CHIAKI_ERR_SUCCESS);
    int socket_fd = takion.sock;
    assert(chiaki_mutex_lock(&events.connection.state_mutex) == CHIAKI_ERR_SUCCESS);
    if(!reject_handshake) {
        assert(chiaki_cond_timedwait_pred(&events.connection.state_cond, &events.connection.state_mutex,
            3000, connected, &events) == CHIAKI_ERR_SUCCESS);
        // Simulate the receiver failing during an otherwise established stream.
        assert(shutdown(socket_fd, SHUT_RD) == 0);
    }
    assert(chiaki_cond_timedwait_pred(&events.connection.state_cond, &events.connection.state_mutex,
        3000, stream_transport_wait_finished, &events.connection) == CHIAKI_ERR_SUCCESS);
    assert(events.connection.state_failed && events.disconnects == 1);
    assert(chiaki_mutex_unlock(&events.connection.state_mutex) == CHIAKI_ERR_SUCCESS);
    // Let the receive thread finish. Previously it closed the socket and destroyed
    // the send-buffer mutex here, leaving other stream threads running against both.
    usleep(100000);
    assert(fcntl(socket_fd, F_GETFD) >= 0);
    uint8_t data = 0x55;
    assert(chiaki_takion_send_raw(&takion, &data, 1) == CHIAKI_ERR_SUCCESS);
    if(!reject_handshake) {
        assert(takion.send_buffer.packets != NULL);
        assert(chiaki_takion_send_message_data(&takion, 1, 1, &data, 1, NULL) == CHIAKI_ERR_SUCCESS);
    }
    chiaki_takion_close(&takion);
    if(owns_socket) {
        assert(CHIAKI_SOCKET_IS_INVALID(takion.sock));
        assert(fcntl(socket_fd, F_GETFD) == -1 && errno == EBADF);
    } else {
        assert(fcntl(socket_fd, F_GETFD) >= 0);
        close(socket_fd);
    }
    atomic_store(&peer.stop, true);
    assert(chiaki_thread_join(&peer_worker, NULL) == CHIAKI_ERR_SUCCESS);
    close(peer.socket);
    chiaki_cond_fini(&events.connection.state_cond);
    chiaki_mutex_fini(&events.connection.state_mutex);
}

int main(void)
{
    for(unsigned repeat = 0; repeat < 5; repeat++) {
        run_case(false, true);
        run_case(false, false);
        run_case(true, true);
        run_case(true, false);
    }
    puts("PASS: 20 transport failures wake the owner; senders remain valid until close; socket ownership respected");
    return 0;
}
