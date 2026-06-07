#include <jni.h>
#include <vector>
#include <stdexcept>
#include "original_stc_adapter.h"

static void throw_java(JNIEnv* env, const char* msg) {
    jclass ex = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(ex, msg);
}

static std::vector<unsigned char> read_jbyte_array(JNIEnv* env, jbyteArray arr) {
    jsize n = env->GetArrayLength(arr);
    std::vector<unsigned char> out((size_t)n);
    jbyte* data = env->GetByteArrayElements(arr, nullptr);
    for (jsize i = 0; i < n; i++) out[(size_t)i] = (unsigned char)data[i];
    env->ReleaseByteArrayElements(arr, data, JNI_ABORT);
    return out;
}

static std::vector<double> read_jdouble_array(JNIEnv* env, jdoubleArray arr) {
    jsize n = env->GetArrayLength(arr);
    std::vector<double> out((size_t)n);
    jdouble* data = env->GetDoubleArrayElements(arr, nullptr);
    for (jsize i = 0; i < n; i++) out[(size_t)i] = (double)data[i];
    env->ReleaseDoubleArrayElements(arr, data, JNI_ABORT);
    return out;
}

static jbyteArray make_jbyte_array(JNIEnv* env, const std::vector<unsigned char>& v) {
    jbyteArray arr = env->NewByteArray((jsize)v.size());
    if (!arr) return nullptr;
    std::vector<jbyte> tmp(v.begin(), v.end());
    env->SetByteArrayRegion(arr, 0, (jsize)tmp.size(), tmp.data());
    return arr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_thesis_ahp_stc_OriginalStcNative_embedBinary(
    JNIEnv* env,
    jclass,
    jbyteArray coverBits,
    jdoubleArray costs,
    jbyteArray messageBits,
    jint constraintHeight
) {
    try {
        auto x = read_jbyte_array(env, coverBits);
        auto rho = read_jdouble_array(env, costs);
        auto m = read_jbyte_array(env, messageBits);
        auto y = original_stc_embed_adapter(x, rho, m, (int)constraintHeight);
        return make_jbyte_array(env, y);
    } catch (const std::exception& e) {
        throw_java(env, e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_thesis_ahp_stc_OriginalStcNative_extractBinary(
    JNIEnv* env,
    jclass,
    jbyteArray stegoBits,
    jint messageBitLength,
    jint constraintHeight
) {
    try {
        auto y = read_jbyte_array(env, stegoBits);
        auto m = original_stc_extract_adapter(y, (int)messageBitLength, (int)constraintHeight);
        return make_jbyte_array(env, m);
    } catch (const std::exception& e) {
        throw_java(env, e.what());
        return nullptr;
    }
}
