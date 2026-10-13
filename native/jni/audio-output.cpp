// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#include "audio-output.h"

#include "circular-buf.hpp"

#include <chiaki/log.h>
#include <chiaki/thread.h>

#include <oboe/Oboe.h>

#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <thread>

#define BUFFER_CHUNK_SIZE 1024
#define BUFFER_CHUNKS_COUNT 32

using AudioBuffer = CircularBuffer<BUFFER_CHUNKS_COUNT, BUFFER_CHUNK_SIZE>;

class AudioOutput;

class AudioOutputCallback: public oboe::AudioStreamCallback
{
private:
	AudioOutput *audio_output;

public:
	AudioOutputCallback(AudioOutput *audio_output) : audio_output(audio_output) {}
	oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
	void onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error) override;
	void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;
};

struct AudioOutput
{
	ChiakiLog *log;
	oboe::ManagedStream stream;
	AudioOutputCallback stream_callback;
	AudioBuffer buf;
	std::atomic<float> volume{1.0f};
	std::atomic<bool> shutting_down{false};
	std::weak_ptr<AudioOutput> self_weak;
	int32_t device_id = 0;
	int32_t buffer_ms = 0;
	uint32_t channels = 0;
	uint32_t rate = 0;
	std::mutex mutex;

	AudioOutput() : stream_callback(this) {}
};

extern "C" void *android_chiaki_audio_output_new(ChiakiLog *log)
{
	auto holder = new std::shared_ptr<AudioOutput>(std::make_shared<AudioOutput>());
	(*holder)->log = log;
	(*holder)->self_weak = *holder;
	return holder;
}

extern "C" void android_chiaki_audio_output_free(void *audio_output)
{
	if(!audio_output)
		return;
	auto holder = reinterpret_cast<std::shared_ptr<AudioOutput> *>(audio_output);
	{
		std::lock_guard<std::mutex> lock((*holder)->mutex);
		(*holder)->shutting_down = true;
		(*holder)->stream = nullptr;
	}
	delete holder;
}

static AudioOutput *get_ao(void *audio_output)
{
	return (*reinterpret_cast<std::shared_ptr<AudioOutput> *>(audio_output)).get();
}

static void open_stream(AudioOutput *ao)
{
	oboe::AudioStreamBuilder builder;
	builder.setPerformanceMode(oboe::PerformanceMode::LowLatency)
		->setSharingMode(oboe::SharingMode::Exclusive)
		->setFormat(oboe::AudioFormat::I16)
		->setChannelCount(ao->channels)
		->setSampleRate(ao->rate)
		->setCallback(&ao->stream_callback);
	if(ao->buffer_ms > 0)
		builder.setBufferCapacityInFrames(static_cast<int32_t>(ao->buffer_ms) * static_cast<int32_t>(ao->rate) / 1000);

	if(ao->device_id != 0)
	{
		builder.setDeviceId(ao->device_id);
		auto result = builder.openManagedStream(ao->stream);
		if(result == oboe::Result::OK)
			CHIAKI_LOGI(ao->log, "Audio Output opened Oboe stream on selected device");
		else
		{
			CHIAKI_LOGE(ao->log, "Audio Output failed to open Oboe stream on selected device: %s, falling back to default device", oboe::convertToText(result));
			ao->stream = nullptr;
			oboe::AudioStreamBuilder fallback_builder;
			fallback_builder.setPerformanceMode(oboe::PerformanceMode::LowLatency)
				->setSharingMode(oboe::SharingMode::Exclusive)
				->setFormat(oboe::AudioFormat::I16)
				->setChannelCount(ao->channels)
				->setSampleRate(ao->rate)
				->setCallback(&ao->stream_callback);
			if(ao->buffer_ms > 0)
				fallback_builder.setBufferCapacityInFrames(static_cast<int32_t>(ao->buffer_ms) * static_cast<int32_t>(ao->rate) / 1000);
			result = fallback_builder.openManagedStream(ao->stream);
			if(result != oboe::Result::OK)
			{
				CHIAKI_LOGE(ao->log, "Audio Output failed to open Oboe stream: %s", oboe::convertToText(result));
				return;
			}
			CHIAKI_LOGI(ao->log, "Audio Output opened Oboe stream on default device");
		}
	}
	else
	{
		auto result = builder.openManagedStream(ao->stream);
		if(result == oboe::Result::OK)
			CHIAKI_LOGI(ao->log, "Audio Output opened Oboe stream");
		else
		{
			CHIAKI_LOGE(ao->log, "Audio Output failed to open Oboe stream: %s", oboe::convertToText(result));
			return;
		}
	}

	if(ao->buffer_ms > 0)
	{
		auto r = ao->stream->setBufferSizeInFrames(ao->stream->getBufferCapacityInFrames());
		if(r != oboe::Result::OK)
			CHIAKI_LOGW(ao->log, "Audio Output failed to set buffer size");
		CHIAKI_LOGI(ao->log, "Audio Output buffer: %i/%i frames (%i ms requested)",
			ao->stream->getBufferSizeInFrames(), ao->stream->getBufferCapacityInFrames(), ao->buffer_ms);
	}

	auto result = ao->stream->start();
	if(result == oboe::Result::OK)
		CHIAKI_LOGI(ao->log, "Audio Output started Oboe stream");
	else
		CHIAKI_LOGE(ao->log, "Audio Output failed to start Oboe stream: %s", oboe::convertToText(result));
}

// Reopen the stream a moment after it died (e.g. the audio server disconnected it while the app
// was backgrounded). Delayed so the server has settled; skipped if a healthy stream is alive.
static void schedule_reopen(AudioOutput *ao)
{
	std::weak_ptr<AudioOutput> weak = ao->self_weak;
	std::thread([weak]() {
		std::this_thread::sleep_for(std::chrono::milliseconds(500));
		auto ao = weak.lock();
		if(!ao || ao->shutting_down)
			return;
		std::lock_guard<std::mutex> lock(ao->mutex);
		if(ao->shutting_down || ao->channels == 0 || ao->rate == 0)
			return;
		if(ao->stream)
		{
			auto state = ao->stream->getState();
			if(state != oboe::StreamState::Closed && state != oboe::StreamState::Disconnected)
				return;
			ao->stream = nullptr;
		}
		CHIAKI_LOGI(ao->log, "Audio Output reopening stream after error");
		uint8_t scratch[4096];
		while(ao->buf.Pop(scratch, sizeof(scratch)) > 0) {}
		open_stream(ao.get());
	}).detach();
}

extern "C" void android_chiaki_audio_output_settings(uint32_t channels, uint32_t rate, void *audio_output)
{
	auto ao = get_ao(audio_output);
	std::lock_guard<std::mutex> lock(ao->mutex);
	ao->channels = channels;
	ao->rate = rate;
	open_stream(ao);
}

extern "C" void android_chiaki_audio_output_set_volume(void *audio_output, float volume)
{
	auto ao = get_ao(audio_output);
	if(volume < 0.0f)
		volume = 0.0f;
	if(volume > 1.0f)
		volume = 1.0f;
	ao->volume.store(volume, std::memory_order_relaxed);
}

extern "C" void android_chiaki_audio_output_set_device_id(void *audio_output, int32_t device_id)
{
	auto ao = get_ao(audio_output);
	std::lock_guard<std::mutex> lock(ao->mutex);
	if(ao->device_id == device_id)
		return;
	ao->device_id = device_id;
	if(ao->channels == 0 || ao->rate == 0)
		return;
	CHIAKI_LOGI(ao->log, "Audio Output switching output device live");
	ao->stream = nullptr;
	open_stream(ao);
}

extern "C" void android_chiaki_audio_output_set_buffer_ms(void *audio_output, int32_t buffer_ms)
{
	auto ao = get_ao(audio_output);
	ao->buffer_ms = buffer_ms;
}

extern "C" void android_chiaki_audio_output_restart(void *audio_output)
{
	auto ao = get_ao(audio_output);
	std::lock_guard<std::mutex> lock(ao->mutex);
	if(ao->channels == 0 || ao->rate == 0)
		return;
	CHIAKI_LOGI(ao->log, "Audio Output restarting stream (e.g. after background suspension)");
	uint8_t scratch[4096];
	while(ao->buf.Pop(scratch, sizeof(scratch)) > 0) {}
	ao->stream = nullptr;
	open_stream(ao);
}

extern "C" void android_chiaki_audio_output_frame(int16_t *buf, size_t samples_count, void *audio_output)
{
	auto ao = get_ao(audio_output);

	size_t buf_size = samples_count * sizeof(int16_t);
	size_t pushed = ao->buf.Push(reinterpret_cast<uint8_t *>(buf), buf_size);
	if(pushed < buf_size)
		CHIAKI_LOGV(ao->log, "Audio Output Buffer Overflow!");
}

oboe::DataCallbackResult AudioOutputCallback::onAudioReady(oboe::AudioStream *stream, void *audio_data, int32_t num_frames)
{
	if(stream->getFormat() != oboe::AudioFormat::I16)
	{
		CHIAKI_LOGE(audio_output->log, "Oboe stream has invalid format in callback");
		return oboe::DataCallbackResult::Stop;
	}

	int32_t bytes_per_frame = stream->getBytesPerFrame();
	size_t buf_size_requested = static_cast<size_t>(bytes_per_frame * num_frames);
	auto buf = reinterpret_cast<uint8_t *>(audio_data);

	size_t buf_size_delivered = audio_output->buf.Pop(buf, buf_size_requested);
	//CHIAKI_LOGW(audio_output->log, "Delivered %llu", (unsigned long long)buf_size_delivered);

	if(buf_size_delivered < buf_size_requested)
	{
		CHIAKI_LOGV(audio_output->log, "Audio Output Buffer Underflow!");
		memset(buf + buf_size_delivered, 0, buf_size_requested - buf_size_delivered);
	}

	float vol = audio_output->volume.load(std::memory_order_relaxed);
	if(vol != 1.0f)
	{
		auto samples = reinterpret_cast<int16_t *>(audio_data);
		size_t num_samples = buf_size_delivered / sizeof(int16_t);
		for(size_t i = 0; i < num_samples; i++)
			samples[i] = static_cast<int16_t>(static_cast<float>(samples[i]) * vol);
	}

	return oboe::DataCallbackResult::Continue;
}

void AudioOutputCallback::onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error)
{
	CHIAKI_LOGE(audio_output->log, "Oboe reported error before close: %s", oboe::convertToText(error));
}

void AudioOutputCallback::onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error)
{
	CHIAKI_LOGE(audio_output->log, "Oboe reported error after close: %s", oboe::convertToText(error));
	schedule_reopen(audio_output);
}
