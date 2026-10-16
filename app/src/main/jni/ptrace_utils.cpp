#include "ptrace_utils.h"
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <sys/user.h>
#include <elf.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <cstring>
#include <cstdio>
#include <android/log.h>

#define TAG "airware_injector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace ptrace_utils {

    int find_pid(const std::string& package_name) {
        DIR* dir = opendir("/proc");
        if (!dir) return -1;

        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_type != DT_DIR) continue;
            int pid = atoi(entry->d_name);
            if (pid <= 0) continue;

            char cmdline_path[64];
            snprintf(cmdline_path, sizeof(cmdline_path), "/proc/%d/cmdline", pid);
            FILE* f = fopen(cmdline_path, "r");
            if (!f) continue;

            char buf[256] = {0};
            fread(buf, 1, sizeof(buf) - 1, f);
            fclose(f);

            if (package_name == buf) {
                closedir(dir);
                return pid;
            }
        }
        closedir(dir);
        return -1;
    }

    bool attach(int pid) {
        if (ptrace(PTRACE_ATTACH, pid, nullptr, nullptr) != 0) {
            LOGE("PTRACE_ATTACH failed for pid=%d: %s", pid, strerror(errno));
            return false;
        }
        int status = 0;
        waitpid(pid, &status, 0);
        return WIFSTOPPED(status);
    }

    bool detach(int pid) {
        return ptrace(PTRACE_DETACH, pid, nullptr, nullptr) == 0;
    }

    uint64_t get_module_base(int pid, const std::string& module_name) {
        char maps_path[64];
        snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", pid);
        FILE* f = fopen(maps_path, "r");
        if (!f) return 0;

        char line[512];
        uint64_t base = 0;
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, module_name.c_str())) {
                base = strtoull(line, nullptr, 16);
                break;
            }
        }
        fclose(f);
        return base;
    }

    bool read_mem(int pid, uint64_t addr, void* buf, size_t len) {
        char mem_path[64];
        snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
        int fd = open(mem_path, O_RDONLY);
        if (fd < 0) return false;
        bool ok = pread(fd, buf, len, (off_t)addr) == (ssize_t)len;
        close(fd);
        return ok;
    }

    bool write_mem(int pid, uint64_t addr, const void* buf, size_t len) {
        char mem_path[64];
        snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
        int fd = open(mem_path, O_WRONLY);
        if (fd < 0) return false;
        bool ok = pwrite(fd, buf, len, (off_t)addr) == (ssize_t)len;
        close(fd);
        return ok;
    }

    // ARM64 remote-call: подставляем аргументы в x0-x7, PC на целевую функцию,
    // LR на нулевой адрес (вызовет SIGSEGV при return — это и есть точка остановки).
    uint64_t remote_call(int pid, uint64_t remote_func_addr, const uint64_t* args, int argc) {
        struct user_pt_regs regs{};
        struct iovec iov{&regs, sizeof(regs)};

        if (ptrace(PTRACE_GETREGSET, pid, (void*)NT_PRSTATUS, &iov) != 0) {
            LOGE("GETREGSET failed: %s", strerror(errno));
            return 0;
        }

        struct user_pt_regs saved_regs = regs;

        for (int i = 0; i < argc && i < 8; i++) {
            regs.regs[i] = args[i];
        }
        regs.pc = remote_func_addr;
        regs.regs[30] = 0; // LR = 0 -> вернёт SIGSEGV на адресе 0, используем как брейкпоинт
        regs.sp = (regs.sp - 0x200) & ~0xFULL; // небольшой запас под стек, выровнен на 16

        iov.iov_base = &regs;
        if (ptrace(PTRACE_SETREGSET, pid, (void*)NT_PRSTATUS, &iov) != 0) {
            LOGE("SETREGSET failed: %s", strerror(errno));
            return 0;
        }

        if (ptrace(PTRACE_CONT, pid, nullptr, nullptr) != 0) {
            LOGE("PTRACE_CONT failed: %s", strerror(errno));
            return 0;
        }

        int status = 0;
        waitpid(pid, &status, 0);

        uint64_t result = 0;
        if (WIFSTOPPED(status)) {
            struct user_pt_regs result_regs{};
            struct iovec riov{&result_regs, sizeof(result_regs)};
            if (ptrace(PTRACE_GETREGSET, pid, (void*)NT_PRSTATUS, &riov) == 0) {
                result = result_regs.regs[0];
            }
        } else {
            LOGE("remote_call: unexpected stop status=0x%x", status);
        }

        // Восстанавливаем исходный контекст потока
        struct iovec restore_iov{&saved_regs, sizeof(saved_regs)};
        ptrace(PTRACE_SETREGSET, pid, (void*)NT_PRSTATUS, &restore_iov);

        return result;
    }

    uint64_t remote_mmap(int pid, size_t size) {
        uint64_t libc_base = get_module_base(pid, "libc.so");
        if (!libc_base) {
            LOGE("remote_mmap: libc.so not found in target");
            return 0;
        }
        // NOTE: нужен реальный RVA mmap() из libc.so на целевой архитектуре/прошивке;
        // на практике проще резолвить его динамически через remote dlsym (см. injector.cpp).
        (void)size;
        return 0;
    }

} // namespace ptrace_utils
