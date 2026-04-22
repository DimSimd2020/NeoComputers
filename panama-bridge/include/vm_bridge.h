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

/* Opaque VM instance type. */
typedef struct vm_instance vm_instance_t;
typedef vm_instance_t* vm_handle_t;

/* Creates a VM instance with the requested memory size (in megabytes). */
VM_BRIDGE_API vm_handle_t create_vm(int32_t memory_size_mb);

/* Advances VM execution by one tick. */
VM_BRIDGE_API void tick_vm(vm_handle_t handle);

/* Destroys a VM instance previously created by create_vm. */
VM_BRIDGE_API void destroy_vm(vm_handle_t handle);

#ifdef __cplusplus
}
#endif

#endif /* VM_BRIDGE_H */
