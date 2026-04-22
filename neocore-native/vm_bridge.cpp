#include "panama-bridge/include/vm_bridge.h"

#include <cstdlib>
#include <iostream>
#include <limits>
#include <new>

struct VmInstance {
  void *memory;
  size_t memory_size_bytes;
};

VmInstance *create_vm(size_t memory_size_mb) {
  constexpr size_t kBytesInMb = 1024ull * 1024ull;

  if (memory_size_mb > (std::numeric_limits<size_t>::max() / kBytesInMb)) {
    std::cerr << "create_vm failed: memory size overflow\n";
    return nullptr;
  }

  const size_t memory_size_bytes = memory_size_mb * kBytesInMb;

  auto *vm = new (std::nothrow) VmInstance{};
  if (vm == nullptr) {
    std::cerr << "create_vm failed: VmInstance allocation failed\n";
    return nullptr;
  }

  vm->memory_size_bytes = memory_size_bytes;
  vm->memory = (memory_size_bytes == 0) ? nullptr : std::malloc(memory_size_bytes);

  if ((memory_size_bytes != 0) && (vm->memory == nullptr)) {
    std::cerr << "create_vm failed: VM memory allocation failed\n";
    delete vm;
    return nullptr;
  }

  return vm;
}

void tick_vm(VmInstance *vm) {
  if (vm == nullptr) {
    std::cerr << "tick_vm called with null VmInstance\n";
    return;
  }

  std::cout << "VM Ticked\n";
}

void destroy_vm(VmInstance *vm) {
  if (vm == nullptr) {
    return;
  }

  std::free(vm->memory);
  vm->memory = nullptr;
  vm->memory_size_bytes = 0;
  delete vm;
}
