// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#ifndef CHIAKI_JNI_VIDEO_DECODER_H
#define CHIAKI_JNI_VIDEO_DECODER_H

#include <jni.h>

#include <chiaki/thread.h>
#include <chiaki/log.h>
#include <chiaki/session.h>

typedef struct AMediaCodec AMediaCodec;
typedef struct ANativeWindow ANativeWindow;

typedef struct android_chiaki_video_decoder_t
{
	ChiakiLog *log;
	ChiakiMutex codec_mutex;
	AMediaCodec *codec;
	ANativeWindow *window;
	uint64_t timestamp_cur;
	ChiakiThread output_thread;
	bool shutdown_output;
	bool configuration_pending;
	volatile bool surface_valid;
	volatile bool needs_recreate;
	volatile uint64_t frames_since_output;
	volatile uint64_t frames_output;
	ChiakiSession *session;
	int32_t target_width;
	int32_t target_height;
	ChiakiCodec target_codec;
	// accessed via __atomic_* builtins (relaxed) from the network and UI threads
	uint64_t frames_queued;
	uint64_t bytes_queued;
	uint64_t frames_lost;
} AndroidChiakiVideoDecoder;

ChiakiErrorCode android_chiaki_video_decoder_init(AndroidChiakiVideoDecoder *decoder, ChiakiLog *log, int32_t target_width, int32_t target_height, ChiakiCodec codec);
void android_chiaki_video_decoder_fini(AndroidChiakiVideoDecoder *decoder);
void android_chiaki_video_decoder_set_surface(AndroidChiakiVideoDecoder *decoder, JNIEnv *env, jobject surface);
bool android_chiaki_video_decoder_video_sample(uint8_t *buf, size_t buf_size, int32_t frames_lost, bool frame_recovered, void *user);
void android_chiaki_video_decoder_get_stats(AndroidChiakiVideoDecoder *decoder, uint64_t *frames, uint64_t *bytes, uint64_t *frames_lost);
uint64_t android_chiaki_video_decoder_get_output_frames(AndroidChiakiVideoDecoder *decoder);

#endif
