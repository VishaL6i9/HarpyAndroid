#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include "arp_operations.h"
#include "network_scan.h"

#define LOG_TAG "HarpyNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global state
static bool g_initialized = false;

extern "C" {

/**
 * Initialize native network operations
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_initializeNativeOps(
    JNIEnv *env, jclass clazz) {
    LOGD("Initializing native network operations");
    
    if (g_initialized) {
        LOGD("Already initialized");
        return JNI_TRUE;
    }
    
    // Initialize ARP operations
    if (!arp_init()) {
        LOGE("Failed to initialize ARP operations");
        return JNI_FALSE;
    }
    
    // Initialize network scan
    if (!network_scan_init()) {
        LOGE("Failed to initialize network scan");
        arp_cleanup();
        return JNI_FALSE;
    }
    
    g_initialized = true;
    LOGD("Native operations initialized successfully");
    return JNI_TRUE;
}

/**
 * Perform ARP spoofing
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_performARPSpoof(
    JNIEnv *env, jclass clazz,
    jstring targetIP, jstring targetMAC,
    jstring gatewayIP, jstring ourMAC) {
    
    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return JNI_FALSE;
    }
    
    const char *target_ip = env->GetStringUTFChars(targetIP, nullptr);
    const char *target_mac = env->GetStringUTFChars(targetMAC, nullptr);
    const char *gateway_ip = env->GetStringUTFChars(gatewayIP, nullptr);
    const char *our_mac = env->GetStringUTFChars(ourMAC, nullptr);
    
    LOGD("Performing ARP spoof: target=%s, gateway=%s", target_ip, gateway_ip);
    
    bool result = arp_spoof(target_ip, target_mac, gateway_ip, our_mac);
    
    env->ReleaseStringUTFChars(targetIP, target_ip);
    env->ReleaseStringUTFChars(targetMAC, target_mac);
    env->ReleaseStringUTFChars(gatewayIP, gateway_ip);
    env->ReleaseStringUTFChars(ourMAC, our_mac);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

/**
 * Scan network for devices
 */
JNIEXPORT jobjectArray JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_scanNetworkNative(
    JNIEnv *env, jclass clazz,
    jstring interfaceName, jstring subnet, jint timeoutSeconds) {
    
    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }
    
    const char *iface = env->GetStringUTFChars(interfaceName, nullptr);
    const char *subnet_str = env->GetStringUTFChars(subnet, nullptr);
    
    LOGD("Scanning network: interface=%s, subnet=%s, timeout=%d", iface, subnet_str, timeoutSeconds);
    
    // Perform network scan
    std::vector<std::string> devices = network_scan(iface, subnet_str, timeoutSeconds);
    
    env->ReleaseStringUTFChars(interfaceName, iface);
    env->ReleaseStringUTFChars(subnet, subnet_str);
    
    // Convert to Java array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(devices.size(), stringClass, nullptr);
    
    for (size_t i = 0; i < devices.size(); i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(devices[i].c_str()));
    }
    
    return result;
}

/**
 * Get MAC address for IP
 */
JNIEXPORT jstring JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_getMACForIP(
    JNIEnv *env, jclass clazz,
    jstring ip, jstring interfaceName) {
    
    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return nullptr;
    }
    
    const char *ip_str = env->GetStringUTFChars(ip, nullptr);
    const char *iface = env->GetStringUTFChars(interfaceName, nullptr);
    
    LOGD("Getting MAC for IP: %s on %s", ip_str, iface);
    
    std::string mac = arp_get_mac(ip_str, iface);
    
    env->ReleaseStringUTFChars(ip, ip_str);
    env->ReleaseStringUTFChars(interfaceName, iface);
    
    if (mac.empty()) {
        return nullptr;
    }
    
    return env->NewStringUTF(mac.c_str());
}

/**
 * Send raw ARP packet
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_sendARPPacket(
    JNIEnv *env, jclass clazz,
    jstring interfaceName, jstring sourceIP, jstring sourceMAC,
    jstring targetIP, jstring targetMAC, jboolean isRequest) {
    
    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return JNI_FALSE;
    }
    
    const char *iface = env->GetStringUTFChars(interfaceName, nullptr);
    const char *src_ip = env->GetStringUTFChars(sourceIP, nullptr);
    const char *src_mac = env->GetStringUTFChars(sourceMAC, nullptr);
    const char *tgt_ip = env->GetStringUTFChars(targetIP, nullptr);
    const char *tgt_mac = env->GetStringUTFChars(targetMAC, nullptr);
    
    LOGD("Sending ARP packet: %s -> %s (request=%d)", src_ip, tgt_ip, isRequest);
    
    bool result = arp_send_packet(iface, src_ip, src_mac, tgt_ip, tgt_mac, isRequest);
    
    env->ReleaseStringUTFChars(interfaceName, iface);
    env->ReleaseStringUTFChars(sourceIP, src_ip);
    env->ReleaseStringUTFChars(sourceMAC, src_mac);
    env->ReleaseStringUTFChars(targetIP, tgt_ip);
    env->ReleaseStringUTFChars(targetMAC, tgt_mac);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

/**
 * Cleanup native resources
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_cleanupNativeOps(
    JNIEnv *env, jclass clazz) {
    
    LOGD("Cleaning up native resources");
    
    if (!g_initialized) {
        return JNI_TRUE;
    }
    
    network_scan_cleanup();
    arp_cleanup();
    
    g_initialized = false;
    LOGD("Native resources cleaned up");
    return JNI_TRUE;
}

} // extern "C"
