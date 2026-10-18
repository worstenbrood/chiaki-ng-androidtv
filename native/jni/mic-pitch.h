// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#ifndef CHIAKI_MIC_PITCH_H
#define CHIAKI_MIC_PITCH_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Real-time pitch shifter for the mic path (Sonic library). Frames are pushed
 * through in place at a constant size: the shifter buffers internally so the
 * opus encoder keeps receiving exactly one 10 ms frame per call.
 */
typedef struct ChiakiMicPitch ChiakiMicPitch;

ChiakiMicPitch *chiaki_mic_pitch_new(void);
void chiaki_mic_pitch_free(ChiakiMicPitch *pitch);

/** Enable/disable and set the pitch factor (1.0 = unchanged). Thread-safe. */
void chiaki_mic_pitch_set(ChiakiMicPitch *pitch, bool enabled, float factor);

/** Pitch-shift num_shorts interleaved 16-bit samples in place. No-op when disabled. */
void chiaki_mic_pitch_process(ChiakiMicPitch *pitch, int16_t *pcm, size_t num_shorts);

#ifdef __cplusplus
}
#endif

#endif
