// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#include <sonic.h>

#include "mic-pitch.h"

#define MIC_PITCH_SAMPLE_RATE 48000
#define MIC_PITCH_CHANNELS 2
// sonic's pitch shift holds a few pitch periods internally and its per-call output
// varies around the input rate; the ring must stay comfortably above that latency or
// it underruns and the consumer permanently outruns the producer (silence gaps = the
// distortion heard after changing pitch). 32 frames (~64 ms) is far above any latency.
#define MIC_PITCH_RING_SHORTS (480 * MIC_PITCH_CHANNELS * 32)

struct ChiakiMicPitch
{
	sonicStream stream;
	int16_t *ring;
	size_t fill;
	bool enabled;
	float factor;
	pthread_mutex_t mutex;
};

ChiakiMicPitch *chiaki_mic_pitch_new(void)
{
	ChiakiMicPitch *pitch = calloc(1, sizeof(ChiakiMicPitch));
	if(!pitch)
		return NULL;
	pitch->stream = sonicCreateStream(MIC_PITCH_SAMPLE_RATE, MIC_PITCH_CHANNELS);
	if(!pitch->stream)
	{
		free(pitch);
		return NULL;
	}
	pitch->ring = malloc(MIC_PITCH_RING_SHORTS * sizeof(int16_t));
	if(!pitch->ring)
	{
		sonicDestroyStream(pitch->stream);
		free(pitch);
		return NULL;
	}
	pitch->factor = 1.0f;
	pthread_mutex_init(&pitch->mutex, NULL);
	return pitch;
}

void chiaki_mic_pitch_free(ChiakiMicPitch *pitch)
{
	if(!pitch)
		return;
	pthread_mutex_destroy(&pitch->mutex);
	sonicDestroyStream(pitch->stream);
	free(pitch->ring);
	free(pitch);
}

void chiaki_mic_pitch_set(ChiakiMicPitch *pitch, bool enabled, float factor)
{
	if(!pitch)
		return;
	pthread_mutex_lock(&pitch->mutex);
	if(factor != pitch->factor)
	{
		pitch->factor = factor;
		sonicSetPitch(pitch->stream, factor);
	}
	if(pitch->enabled && !enabled)
	{
		// drop buffered speech so re-enabling does not burst stale audio
		pitch->fill = 0;
		int16_t scratch[512 * MIC_PITCH_CHANNELS];
		while(sonicSamplesAvailable(pitch->stream) > 0)
			if(sonicReadShortFromStream(pitch->stream, scratch, 512) <= 0)
				break;
	}
	pitch->enabled = enabled;
	pthread_mutex_unlock(&pitch->mutex);
}

void chiaki_mic_pitch_process(ChiakiMicPitch *pitch, int16_t *pcm, size_t num_shorts)
{
	if(!pitch)
		return;
	pthread_mutex_lock(&pitch->mutex);
	if(!pitch->enabled)
	{
		pthread_mutex_unlock(&pitch->mutex);
		return;
	}

	int frames = (int)(num_shorts / MIC_PITCH_CHANNELS);
	sonicWriteShortToStream(pitch->stream, pcm, frames);

	// fully drain sonic into the ring every call so its internal backlog never grows
	// unbounded and the ring fill settles at sonic's latency (well above one frame)
	int16_t out[512 * MIC_PITCH_CHANNELS];
	while(sonicSamplesAvailable(pitch->stream) > 0)
	{
		size_t space = MIC_PITCH_RING_SHORTS - pitch->fill;
		if(space < (size_t)MIC_PITCH_CHANNELS)
			break; // ring full (should not happen; bounds latency if sonic over-produces)
		int max_frames = (int)(space / MIC_PITCH_CHANNELS);
		if(max_frames > 512)
			max_frames = 512;
		int n = sonicReadShortFromStream(pitch->stream, out, max_frames);
		if(n <= 0)
			break;
		memcpy(pitch->ring + pitch->fill, out, (size_t)n * MIC_PITCH_CHANNELS * sizeof(int16_t));
		pitch->fill += (size_t)n * MIC_PITCH_CHANNELS;
	}

	// pop exactly one frame; zero-pad only during warmup before sonic has produced a frame
	size_t take = num_shorts < pitch->fill ? num_shorts : pitch->fill;
	memcpy(pcm, pitch->ring, take * sizeof(int16_t));
	if(take < num_shorts)
		memset(pcm + take, 0, (num_shorts - take) * sizeof(int16_t));
	if(take < pitch->fill)
		memmove(pitch->ring, pitch->ring + take, (pitch->fill - take) * sizeof(int16_t));
	pitch->fill -= take;

	pthread_mutex_unlock(&pitch->mutex);
}
