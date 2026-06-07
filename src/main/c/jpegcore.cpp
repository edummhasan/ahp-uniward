#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jpeglib.h>
#include <setjmp.h>

struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
};

typedef struct my_error_mgr* my_error_ptr;

static void my_error_exit(j_common_ptr cinfo) {
    my_error_ptr myerr = (my_error_ptr)cinfo->err;
    (*cinfo->err->output_message)(cinfo);
    longjmp(myerr->setjmp_buffer, 1);
}

static void throw_java(JNIEnv* env, const char* msg) {
    jclass ex = env->FindClass("java/lang/RuntimeException");
    if (ex != NULL) {
        env->ThrowNew(ex, msg);
    }
}

extern "C" JNIEXPORT jobject JNICALL Java_thesis_ahp_jni_JpegCore_readCoefficients
(JNIEnv* env, jclass cls, jstring pathStr) {

    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    FILE* infile = fopen(path, "rb");

    if (!infile) {
        env->ReleaseStringUTFChars(pathStr, path);
        throw_java(env, "Cannot open JPEG input");
        return NULL;
    }

    struct jpeg_decompress_struct cinfo;
    struct my_error_mgr jerr;

    cinfo.err = jpeg_std_error(&jerr.pub);
    jerr.pub.error_exit = my_error_exit;

    if (setjmp(jerr.setjmp_buffer)) {
        jpeg_destroy_decompress(&cinfo);
        fclose(infile);
        env->ReleaseStringUTFChars(pathStr, path);
        throw_java(env, "JPEG read error");
        return NULL;
    }

    jpeg_create_decompress(&cinfo);
    jpeg_stdio_src(&cinfo, infile);
    jpeg_read_header(&cinfo, TRUE);

    jvirt_barray_ptr* coef_arrays = jpeg_read_coefficients(&cinfo);

    jclass compCls = env->FindClass("thesis/ahp/jni/JpegComponent");
    jmethodID compCtor = env->GetMethodID(compCls, "<init>", "(III[I[I)V");
    jobjectArray comps = env->NewObjectArray(cinfo.num_components, compCls, NULL);

    for (int comp = 0; comp < cinfo.num_components; comp++) {
        jpeg_component_info* ci = &cinfo.comp_info[comp];

        int wb = ci->width_in_blocks;
        int hb = ci->height_in_blocks;
        int total = wb * hb * 64;

        jintArray coeffArr = env->NewIntArray(total);
        jint* coeffs = (jint*)malloc(sizeof(jint) * total);

        int pos = 0;
        for (int by = 0; by < hb; by++) {
            JBLOCKARRAY row = (*cinfo.mem->access_virt_barray)(
                (j_common_ptr)&cinfo,
                                                               coef_arrays[comp],
                                                               by,
                                                               1,
                                                               FALSE
            );

            for (int bx = 0; bx < wb; bx++) {
                JCOEF* block = row[0][bx];
                for (int k = 0; k < 64; k++) {
                    coeffs[pos++] = (jint)block[k];
                }
            }
        }

        env->SetIntArrayRegion(coeffArr, 0, total, coeffs);
        free(coeffs);

        jintArray qArr = env->NewIntArray(64);
        jint q[64];

        for (int k = 0; k < 64; k++) {
            q[k] = ci->quant_table ? (jint)ci->quant_table->quantval[k] : 1;
        }

        env->SetIntArrayRegion(qArr, 0, 64, q);

        jobject compObj = env->NewObject(
            compCls,
            compCtor,
            comp,
            wb,
            hb,
            coeffArr,
            qArr
        );

        env->SetObjectArrayElement(comps, comp, compObj);
    }

    jclass imgCls = env->FindClass("thesis/ahp/jni/JpegImage");
    jmethodID imgCtor = env->GetMethodID(
        imgCls,
        "<init>",
        "(II[Lthesis/ahp/jni/JpegComponent;)V"
    );

    jobject img = env->NewObject(
        imgCls,
        imgCtor,
        (jint)cinfo.image_width,
                                 (jint)cinfo.image_height,
                                 comps
    );

    jpeg_finish_decompress(&cinfo);
    jpeg_destroy_decompress(&cinfo);
    fclose(infile);

    env->ReleaseStringUTFChars(pathStr, path);

    return img;
}

extern "C" JNIEXPORT void JNICALL Java_thesis_ahp_jni_JpegCore_writeCoefficients
(JNIEnv* env, jclass cls, jstring inStr, jstring outStr, jobject imageObj) {

    const char* inpath = env->GetStringUTFChars(inStr, nullptr);
    const char* outpath = env->GetStringUTFChars(outStr, nullptr);

    FILE* infile = NULL;
    FILE* outfile = NULL;

    struct jpeg_decompress_struct srcinfo;
    struct jpeg_compress_struct dstinfo;
    struct my_error_mgr jsrcerr;
    struct my_error_mgr jdsterr;

    bool srcCreated = false;
    bool dstCreated = false;

    infile = fopen(inpath, "rb");
    if (!infile) {
        throw_java(env, "Cannot open template JPEG");
        env->ReleaseStringUTFChars(inStr, inpath);
        env->ReleaseStringUTFChars(outStr, outpath);
        return;
    }

    outfile = fopen(outpath, "wb");
    if (!outfile) {
        fclose(infile);
        throw_java(env, "Cannot open output JPEG");
        env->ReleaseStringUTFChars(inStr, inpath);
        env->ReleaseStringUTFChars(outStr, outpath);
        return;
    }

    srcinfo.err = jpeg_std_error(&jsrcerr.pub);
    jsrcerr.pub.error_exit = my_error_exit;

    dstinfo.err = jpeg_std_error(&jdsterr.pub);
    jdsterr.pub.error_exit = my_error_exit;

    if (setjmp(jsrcerr.setjmp_buffer) || setjmp(jdsterr.setjmp_buffer)) {
        if (dstCreated) jpeg_destroy_compress(&dstinfo);
        if (srcCreated) jpeg_destroy_decompress(&srcinfo);
        if (infile) fclose(infile);
        if (outfile) fclose(outfile);

        env->ReleaseStringUTFChars(inStr, inpath);
        env->ReleaseStringUTFChars(outStr, outpath);

        throw_java(env, "JPEG write error");
        return;
    }

    jpeg_create_decompress(&srcinfo);
    srcCreated = true;

    jpeg_stdio_src(&srcinfo, infile);
    jpeg_read_header(&srcinfo, TRUE);

    jvirt_barray_ptr* coef_arrays = jpeg_read_coefficients(&srcinfo);

    jclass imgCls = env->GetObjectClass(imageObj);
    jfieldID compsF = env->GetFieldID(
        imgCls,
        "components",
        "[Lthesis/ahp/jni/JpegComponent;"
    );

    jobjectArray comps = (jobjectArray)env->GetObjectField(imageObj, compsF);

    jclass compCls = env->FindClass("thesis/ahp/jni/JpegComponent");
    jfieldID coeffF = env->GetFieldID(compCls, "coefficients", "[I");

    for (int comp = 0; comp < srcinfo.num_components; comp++) {
        jobject compObj = env->GetObjectArrayElement(comps, comp);
        jintArray coeffArr = (jintArray)env->GetObjectField(compObj, coeffF);
        jint* coeffs = env->GetIntArrayElements(coeffArr, NULL);

        jpeg_component_info* ci = &srcinfo.comp_info[comp];

        int wb = ci->width_in_blocks;
        int hb = ci->height_in_blocks;
        int pos = 0;

        for (int by = 0; by < hb; by++) {
            JBLOCKARRAY row = (*srcinfo.mem->access_virt_barray)(
                (j_common_ptr)&srcinfo,
                                                                 coef_arrays[comp],
                                                                 by,
                                                                 1,
                                                                 TRUE
            );

            for (int bx = 0; bx < wb; bx++) {
                JCOEF* block = row[0][bx];

                for (int k = 0; k < 64; k++) {
                    block[k] = (JCOEF)coeffs[pos++];
                }
            }
        }

        env->ReleaseIntArrayElements(coeffArr, coeffs, JNI_ABORT);
    }

    jpeg_create_compress(&dstinfo);
    dstCreated = true;

    jpeg_stdio_dest(&dstinfo, outfile);
    jpeg_copy_critical_parameters(&srcinfo, &dstinfo);
    jpeg_write_coefficients(&dstinfo, coef_arrays);
    jpeg_finish_compress(&dstinfo);
    jpeg_finish_decompress(&srcinfo);

    jpeg_destroy_compress(&dstinfo);
    jpeg_destroy_decompress(&srcinfo);

    fclose(infile);
    fclose(outfile);

    env->ReleaseStringUTFChars(inStr, inpath);
    env->ReleaseStringUTFChars(outStr, outpath);
}
