#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <vector>
#include "arp_operations.h"
#include "network_scan.h"
#include "dns_handler.h"
#include "dhcp_spoofing.h"

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
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter

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
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter
    
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
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter
    
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
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter
    
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
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter
    
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
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter

    LOGD("Cleaning up native resources");

    if (!g_initialized) {
        return JNI_TRUE;
    }

    network_scan_cleanup();
    arp_cleanup();
    dhcp_spoof_cleanup();

    g_initialized = false;
    LOGD("Native resources cleaned up");
    return JNI_TRUE;
}


/**
 * Initialize DHCP spoofing
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_initializeDHCPSpoof(
    JNIEnv *env, jclass clazz) {
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter

    LOGD("Initializing DHCP spoofing operations");
    bool result = dhcp_spoof_init();
    return result ? JNI_TRUE : JNI_FALSE;
}

/**
 * Start DHCP spoofing
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_startDHCPSpoof(
    JNIEnv *env, jclass clazz,
    jstring interfaceName,
    jobjectArray targetMacs,
    jobjectArray spoofedIPs,
    jobjectArray gatewayIPs,
    jobjectArray subnetMasks,
    jobjectArray dnsServers) {
    (void)clazz;  // Unused parameter

    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return JNI_FALSE;
    }

    const char *iface = env->GetStringUTFChars(interfaceName, nullptr);

    jsize arraySize = env->GetArrayLength(targetMacs);
    if (arraySize != env->GetArrayLength(spoofedIPs) ||
        arraySize != env->GetArrayLength(gatewayIPs) ||
        arraySize != env->GetArrayLength(subnetMasks) ||
        arraySize != env->GetArrayLength(dnsServers)) {
        LOGE("All DHCP spoofing arrays must have the same size");
        env->ReleaseStringUTFChars(interfaceName, iface);
        return JNI_FALSE;
    }

    // Build rules vector
    std::vector<DHCPSpoofRule> rules;
    for (jsize i = 0; i < arraySize; i++) {
        jstring target_mac = (jstring)env->GetObjectArrayElement(targetMacs, i);
        jstring ip = (jstring)env->GetObjectArrayElement(spoofedIPs, i);
        jstring gateway = (jstring)env->GetObjectArrayElement(gatewayIPs, i);
        jstring subnet = (jstring)env->GetObjectArrayElement(subnetMasks, i);
        jstring dns = (jstring)env->GetObjectArrayElement(dnsServers, i);

        const char *target_mac_str = env->GetStringUTFChars(target_mac, nullptr);
        const char *ip_str = env->GetStringUTFChars(ip, nullptr);
        const char *gateway_str = env->GetStringUTFChars(gateway, nullptr);
        const char *subnet_str = env->GetStringUTFChars(subnet, nullptr);
        const char *dns_str = env->GetStringUTFChars(dns, nullptr);

        rules.push_back({
            std::string(target_mac_str),
            std::string(ip_str),
            std::string(gateway_str),
            std::string(subnet_str),
            std::string(dns_str)
        });

        env->ReleaseStringUTFChars(target_mac, target_mac_str);
        env->ReleaseStringUTFChars(ip, ip_str);
        env->ReleaseStringUTFChars(gateway, gateway_str);
        env->ReleaseStringUTFChars(subnet, subnet_str);
        env->ReleaseStringUTFChars(dns, dns_str);

        env->DeleteLocalRef(target_mac);
        env->DeleteLocalRef(ip);
        env->DeleteLocalRef(gateway);
        env->DeleteLocalRef(subnet);
        env->DeleteLocalRef(dns);
    }

    bool result = dhcp_start_spoofing(iface, rules);

    env->ReleaseStringUTFChars(interfaceName, iface);

    return result ? JNI_TRUE : JNI_FALSE;
}

/**
 * Stop DHCP spoofing
 */
JNIEXPORT void JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_stopDHCPSpoof(
    JNIEnv *env, jclass clazz) {
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter

    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return;
    }

    dhcp_stop_spoofing();
}

/**
 * Add DHCP spoofing rule
 */
JNIEXPORT void JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_addDHCPSpoofRule(
    JNIEnv *env, jclass clazz,
    jstring targetMac, jstring spoofedIP, jstring gatewayIP,
    jstring subnetMask, jstring dnsServer) {
    (void)clazz;  // Unused parameter

    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return;
    }

    const char *target_mac_str = env->GetStringUTFChars(targetMac, nullptr);
    const char *ip_str = env->GetStringUTFChars(spoofedIP, nullptr);
    const char *gateway_str = env->GetStringUTFChars(gatewayIP, nullptr);
    const char *subnet_str = env->GetStringUTFChars(subnetMask, nullptr);
    const char *dns_str = env->GetStringUTFChars(dnsServer, nullptr);

    dhcp_add_rule(target_mac_str, ip_str, gateway_str, subnet_str, dns_str);

    env->ReleaseStringUTFChars(targetMac, target_mac_str);
    env->ReleaseStringUTFChars(spoofedIP, ip_str);
    env->ReleaseStringUTFChars(gatewayIP, gateway_str);
    env->ReleaseStringUTFChars(subnetMask, subnet_str);
    env->ReleaseStringUTFChars(dnsServer, dns_str);
}

/**
 * Remove DHCP spoofing rule
 */
JNIEXPORT void JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_removeDHCPSpoofRule(
    JNIEnv *env, jclass clazz,
    jstring targetMac) {
    (void)clazz;  // Unused parameter

    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return;
    }

    const char *target_mac_str = env->GetStringUTFChars(targetMac, nullptr);

    dhcp_remove_rule(target_mac_str);

    env->ReleaseStringUTFChars(targetMac, target_mac_str);
}

/**
 * Check if DHCP spoofing is active
 */
JNIEXPORT jboolean JNICALL
Java_com_vishal_harpy_core_native_NativeNetworkOps_isDHCPSpoofActive(
    JNIEnv *env, jclass clazz) {
    (void)env;  // Unused parameter
    (void)clazz;  // Unused parameter

    if (!g_initialized) {
        LOGE("Native operations not initialized");
        return JNI_FALSE;
    }

    bool result = dhcp_is_active();
    return result ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
