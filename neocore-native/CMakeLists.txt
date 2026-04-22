cmake_minimum_required(VERSION 3.20)

project(neocore_native_vm_bridge LANGUAGES CXX)

add_library(vm_bridge SHARED
  vm_bridge.cpp
)

target_compile_features(vm_bridge PUBLIC cxx_std_20)

target_include_directories(vm_bridge PUBLIC
  ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(vm_bridge PRIVATE VM_BRIDGE_BUILD)
