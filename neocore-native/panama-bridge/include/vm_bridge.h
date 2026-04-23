#ifndef VM_BRIDGE_H
#define VM_BRIDGE_H

#include <stdint.h>

#if defined(_WIN32)
#  if defined(VM_BRIDGE_BUILD)
#    define VM_BRIDGE_API __declspec(dllexport)
#  else
#    define VM_BRIDGE_API __declspec(dllimport)
#  endif
#else
#  define VM_BRIDGE_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct vm_instance vm_instance_t;
typedef vm_instance_t* vm_handle_t;

VM_BRIDGE_API vm_handle_t create_vm(int32_t memory_size_mb, int32_t disk_size_mb, const char* disk_image);
VM_BRIDGE_API void tick_vm(vm_handle_t handle);
VM_BRIDGE_API void vm_submit_command(vm_handle_t handle, const char* command);
VM_BRIDGE_API const char* vm_disk_image(vm_handle_t handle);
VM_BRIDGE_API const char* vm_terminal_snapshot(vm_handle_t handle);
VM_BRIDGE_API const char* vm_framebuffer_snapshot(vm_handle_t handle);
VM_BRIDGE_API const char* vm_installed_os(vm_handle_t handle);
VM_BRIDGE_API int32_t vm_is_halted(vm_handle_t handle);
VM_BRIDGE_API int32_t vm_boot_count(vm_handle_t handle);
VM_BRIDGE_API void destroy_vm(vm_handle_t handle);

#ifdef __cplusplus
}
#endif

#endif
