#pragma once
#include <cstdint>
#include <string>

namespace ptrace_utils {

    // Находит PID процесса по имени пакета (сканирует /proc).
    int find_pid(const std::string& package_name);

    // Подключается к процессу через PTRACE_ATTACH и ждёт остановки.
    bool attach(int pid);
    bool detach(int pid);

    // Базовый адрес загруженной в remote-процессе библиотеки (из /proc/pid/maps).
    uint64_t get_module_base(int pid, const std::string& module_name);

    // Читает/пишет память remote-процесса через /proc/pid/mem.
    bool read_mem(int pid, uint64_t addr, void* buf, size_t len);
    bool write_mem(int pid, uint64_t addr, const void* buf, size_t len);

    // Выполняет удалённый вызов функции по адресу remote_func_addr с ARM64 ABI
    // (до 8 integer/pointer аргументов в x0..x7), возвращает значение x0 после выполнения.
    // Работает через подмену регистров (PTRACE_GETREGSET/SETREGSET), установку PC
    // на remote_func_addr, LR на заведомо невалидный breakpoint-адрес и ожидание SIGSEGV/SIGTRAP
    // в этой точке (классический call-gadget), после чего регистры восстанавливаются.
    uint64_t remote_call(int pid, uint64_t remote_func_addr, const uint64_t* args, int argc);

    // Выделяет память в remote-процессе через удалённый вызов mmap (RWX).
    uint64_t remote_mmap(int pid, size_t size);

} // namespace ptrace_utils
