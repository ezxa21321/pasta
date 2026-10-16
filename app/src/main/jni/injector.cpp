#include <jni.h>
#include <string>
#include <cstring>
#include <unistd.h>
#include <dlfcn.h>
#include <sys/ptrace.h>
#include <android/log.h>
#include "ptrace_utils.h"

#define TAG "airware_injector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace ptrace_utils;

namespace {

    // Пишет строку в стек remote-процесса (по низкому адресу относительно SP) и возвращает адрес.
    uint64_t push_remote_string(int pid, uint64_t sp_base, const std::string& str, size_t& cursor) {
        size_t len = str.size() + 1;
        cursor += len;
        cursor = (cursor + 0xF) & ~0xFULL; // выравнивание на 16
        uint64_t addr = sp_base - cursor;
        if (!write_mem(pid, addr, str.c_str(), len)) {
            LOGE("push_remote_string: write failed for '%s'", str.c_str());
            return 0;
        }
        return addr;
    }

    // Находит RVA экспортируемого символа внутри уже загруженного в remote-процессе модуля,
    // читая его локальную копию через dlopen(RTLD_NOLOAD) в ЭТОМ процессе и вычисляя разницу
    // между локальной базой и символом, которая равна RVA — применимо к системным либам
    // с одинаковой версией на обоих процессах (инжектор и жертва работают на одном устройстве/ABI).
    uint64_t resolve_local_rva(const char* lib_name, const char* symbol) {
        void* handle = dlopen(lib_name, RTLD_NOW | RTLD_NOLOAD);
        if (!handle) handle = dlopen(lib_name, RTLD_NOW);
        if (!handle) {
            LOGE("resolve_local_rva: dlopen(%s) failed: %s", lib_name, dlerror());
            return 0;
        }
        void* sym = dlsym(handle, symbol);
        if (!sym) {
            LOGE("resolve_local_rva: dlsym(%s) failed", symbol);
            return 0;
        }

        // Локальная база модуля в ЭТОМ процессе
        char maps_path[64];
        snprintf(maps_path, sizeof(maps_path), "/proc/self/maps");
        FILE* f = fopen(maps_path, "r");
        uint64_t local_base = 0;
        if (f) {
            char line[512];
            while (fgets(line, sizeof(line), f)) {
                if (strstr(line, lib_name)) {
                    local_base = strtoull(line, nullptr, 16);
                    break;
                }
            }
            fclose(f);
        }
        if (!local_base) return 0;

        return (uint64_t)sym - local_base;
    }

    bool do_inject(int target_pid, const std::string& lib_path) {
        if (!attach(target_pid)) {
            LOGE("attach failed, pid=%d (root/SELinux?)", target_pid);
            return false;
        }

        uint64_t libc_base = get_module_base(target_pid, "libc.so");
        uint64_t dlopen_rva = resolve_local_rva("libc.so", "dlopen");
        uint64_t mmap_rva   = resolve_local_rva("libc.so", "mmap");

        if (!libc_base || !dlopen_rva || !mmap_rva) {
            LOGE("failed to resolve libc base/dlopen/mmap (base=0x%llx dlopen_rva=0x%llx mmap_rva=0x%llx)",
                 (unsigned long long)libc_base, (unsigned long long)dlopen_rva, (unsigned long long)mmap_rva);
            detach(target_pid);
            return false;
        }

        uint64_t remote_dlopen = libc_base + dlopen_rva;
        uint64_t remote_mmap_fn = libc_base + mmap_rva;

        // mmap(NULL, 0x1000, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0)
        // — отдельная страница под путь к библиотеке, чтобы не затирать стек жертвы без необходимости.
        uint64_t mmap_args[6] = {
            0, 0x1000, 0x3 /*PROT_READ|PROT_WRITE*/, 0x22 /*MAP_PRIVATE|MAP_ANONYMOUS*/,
            (uint64_t)-1, 0
        };
        uint64_t scratch_page = remote_call(target_pid, remote_mmap_fn, mmap_args, 6);
        if (!scratch_page || scratch_page == (uint64_t)-1) {
            LOGE("remote mmap failed, scratch_page=0x%llx", (unsigned long long)scratch_page);
            detach(target_pid);
            return false;
        }

        if (!write_mem(target_pid, scratch_page, lib_path.c_str(), lib_path.size() + 1)) {
            LOGE("failed writing lib_path to scratch page");
            detach(target_pid);
            return false;
        }

        // dlopen(path, RTLD_NOW)
        uint64_t dlopen_args[2] = { scratch_page, 2 /*RTLD_NOW*/ };
        uint64_t handle = remote_call(target_pid, remote_dlopen, dlopen_args, 2);

        detach(target_pid);

        if (!handle) {
            LOGE("remote dlopen returned NULL — injection failed");
            return false;
        }

        LOGI("injected '%s' into pid=%d, remote handle=0x%llx", lib_path.c_str(), target_pid, (unsigned long long)handle);
        return true;
    }

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_airware_loader_MainActivity_nativeInject(JNIEnv* env, jobject /* this */,
                                                    jstring jTargetPackage, jstring jLibPath) {
    const char* target_cstr = env->GetStringUTFChars(jTargetPackage, nullptr);
    const char* lib_cstr = env->GetStringUTFChars(jLibPath, nullptr);
    std::string target_package(target_cstr);
    std::string lib_path(lib_cstr);
    env->ReleaseStringUTFChars(jTargetPackage, target_cstr);
    env->ReleaseStringUTFChars(jLibPath, lib_cstr);

    int pid = find_pid(target_package);
    if (pid <= 0) {
        LOGE("target package '%s' not running", target_package.c_str());
        return JNI_FALSE;
    }

    bool ok = do_inject(pid, lib_path);
    return ok ? JNI_TRUE : JNI_FALSE;
}
