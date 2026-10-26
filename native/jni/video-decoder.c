// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#include "video-decoder.h"

#include <jni.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window_jni.h>
#include <android/api-level.h>

#include <string.h>
#include <chiaki/videoreceiver.h>

// small grace period instead of instantly dropping a whole frame when the codec input
// is momentarily full (a burst of packets is normal at frame boundaries)
#define INPUT_BUFFER_TIMEOUT_MS 5

static void *android_chiaki_video_decoder_output_thread_func(void *user);

ChiakiErrorCode android_chiaki_video_decoder_init(AndroidChiakiVideoDecoder *decoder, ChiakiLog *log, int32_t target_width, int32_t target_height, ChiakiCodec codec)
{
	decoder->log = log;
	decoder->codec = NULL;
	decoder->window = NULL;
	decoder->timestamp_cur = 0;
	decoder->target_width = target_width;
	decoder->target_height = target_height;
	decoder->target_codec = codec;
	__atomic_store_n(&decoder->shutdown_output, false, __ATOMIC_RELEASE);
	__atomic_store_n(&decoder->surface_valid, false, __ATOMIC_RELEASE);
	__atomic_store_n(&decoder->needs_recreate, false, __ATOMIC_RELEASE);
	decoder->configuration_pending = false;
	decoder->session = NULL;
	return chiaki_mutex_init(&decoder->codec_mutex, false);
}

// must be called with codec_mutex held; returns with codec_mutex held (may briefly release it for the join)
static void kill_decoder_locked(AndroidChiakiVideoDecoder *decoder)
{
	__atomic_store_n(&decoder->shutdown_output, true, __ATOMIC_RELEASE);
	AMediaCodec_stop(decoder->codec);
	// Join even when there is no free input buffer: never delete a codec
	// while its output thread may still be using it.
	chiaki_mutex_unlock(&decoder->codec_mutex);
	chiaki_thread_join(&decoder->output_thread, NULL);
	chiaki_mutex_lock(&decoder->codec_mutex);
	AMediaCodec_delete(decoder->codec);
	decoder->codec = NULL;
	__atomic_store_n(&decoder->shutdown_output, false, __ATOMIC_RELEASE);
}

static void kill_decoder(AndroidChiakiVideoDecoder *decoder)
{
	chiaki_mutex_lock(&decoder->codec_mutex);
	kill_decoder_locked(decoder);
	chiaki_mutex_unlock(&decoder->codec_mutex);
}

void android_chiaki_video_decoder_fini(AndroidChiakiVideoDecoder *decoder)
{
	if(decoder->codec)
		kill_decoder(decoder);
	if(decoder->window)
	{
		ANativeWindow_release(decoder->window);
		decoder->window = NULL;
	}
	chiaki_mutex_fini(&decoder->codec_mutex);
}

// must be called with codec_mutex held; creates + starts the codec on decoder->window
static bool start_codec_locked(AndroidChiakiVideoDecoder *decoder)
{
	const char *mime = chiaki_codec_is_h265(decoder->target_codec) ? "video/hevc" : "video/avc";
	decoder->codec = AMediaCodec_createDecoderByType(mime);
	if(!decoder->codec)
	{
		CHIAKI_LOGE(decoder->log, "Failed to create AMediaCodec for mime type %s", mime);
		return false;
	}

	AMediaFormat *format = AMediaFormat_new();
	AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, decoder->target_width);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, decoder->target_height);
	if(android_get_device_api_level() >= 30)
		// AMEDIAFORMAT_KEY_LOW_LATENCY is __INTRODUCED_IN(30), so use the literal key with a runtime check
		AMediaFormat_setInt32(format, "android.media.key.lowlatency", 1);

	media_status_t r = AMediaCodec_configure(decoder->codec, format, decoder->window, NULL, 0);
	AMediaFormat_delete(format);
	if(r != AMEDIA_OK)
	{
		CHIAKI_LOGE(decoder->log, "AMediaCodec_configure() failed: %d", (int)r);
		goto error;
	}

	r = AMediaCodec_start(decoder->codec);
	if(r != AMEDIA_OK)
	{
		CHIAKI_LOGE(decoder->log, "AMediaCodec_start() failed: %d", (int)r);
		goto error;
	}

	ChiakiErrorCode err = chiaki_thread_create(&decoder->output_thread, android_chiaki_video_decoder_output_thread_func, decoder);
	if(err != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(decoder->log, "Failed to create output thread for AMediaCodec");
		goto error;
	}

	__atomic_store_n(&decoder->surface_valid, decoder->window != NULL, __ATOMIC_RELEASE);
	decoder->configuration_pending = true;
	__atomic_store_n(&decoder->frames_since_output, 0, __ATOMIC_RELAXED);
	return true;

error:
	AMediaCodec_delete(decoder->codec);
	decoder->codec = NULL;
	return false;
}

void android_chiaki_video_decoder_set_surface(AndroidChiakiVideoDecoder *decoder, JNIEnv *env, jobject surface)
{
	chiaki_mutex_lock(&decoder->codec_mutex);

	if(!surface)
	{
		if(decoder->codec)
		{
#if __ANDROID_API__ >= 23
			// keep the codec alive so it does not lose reference frames; just stop rendering
			__atomic_store_n(&decoder->surface_valid, false, __ATOMIC_RELEASE);
			media_status_t sr = AMediaCodec_setOutputSurface(decoder->codec, NULL);
			if(sr != AMEDIA_OK)
			{
				// Shield rejects the NULL swap (-22): the codec stays bound to the dying
				// surface and a later swap to a fresh one renders nothing -> tear down so
				// resume re-initializes the codec on the new surface (upstream behavior)
				CHIAKI_LOGW(decoder->log, "AMediaCodec_setOutputSurface(NULL) failed: %d, killing codec", (int)sr);
				kill_decoder_locked(decoder);
			}
			if(decoder->window)
				ANativeWindow_release(decoder->window);
			decoder->window = NULL;
			CHIAKI_LOGI(decoder->log, "Video Decoder rendering disabled after surface was removed");
#else
			kill_decoder_locked(decoder);
			CHIAKI_LOGI(decoder->log, "Decoder shut down after surface was removed");
#endif
		}
		goto beach;
	}

	if(decoder->codec)
	{
#if __ANDROID_API__ >= 23
		CHIAKI_LOGI(decoder->log, "Video decoder already initialized, swapping surface");
		ANativeWindow *new_window = ANativeWindow_fromSurface(env, surface);
		media_status_t r = AMediaCodec_setOutputSurface(decoder->codec, new_window);
		if(decoder->window)
			ANativeWindow_release(decoder->window);
		decoder->window = new_window;
		__atomic_store_n(&decoder->surface_valid, true, __ATOMIC_RELEASE);
		if(r != AMEDIA_OK)
		{
			// some devices reject the surface swap once the old surface died; rebuild the codec
			CHIAKI_LOGE(decoder->log, "AMediaCodec_setOutputSurface() failed: %d, will recreate codec", (int)r);
			__atomic_store_n(&decoder->needs_recreate, true, __ATOMIC_RELEASE);
		}
#else
		CHIAKI_LOGE(decoder->log, "Video Decoder already initialized");
#endif
		goto beach;
	}

	decoder->window = ANativeWindow_fromSurface(env, surface);

	CHIAKI_LOGI(decoder->log, "Initializing decoder");
	if(!start_codec_locked(decoder))
	{
		ANativeWindow_release(decoder->window);
		decoder->window = NULL;
	}

beach:
	chiaki_mutex_unlock(&decoder->codec_mutex);
}

bool android_chiaki_video_decoder_video_sample(uint8_t *buf, size_t buf_size, int32_t frames_lost, bool frame_recovered, void *user)
{
	bool r = true;
	AndroidChiakiVideoDecoder *decoder = user;
	__atomic_add_fetch(&decoder->frames_queued, 1, __ATOMIC_RELAXED);
	__atomic_add_fetch(&decoder->bytes_queued, (uint64_t)buf_size, __ATOMIC_RELAXED);
	if(frames_lost > 0)
		__atomic_add_fetch(&decoder->frames_lost, (uint64_t)frames_lost, __ATOMIC_RELAXED);
	chiaki_mutex_lock(&decoder->codec_mutex);

	if(__atomic_load_n(&decoder->shutdown_output, __ATOMIC_ACQUIRE))
		goto beach;

	if(__atomic_exchange_n(&decoder->needs_recreate, false, __ATOMIC_ACQ_REL))
	{
		CHIAKI_LOGE(decoder->log, "Video Decoder recreating codec after output surface became invalid");
		if(decoder->codec)
		{
			kill_decoder_locked(decoder);
		}
		if(!decoder->window || !start_codec_locked(decoder))
			__atomic_store_n(&decoder->needs_recreate, true, __ATOMIC_RELEASE); // retry on the next frame
		else if(decoder->session)
			chiaki_session_request_idr(decoder->session);
	}

	if(!decoder->codec)
	{
		CHIAKI_LOGV(decoder->log, "Received video data, but decoder is not initialized!");
		goto beach;
	}

	// a fresh codec (e.g. after a resume re-init) needs a keyframe; if the single IDR
	// request after init never landed (takion reliable-channel gap), keep asking
	// until the decoder actually produces output again
	if(__atomic_load_n(&decoder->surface_valid, __ATOMIC_ACQUIRE) && decoder->session &&
	   __atomic_add_fetch(&decoder->frames_since_output, 1, __ATOMIC_RELAXED) > 120)
	{
		__atomic_store_n(&decoder->frames_since_output, 60, __ATOMIC_RELAXED);
		CHIAKI_LOGI(decoder->log, "No decoder output for ~2s, re-requesting IDR");
		chiaki_session_request_idr(decoder->session);
	}

	// The receiver only sends the codec header on a profile switch. After a
	// surface loss a new codec needs that header again, before the next IDR.
	// Access the active profile on this receiver thread, which owns it.
	if(decoder->configuration_pending && decoder->session)
	{
		ChiakiVideoReceiver *receiver = decoder->session->stream_connection.video_receiver;
		if(receiver && receiver->profile_cur >= 0 &&
		   (size_t)receiver->profile_cur < receiver->profiles_count)
		{
			ChiakiVideoProfile *profile = &receiver->profiles[receiver->profile_cur];
			ssize_t index = AMediaCodec_dequeueInputBuffer(decoder->codec, INPUT_BUFFER_TIMEOUT_MS * 1000);
			if(index < 0)
			{
				r = false;
				goto beach;
			}
			size_t capacity = 0;
			uint8_t *input = AMediaCodec_getInputBuffer(decoder->codec, (size_t)index, &capacity);
			if(!input || !profile->header_sz || profile->header_sz > capacity)
			{
				AMediaCodec_queueInputBuffer(decoder->codec, (size_t)index, 0, 0, 0, 0);
				r = false;
				goto beach;
			}
			memcpy(input, profile->header, profile->header_sz);
			if(AMediaCodec_queueInputBuffer(decoder->codec, (size_t)index, 0,
				profile->header_sz, decoder->timestamp_cur++, AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) != AMEDIA_OK)
			{
				r = false;
				goto beach;
			}
			decoder->configuration_pending = false;
			CHIAKI_LOGI(decoder->log, "Video Decoder restored active profile configuration");
			if(buf == profile->header)
				goto beach;
		}
	}

	while(buf_size > 0)
	{
		ssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, INPUT_BUFFER_TIMEOUT_MS * 1000);
		if(codec_buf_index < 0)
		{
			CHIAKI_LOGV(decoder->log, "Codec input full, dropping video frame");
			r = false;
			goto beach;
		}

		size_t codec_buf_size;
		uint8_t *codec_buf = AMediaCodec_getInputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
		size_t codec_sample_size = buf_size;
		if(codec_sample_size > codec_buf_size)
		{
			//CHIAKI_LOGD(decoder->log, "Sample is bigger than buffer, splitting");
			codec_sample_size = codec_buf_size;
		}
		memcpy(codec_buf, buf, codec_sample_size);
		media_status_t r = AMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, codec_sample_size, decoder->timestamp_cur++, 0); // timestamp just raised by 1 for maximum realtime
		if(r != AMEDIA_OK)
		{
			CHIAKI_LOGE(decoder->log, "AMediaCodec_queueInputBuffer() failed: %d", (int)r);
		}
		buf += codec_sample_size;
		buf_size -= codec_sample_size;

	}

beach:
	chiaki_mutex_unlock(&decoder->codec_mutex);
	return r;
}

static void *android_chiaki_video_decoder_output_thread_func(void *user)
{
	AndroidChiakiVideoDecoder *decoder = user;
	int render_failures = 0;
	int dequeue_errors = 0;

	while(1)
	{
		AMediaCodecBufferInfo info;
		ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder->codec, &info, -1);
		if(status >= 0)
		{
			dequeue_errors = 0;
			if(info.size != 0)
			{
				__atomic_store_n(&decoder->frames_since_output, 0, __ATOMIC_RELAXED);
				__atomic_add_fetch(&decoder->frames_output, 1, __ATOMIC_RELAXED);
			}
			bool render = info.size != 0 && __atomic_load_n(&decoder->surface_valid, __ATOMIC_ACQUIRE);
			media_status_t rr = AMediaCodec_releaseOutputBuffer(decoder->codec, (size_t)status, render);
			if(render && rr != AMEDIA_OK)
			{
				// output surface died under us (e.g. surface destroyed during a background cycle);
				// after a short burst the codec will never recover on its own
				if(++render_failures > 30)
				{
					CHIAKI_LOGE(decoder->log, "Video Decoder output rendering failed %d times (%d), requesting codec recreation", render_failures, (int)rr);
					__atomic_store_n(&decoder->needs_recreate, true, __ATOMIC_RELEASE);
					break;
				}
			}
			else if(render)
				render_failures = 0;
			if(info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM)
			{
				CHIAKI_LOGI(decoder->log, "AMediaCodec reported EOS");
				break;
			}
		}
		else
		{
			chiaki_mutex_lock(&decoder->codec_mutex);
			bool shutdown = __atomic_load_n(&decoder->shutdown_output, __ATOMIC_ACQUIRE);
			chiaki_mutex_unlock(&decoder->codec_mutex);
			if(shutdown)
			{
				CHIAKI_LOGI(decoder->log, "Video Decoder Output Thread detected shutdown after reported error");
				break;
			}
			if(++dequeue_errors > 500)
			{
				CHIAKI_LOGE(decoder->log, "Video Decoder output thread saw %d consecutive dequeue errors, requesting codec recreation", dequeue_errors);
				__atomic_store_n(&decoder->needs_recreate, true, __ATOMIC_RELEASE);
				break;
			}
		}
	}

	CHIAKI_LOGI(decoder->log, "Video Decoder Output Thread exiting");

	return NULL;
}
void android_chiaki_video_decoder_get_stats(AndroidChiakiVideoDecoder *decoder, uint64_t *frames, uint64_t *bytes, uint64_t *frames_lost)
{
	*frames = __atomic_load_n(&decoder->frames_queued, __ATOMIC_RELAXED);
	*bytes = __atomic_load_n(&decoder->bytes_queued, __ATOMIC_RELAXED);
	*frames_lost = __atomic_load_n(&decoder->frames_lost, __ATOMIC_RELAXED);
}

uint64_t android_chiaki_video_decoder_get_output_frames(AndroidChiakiVideoDecoder *decoder)
{
	return __atomic_load_n(&decoder->frames_output, __ATOMIC_RELAXED);
}
