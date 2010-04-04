#include "dacapoallocation.h"

#include "dacapotag.h"
#include "dacapolog.h"
#include "dacapolock.h"


/* Callback for JVMTI_EVENT_VM_OBJECT_ALLOC */
void JNICALL callbackVMObjectAlloc(jvmtiEnv *jvmti, JNIEnv *env, jthread thread,
                jobject object, jclass object_klass, jlong size)
{
	if (jvmRunning && !jvmStopped) {
		jlong class_tag  = 0;
		jlong thread_tag = 0;

		enterCriticalSection(&lockTag);
		jlong tag = setTag(object, size);
		jboolean class_has_new_tag = getTag(object_klass, &class_tag);
		jboolean thread_has_new_tag = getTag(thread, &thread_tag);
		exitCriticalSection(&lockTag);

		/* trace allocation */
		enterCriticalSection(&lockLog);
		jniNativeInterface* jni_table;
		if (class_has_new_tag || thread_has_new_tag) {
			if (JVMTI_FUNC_PTR(baseEnv,GetJNIFunctionTable)(baseEnv,&jni_table) != JNI_OK) {
				fprintf(stderr, "failed to get JNI function table\n");
				exit(1);
			}
		}

		log_field_string(LOG_PREFIX_ALLOCATION);

		log_field_jlong(tag);
	    if (class_has_new_tag) {
	    	LOG_CLASS(jni_table,env,baseEnv,object_klass);
	    } else 
	    	log_field_string(NULL);
		log_field_jlong(thread_tag);
	    if (thread_has_new_tag) {
	    	LOG_OBJECT_CLASS(jni_table,env,baseEnv,thread);
	    } else
	    	log_field_string(NULL);
		log_field_jlong(size);
		
		log_eol();
		
		exitCriticalSection(&lockLog);
	}
}

/* Callback for JVMTI_EVENT_OBJECT_FREE */
void JNICALL callbackObjectFree(jvmtiEnv *jvmti, jlong tag)
{
	if (jvmRunning && !jvmStopped) {
		/* trace free */
		log_field_string(LOG_PREFIX_FREE);
		log_field_jlong(tag);
		log_eol();
	}
}

