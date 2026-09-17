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
Java_com_example_jarvisai_data_native_LlamaEngine_generateNative(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jstring prompt_jstr,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jobject callback
) {
    auto* container = reinterpret_cast<LlamaContextContainer*>(handle);
    if (!container) {
        LOGE("generateNative: Invalid native context handle");
        return;
    }

    if (callback == nullptr) {
        LOGE("generateNative: Callback is null");
        return;
    }

    // Find callback method: boolean onToken(String token)
    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
    if (!on_token_method) {
        LOGE("generateNative: Failed to find onToken(Ljava/lang/String;)Z method");
        return;
    }

    container->is_interrupted.store(false);

    const char* prompt = env->GetStringUTFChars(prompt_jstr, nullptr);
    LOGI("generateNative: Starting generation (max_tokens: %d, temp: %.2f, top_p: %.2f)",
         max_tokens, temperature, top_p);

#if defined(HAVE_LLAMA_CPP)
    if (container->model && container->ctx && container->vocab) {
        const llama_vocab* vocab = container->vocab;
        llama_context* ctx = container->ctx;

        // 1. Tokenize prompt
        const int n_prompt_max = (int)strlen(prompt) + 16;
        std::vector<llama_token> prompt_tokens(n_prompt_max);
        int n_tokens = llama_tokenize(vocab, prompt, (int)strlen(prompt),
                                      prompt_tokens.data(), (int)prompt_tokens.size(),
                                      true, true);
        if (n_tokens < 0) {
            prompt_tokens.resize(-n_tokens);
            n_tokens = llama_tokenize(vocab, prompt, (int)strlen(prompt),
                                      prompt_tokens.data(), (int)prompt_tokens.size(),
                                      true, true);
        }
        if (n_tokens < 0) {
            LOGE("Failed to tokenize prompt");
            env->ReleaseStringUTFChars(prompt_jstr, prompt);
            return;
        }
        prompt_tokens.resize(n_tokens);

        // 2. Batch evaluation of prompt
        llama_batch batch = llama_batch_init(std::max(512, n_tokens), 0, 1);

        for (int i = 0; i < n_tokens; ++i) {
            batch_add(batch, prompt_tokens[i], i, {0}, i == n_tokens - 1);
        }

        if (llama_decode(ctx, batch) != 0) {
            LOGE("Failed to decode prompt tokens");
            llama_batch_free(batch);
            env->ReleaseStringUTFChars(prompt_jstr, prompt);
            return;
        }

        // 3. Initialize sampler chain
        auto sparams = llama_sampler_chain_default_params();
        llama_sampler* smpl = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        // 4. Autoregressive loop
        int n_generated = 0;
        llama_token new_token_id;
        int n_cur = batch.n_tokens;

        while (n_generated < max_tokens && !container->is_interrupted.load()) {
            // Sample next token
            new_token_id = llama_sampler_sample(smpl, ctx, -1);
            llama_sampler_accept(smpl, new_token_id);

            // Check EOS
            if (llama_vocab_is_eog(vocab, new_token_id)) {
                LOGI("EOS encountered after %d tokens", n_generated);
                break;
            }

            // Convert token to piece
            char piece_buf[256];
            int n_piece = llama_token_to_piece(vocab, new_token_id, piece_buf, sizeof(piece_buf), 0, true);
            if (n_piece < 0) {
                n_piece = -n_piece;
            }
            if (n_piece > 0) {
                std::string piece_str(piece_buf, n_piece);
                jstring token_jstr = env->NewStringUTF(piece_str.c_str());
                jboolean keep_going = env->CallBooleanMethod(callback, on_token_method, token_jstr);
                env->DeleteLocalRef(token_jstr);

                if (!keep_going) {
                    LOGI("Generation cancelled by client callback");
                    break;
                }
            }

            // Prepare next single-token batch
            batch_clear(batch);
            batch_add(batch, new_token_id, n_cur++, {0}, true);

            if (llama_decode(ctx, batch) != 0) {
                LOGE("Failed to decode sampled token");
                break;
            }
            n_generated++;
        }

        llama_sampler_free(smpl);
        llama_batch_free(batch);
    }
#else
    // Fallback simulation when submodule is not yet compiled
    std::vector<std::string> mock_words = {
        " Jarvis", " funcionando", " en", " modo", " offline", " (JNI", " compilado", " con", " éxito).",
        "\n\nPara", " inferencia", " local", " con", " pesos", " reales,", " recuerda", " inicializar",
        " el", " submódulo", " de", " llama.cpp."
    };

    for (const auto& word : mock_words) {
        if (container->is_interrupted.load()) break;
        jstring token_jstr = env->NewStringUTF(word.c_str());
        jboolean keep_going = env->CallBooleanMethod(callback, on_token_method, token_jstr);
        env->DeleteLocalRef(token_jstr);
        if (!keep_going) break;
    }
#endif

    env->ReleaseStringUTFChars(prompt_jstr, prompt);
    LOGI("generateNative: Execution finished");
}

/**
 * JNI: stopNative
 */
JNIEXPORT void JNICALL
Java_com_example_jarvisai_data_native_LlamaEngine_stopNative(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto* container = reinterpret_cast<LlamaContextContainer*>(handle);
    if (container) {
        LOGI("stopNative: Interrupting generation for container %p", (void*)container);
        container->is_interrupted.store(true);
    }
}

/**
 * JNI: freeModelNative
 */
JNIEXPORT void JNICALL
Java_com_example_jarvisai_data_native_LlamaEngine_freeModelNative(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto* container = reinterpret_cast<LlamaContextContainer*>(handle);
    if (container) {
        LOGI("freeModelNative: Freeing resources at %p", (void*)container);
#if defined(HAVE_LLAMA_CPP)
        if (container->ctx) {
            llama_free(container->ctx);
            container->ctx = nullptr;
        }
        if (container->model) {
            llama_model_free(container->model);
            container->model = nullptr;
        }
#endif
        delete container;
    }
}

} // extern "C"