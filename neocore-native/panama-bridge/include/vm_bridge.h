#ifndef VM_BRIDGE_H
#define VM_BRIDGE_H

#include <stddef.h>

#if defined(_WIN32) || defined(__CYGWIN__)
#if defined(VM_BRIDGE_BUILD)
#define VM_BRIDGE_API __declspec(dllexport)
#else
#define VM_BRIDGE_API __declspec(dllimport)
#endif
#else
#define VM_BRIDGE_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct VmInstance VmInstance;

VM_BRIDGE_API VmInstance *create_vm(size_t memory_size_mb);
VM_BRIDGE_API void tick_vm(VmInstance *vm);
VM_BRIDGE_API void destroy_vm(VmInstance *vm);

#ifdef __cplusplus
}
#endif

#endif
