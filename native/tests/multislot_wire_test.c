// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Exercise the production packet builders over a local datagram socket; crypto is a no-op.
#include <chiaki/gkcrypt.h>
#include <chiaki/multislot.h>
#include <sys/socket.h>
#include <unistd.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>

void chiaki_log(ChiakiLog *log, ChiakiLogLevel level, const char *fmt, ...) { (void)log; (void)level; (void)fmt; }
ChiakiErrorCode chiaki_gkcrypt_decrypt(ChiakiGKCrypt *crypt, uint64_t pos, uint8_t *buf, size_t size)
{ (void)crypt; (void)pos; (void)buf; (void)size; return CHIAKI_ERR_SUCCESS; }
ChiakiErrorCode chiaki_gkcrypt_gmac(ChiakiGKCrypt *crypt, uint64_t pos, const uint8_t *buf, size_t size, uint8_t *out)
{ (void)crypt; (void)pos; (void)buf; (void)size; memset(out, 0, 4); return CHIAKI_ERR_SUCCESS; }
int main(void)
{
    int sockets[2];
    assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, sockets) == 0);
    ChiakiTakion tk = {0};
    tk.sock = sockets[0];
    tk.gkcrypt_local = (ChiakiGKCrypt *)1;
    assert(chiaki_mutex_init(&tk.gkcrypt_local_mutex, true) == CHIAKI_ERR_SUCCESS);
    uint8_t packet[512];
    for(unsigned version = 9; version <= 12; version += 3) {
        tk.version = version;
        for(uint8_t slot = 0; slot < 7; slot++) {
            ChiakiFeedbackState state = { .orient_w = 1.0f };
            uint16_t seq = 0x1234 + slot;
            assert(chiaki_takion_send_feedback_state_slot(&tk, seq, &state, slot) == CHIAKI_ERR_SUCCESS);
            ssize_t length = recv(sockets[1], packet, sizeof(packet), 0);
            assert(length == 12 + (version <= 9 ? 25 : 28));
            assert(packet[0] == 6 && packet[1] == (seq >> 8) && packet[2] == (seq & 255));
            assert(packet[3] == 0 && packet[12] == (0xa0 | slot));
            uint8_t event[] = { (uint8_t)(0x80 | slot), 0x88, 0xff };
            assert(chiaki_takion_send_feedback_history_slot(&tk, seq, event, sizeof(event), slot) == CHIAKI_ERR_SUCCESS);
            length = recv(sockets[1], packet, sizeof(packet), 0);
            assert(length == 15 && packet[0] == 1 && packet[3] == 0);
            assert(memcmp(packet + 12, event, sizeof(event)) == 0);
        }
    }
    chiaki_mutex_fini(&tk.gkcrypt_local_mutex);
    close(sockets[0]); close(sockets[1]);
    puts("takion: v9/v12 state/history bytes match Asobi for slots 0..6");
    return 0;
}
