/**
 * llama_android.cpp
 * JNI Bridge implementation for llama.cpp on Android.
 * Compatible with recent llama.cpp (2026 API).
 *
 * Package: com.example.jarvisai.data.native.LlamaEngine
 * Target: libllama_android.so
 */

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <cstring>
#include <android/log.h>

#define TAG "LlamaAndroidNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#if defined(HAVE_LLAMA_CPP)
#include "llama.h"
#include "common.h"
#endif

// Structure encapsulating native llama.cpp state
struct LlamaContextContainer {
#if defined(HAVE_LLAMA_CPP)
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
#else
    void* model = nullptr;
    void* ctx = nullptr;
    void* vocab = nullptr;
#endif
    std::atomic<bool> is_interrupted{false};
    int n_threads = 4;
    int n_ctx = 2048;
    bool use_mmap = true;
};

extern "C" {

/**
 * JNI: loadModelNative
 * Loads a GGUF model and initializes context with KV cache.
 * Signature: (Ljava/lang/String;IIZ)J
 */
JNIEXPORT jlong JNICALL
Java_com_example_jarvisai_data_native_LlamaEngine_loadModelNative(
    JNIEnv* env,
    jobject /* thiz */,
    jstring model_path_jstr,
    jint context_size,
    jint threads,
    jboolean use_mmap
) {
    if (model_path_jstr == nullptr) {
        LOGE("Model path is null");
        return 0;
    }

    const char* model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    LOGI("Loading GGUF model from: %s [ctx: %d, threads: %d, mmap: %d]",
         model_path, context_size, threads, (int)use_mmap);

    auto* container = new LlamaContextContainer();
    container->n_threads = threads;
    container->n_ctx = context_size;
    container->use_mmap = (bool)use_mmap;
    container->is_interrupted.store(false);

#if defined(HAVE_LLAMA_CPP)
    // 1. Initialize llama backend once
    llama_backend_init();

    // 2. Configure model parameters
    // Nota: en la versión actual de llama.cpp ya no existe model_params.use_mmap
    llama_model_params model_params = llama_model_default_params();
    model_params.check_tensors = false;

    // Prefer the non-deprecated function
    llama_model* model = llama_model_load_from_file(model_path, model_params);
    if (!model) {
        // Fallback for older builds
        model = llama_load_model_from_file(model_path, model_params);
    }

    if (!model) {
        LOGE("Failed to load llama model from: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        delete container;
        return 0;
    }

    // 3. Configure context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t)context_size;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    // Prefer the non-deprecated function
    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        // Fallback for older API
        ctx = llama_new_context_with_model(model, ctx_params);
    }

    if (!ctx) {
        LOGE("Failed to create llama context for model: %s", model_path);
        llama_model_free(model);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        delete container;
        return 0;
    }

    container->model = model;
    container->ctx = ctx;
    container->vocab = llama_model_get_vocab(model);
#else
    LOGW("Compiled without HAVE_LLAMA_CPP submodule. Operating in mock/stub mode.");
#endif

    env->ReleaseStringUTFChars(model_path_jstr, model_path);
    LOGI("Llama context container successfully allocated at %p", (void*)container);
    return reinterpret_cast<jlong>(container);
}

/**
 * Helper: clear a llama_batch (manual, works across API versions)
 */
#if defined(HAVE_LLAMA_CPP)
static void batch_clear(llama_batch & batch) {
    batch.n_tokens = 0;
}

static void batch_add(
    llama_batch & batch,
    llama_token  id,
    llama_pos    pos,
    const std::vector<llama_seq_id> & seq_ids,
    bool         logits
) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits  [batch.n_tokens] = logits ? 1 : 0;
    batch.n_tokens++;
}
#endif

/**
 * JNI: generateNative
 * Performs autoregressive token generation with streaming callback.
 */
JNIEXPORT void JNICALL
Java