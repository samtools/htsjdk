/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Native method support for htsjdk.samtools.util.zip.IntelDeflater.
 * This is copied from OpenJDK native support for java.util.zip.Deflater, and modified to support igzip.
 */

#include <stdio.h>
#include <stdlib.h>
#include <immintrin.h> 
#include <emmintrin.h>
#include <stdbool.h>
#include <assert.h>
#include "jlong.h"
#include "jni.h"
//#include "jni_util.h"

#include "zlib.h"
#include "htsjdk_samtools_util_zip_IntelDeflater.h"
#include "igzip_lib.h"
#define DEF_MEM_LEVEL 8
#define FAST_COMPRESSION 1
#define IGZIP_TRUE 1

static jfieldID levelID;
static jfieldID strategyID;
static jfieldID setParamsID;
static jfieldID finishID;
static jfieldID finishedID;
static jfieldID bufID, offID, lenID;

typedef struct {
    z_stream zStream;
    LZ_Stream2 lz2Stream;
    int useIGZIP;
} Stream;


bool is_cpuid_ecx_bit_set(int eax, int bitidx) 
{ 
  int ecx = 0, edx = 0, ebx = 0; 
  __asm__ ("cpuid" 
	   :"=b" (ebx), 
	    "=c" (ecx), 
	    "=d" (edx) 
	   :"a" (eax) 
	   ); 
  return (((ecx >> bitidx)&1) == 1); 
}

bool is_sse42_supported() 
{ 
#ifdef __INTEL_COMPILER 
  return  (_may_i_use_cpu_feature(_FEATURE_SSE4_2) > 0); 
#else 
  //  return  __builtin_cpu_supports("sse4.2"); 
  return is_cpuid_ecx_bit_set(1, 20); 
#endif 
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Throw a Java exception by name. Similar to SignalError.
 */
JNIEXPORT void JNICALL
JNU_ThrowByName(JNIEnv *env, const char *name, const char *msg)
{
    jclass cls = (*env)->FindClass(env, name);

    if (cls != 0) /* Otherwise an exception has already been thrown */
        (*env)->ThrowNew(env, cls, msg);
}

/* JNU_Throw common exceptions */

JNIEXPORT void JNICALL
JNU_ThrowNullPointerException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NullPointerException", msg);
}


JNIEXPORT void JNICALL
JNU_ThrowOutOfMemoryError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/OutOfMemoryError", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowIllegalArgumentException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/IllegalArgumentException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowInternalError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/InternalError", msg);
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
JNIEXPORT void JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_initIDs(JNIEnv *env, jclass cls)
{
    levelID = (*env)->GetFieldID(env, cls, "level", "I");
    strategyID = (*env)->GetFieldID(env, cls, "strategy", "I");
    setParamsID = (*env)->GetFieldID(env, cls, "setParams", "Z");
    finishID = (*env)->GetFieldID(env, cls, "finish", "Z");
    finishedID = (*env)->GetFieldID(env, cls, "finished", "Z");
    bufID = (*env)->GetFieldID(env, cls, "buf", "[B");
    offID = (*env)->GetFieldID(env, cls, "off", "I");
    lenID = (*env)->GetFieldID(env, cls, "len", "I");
 
}

JNIEXPORT jlong JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_init(JNIEnv *env, jclass cls, jint level,
                                 jint strategy, jboolean nowrap)
{
    Stream *strm = calloc(1, sizeof(Stream));
    if (level == FAST_COMPRESSION && is_sse42_supported()) { //Use igzip
	printf("Using igzip\n");
	strm->useIGZIP = IGZIP_TRUE;
	if (strm == 0) {
	    JNU_ThrowOutOfMemoryError(env, 0);
	    return jlong_zero;
	} else {
	    init_stream(&strm->lz2Stream); //CHECK RETURN VALUE
	    return ptr_to_jlong(strm);
	}
      
    } else {

	if (strm == 0) {
	    JNU_ThrowOutOfMemoryError(env, 0);
	    return jlong_zero;
	} else {
	    char *msg;
	    switch (deflateInit2(&strm->zStream, level, Z_DEFLATED,
				 nowrap ? -MAX_WBITS : MAX_WBITS,
				 DEF_MEM_LEVEL, strategy)) {
	    case Z_OK:
		return ptr_to_jlong(&strm->zStream);
	    case Z_MEM_ERROR:
		free(strm);
		JNU_ThrowOutOfMemoryError(env, 0);
		return jlong_zero;
	    case Z_STREAM_ERROR:
		free(strm);
		JNU_ThrowIllegalArgumentException(env, 0);
		return jlong_zero;
	    default:
		msg = strm->zStream.msg;
		free(strm);
		JNU_ThrowInternalError(env, msg);
		return jlong_zero;
	    }
	}
    }
}

JNIEXPORT void JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_setDictionary(JNIEnv *env, jclass cls, jlong addr,
                                          jarray b, jint off, jint len)
{
    Bytef *buf = (*env)->GetPrimitiveArrayCritical(env, b, 0);
    int res;
    if (buf == 0) {/* out of memory */
        return;
    }
    res = deflateSetDictionary(&((Stream *)jlong_to_ptr(addr))->zStream, buf + off, len);
    (*env)->ReleasePrimitiveArrayCritical(env, b, buf, 0);
    switch (res) {
    case Z_OK:
        break;
    case Z_STREAM_ERROR:
        JNU_ThrowIllegalArgumentException(env, 0);
        break;
    default:
        JNU_ThrowInternalError(env, ((Stream *)jlong_to_ptr(addr))->zStream.msg);
        break;
    }
}

JNIEXPORT jint JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_deflateBytes(JNIEnv *env, jobject this, jlong addr,
                                         jarray b, jint off, jint len, jint flush)
{
    jarray this_buf = (*env)->GetObjectField(env, this, bufID);
    jint this_off = (*env)->GetIntField(env, this, offID);
    jint this_len = (*env)->GetIntField(env, this, lenID);
    jbyte *in_buf;
    jbyte *out_buf;
    Stream *strm = jlong_to_ptr(addr);

    //igzip only supports one compression level so setParamsID should not be set when using igzip 
    //igzip does not support flush
    if (((Stream *)jlong_to_ptr(addr))->useIGZIP && (((*env)->GetBooleanField(env, this, setParamsID) && strm->lz2Stream.total_in != 0)  || flush == 1)) {
	JNU_ThrowInternalError(env, "igzip doesn't support this");
    } else if (((Stream *)jlong_to_ptr(addr))->useIGZIP) {
	in_buf = (*env)->GetPrimitiveArrayCritical(env, this_buf, 0);
	if (in_buf == NULL) {
	    // Throw OOME only when length is not zero
	    if (this_len != 0) {
		JNU_ThrowOutOfMemoryError(env, 0);
	    }
	    return 0;
	}
	out_buf = (*env)->GetPrimitiveArrayCritical(env, b, 0);
	if (out_buf == NULL) {
	    (*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);
	    if (len != 0) {
		JNU_ThrowOutOfMemoryError(env, 0);
	    }
	    return 0;
	}
	strm->lz2Stream.next_in = (Bytef *) (in_buf + this_off);
	strm->lz2Stream.next_out = (Bytef *) (out_buf + off);
	strm->lz2Stream.avail_in = this_len;
	strm->lz2Stream.avail_out = len;
	assert(strm->lz2Stream.avail_in != 0);
	assert(strm->lz2Stream.avail_out != 0);
	jboolean finish = (*env)->GetBooleanField(env, this, finishID);
	if (finish) {
	    strm->lz2Stream.end_of_stream = 1;
	} else {
	    strm->lz2Stream.end_of_stream = 0;
	}
	fast_lz(&strm->lz2Stream);

	(*env)->ReleasePrimitiveArrayCritical(env, b, out_buf, 0);
	(*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);
	if (finish) {
	    (*env)->SetBooleanField(env, this, finishedID, JNI_TRUE);
	}
	this_off += this_len - strm->lz2Stream.avail_in;
	(*env)->SetIntField(env, this, offID, this_off);
	(*env)->SetIntField(env, this, lenID, strm->lz2Stream.avail_in);
	return len - strm->lz2Stream.avail_out;
    } else {

	int res;
	if ((*env)->GetBooleanField(env, this, setParamsID)) {
	    int level = (*env)->GetIntField(env, this, levelID);
	    int strategy = (*env)->GetIntField(env, this, strategyID);

	    in_buf = (*env)->GetPrimitiveArrayCritical(env, this_buf, 0);
	    if (in_buf == NULL) {
		// Throw OOME only when length is not zero
		if (this_len != 0)
		    JNU_ThrowOutOfMemoryError(env, 0);
		return 0;
	    }
	    out_buf = (*env)->GetPrimitiveArrayCritical(env, b, 0);
	    if (out_buf == NULL) {
		(*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);
		if (len != 0)
		    JNU_ThrowOutOfMemoryError(env, 0);
		return 0;
	    }

	    strm->zStream.next_in = (Bytef *) (in_buf + this_off);
	    strm->zStream.next_out = (Bytef *) (out_buf + off);
	    strm->zStream.avail_in = this_len;
	    strm->zStream.avail_out = len;
	    res = deflateParams(&strm->zStream, level, strategy);
	    (*env)->ReleasePrimitiveArrayCritical(env, b, out_buf, 0);
	    (*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);

	    switch (res) {
	    case Z_OK:
		(*env)->SetBooleanField(env, this, setParamsID, JNI_FALSE);
		this_off += this_len - strm->zStream.avail_in;
		(*env)->SetIntField(env, this, offID, this_off);
		(*env)->SetIntField(env, this, lenID, strm->zStream.avail_in);
		return len - strm->zStream.avail_out;
	    case Z_BUF_ERROR:
		(*env)->SetBooleanField(env, this, setParamsID, JNI_FALSE);
		return 0;
	    default:
		JNU_ThrowInternalError(env, strm->zStream.msg);
		return 0;
	    }
	} else {
	    jboolean finish = (*env)->GetBooleanField(env, this, finishID);
	    in_buf = (*env)->GetPrimitiveArrayCritical(env, this_buf, 0);
	    if (in_buf == NULL) {
		if (this_len != 0)
		    JNU_ThrowOutOfMemoryError(env, 0);
		return 0;
	    }
	    out_buf = (*env)->GetPrimitiveArrayCritical(env, b, 0);
	    if (out_buf == NULL) {
		(*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);
		if (len != 0)
		    JNU_ThrowOutOfMemoryError(env, 0);

		return 0;
	    }

	    strm->zStream.next_in = (Bytef *) (in_buf + this_off);
	    strm->zStream.next_out = (Bytef *) (out_buf + off);
	    strm->zStream.avail_in = this_len;
	    strm->zStream.avail_out = len;
	    res = deflate(&strm->zStream, finish ? Z_FINISH : flush);
	    (*env)->ReleasePrimitiveArrayCritical(env, b, out_buf, 0);
	    (*env)->ReleasePrimitiveArrayCritical(env, this_buf, in_buf, 0);

	    switch (res) {
	    case Z_STREAM_END:
		(*env)->SetBooleanField(env, this, finishedID, JNI_TRUE);
		/* fall through */
	    case Z_OK:
		this_off += this_len - strm->zStream.avail_in;
		(*env)->SetIntField(env, this, offID, this_off);
		(*env)->SetIntField(env, this, lenID, strm->zStream.avail_in);
		return len - strm->zStream.avail_out;
	    case Z_BUF_ERROR:
		return 0;
            default:
		JNU_ThrowInternalError(env, strm->zStream.msg);
		return 0;
	    }
	}
    }
}

JNIEXPORT jint JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_getAdler(JNIEnv *env, jclass cls, jlong addr)
{
    if (((Stream *)jlong_to_ptr(addr))->useIGZIP)
	JNU_ThrowInternalError(env, "igzip doesn't support getAdler function");
    else
	return ((Stream *)jlong_to_ptr(addr))->zStream.adler;
}

JNIEXPORT jlong JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_getBytesRead(JNIEnv *env, jclass cls, jlong addr)
{
    return ( ((Stream *)jlong_to_ptr(addr))->useIGZIP ? ((Stream *) jlong_to_ptr(addr))->lz2Stream.total_in : ((Stream *)jlong_to_ptr(addr))->zStream.total_in);
}

JNIEXPORT jlong JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_getBytesWritten(JNIEnv *env, jclass cls, jlong addr)
{
    return ( ((Stream *)jlong_to_ptr(addr))->useIGZIP ? ((Stream *) jlong_to_ptr(addr))->lz2Stream.total_out : ((Stream *)jlong_to_ptr(addr))->zStream.total_out);
}

JNIEXPORT void JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_reset(JNIEnv *env, jclass cls, jlong addr)
{
    if  (((Stream *)jlong_to_ptr(addr))->useIGZIP)
	init_stream(&(((Stream *)jlong_to_ptr(addr))->lz2Stream));
    else {
    if (deflateReset(&(((Stream *)jlong_to_ptr(addr))->zStream)) != Z_OK) {
	    JNU_ThrowInternalError(env, 0);
	}
    }
}

JNIEXPORT void JNICALL
Java_htsjdk_samtools_util_zip_IntelDeflater_end(JNIEnv *env, jclass cls, jlong addr)
{
    if (!((Stream *)jlong_to_ptr(addr))->useIGZIP) {
	if (deflateEnd(&(((Stream *)jlong_to_ptr(addr))->zStream)) == Z_STREAM_ERROR) {
	    JNU_ThrowInternalError(env, 0);
	} 
    }
    free((Stream *)jlong_to_ptr(addr));
}
