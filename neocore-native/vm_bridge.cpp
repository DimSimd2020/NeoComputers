#include "vm_bridge.h"

#include <jni.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <iomanip>
#include <limits>
#include <new>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

namespace {

constexpr int kMaxTerminalLines = 24;
constexpr std::string_view kDiskMagic = "NCVM1";
constexpr std::string_view kDefaultOsName = "NeoLinux Alpine";

struct VmInstance {
  std::vector<std::uint8_t> memory;
  int32_t disk_size_mb = 0;
  std::string installed_os;
  std::vector<std::string> terminal;
  int32_t boot_count = 0;
  int32_t ticks = 0;
  bool halted = false;
  std::string disk_image_cache;
  std::string terminal_cache;
  std::string framebuffer_cache;
};

std::string HexEncode(std::string_view value) {
  std::ostringstream out;
  out << std::hex << std::setfill('0');
  for (unsigned char ch : value) {
    out << std::setw(2) << static_cast<int>(ch);
  }
  return out.str();
}

int HexValue(char ch) {
  if (ch >= '0' && ch <= '9') {
    return ch - '0';
  }
  ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
  if (ch >= 'a' && ch <= 'f') {
    return ch - 'a' + 10;
  }
  return -1;
}

std::string HexDecode(std::string_view value) {
  std::string out;
  out.reserve(value.size() / 2);
  for (size_t i = 0; i + 1 < value.size(); i += 2) {
    const int high = HexValue(value[i]);
    const int low = HexValue(value[i + 1]);
    if (high < 0 || low < 0) {
      return {};
    }
    out.push_back(static_cast<char>((high << 4) | low));
  }
  return out;
}

std::string Trim(std::string_view value) {
  size_t first = 0;
  while (first < value.size() && std::isspace(static_cast<unsigned char>(value[first]))) {
    first++;
  }
  size_t last = value.size();
  while (last > first && std::isspace(static_cast<unsigned char>(value[last - 1]))) {
    last--;
  }
  return std::string(value.substr(first, last - first));
}

std::string Lower(std::string_view value) {
  std::string out(value);
  std::transform(out.begin(), out.end(), out.begin(), [](unsigned char ch) {
    return static_cast<char>(std::tolower(ch));
  });
  return out;
}

void AppendLine(VmInstance& vm, std::string line) {
  vm.terminal.push_back(std::move(line));
  if (vm.terminal.size() > kMaxTerminalLines) {
    vm.terminal.erase(vm.terminal.begin(), vm.terminal.begin() + (vm.terminal.size() - kMaxTerminalLines));
  }
}

void ParseDiskImage(VmInstance& vm, const char* disk_image) {
  if (disk_image == nullptr || disk_image[0] == '\0') {
    return;
  }

  std::istringstream input(disk_image);
  std::string line;
  std::getline(input, line);
  if (line != kDiskMagic) {
    return;
  }

  vm.terminal.clear();
  while (std::getline(input, line)) {
    const size_t equals = line.find('=');
    if (equals == std::string::npos) {
      continue;
    }
    std::string key = line.substr(0, equals);
    std::string value = line.substr(equals + 1);
    if (key == "os") {
      vm.installed_os = HexDecode(value);
    } else if (key == "boot") {
      try {
        vm.boot_count = std::max(0, std::stoi(value));
      } catch (...) {
        vm.boot_count = 0;
      }
    } else if (key == "halted") {
      vm.halted = value == "1";
    } else if (key == "line") {
      AppendLine(vm, HexDecode(value));
    }
  }
}

void Boot(VmInstance& vm) {
  vm.halted = false;
  vm.boot_count++;
  AppendLine(vm, "Boot #" + std::to_string(vm.boot_count) + ": " + (vm.installed_os.empty() ? "NeoBIOS" : vm.installed_os));
  if (vm.installed_os.empty()) {
    AppendLine(vm, "No OS found. Type: install alpine");
  } else {
    AppendLine(vm, "localhost login: root");
  }
}

std::string SerializeDiskImage(const VmInstance& vm) {
  std::ostringstream out;
  out << kDiskMagic << '\n';
  out << "os=" << HexEncode(vm.installed_os) << '\n';
  out << "boot=" << vm.boot_count << '\n';
  out << "halted=" << (vm.halted ? "1" : "0") << '\n';
  for (const std::string& line : vm.terminal) {
    out << "line=" << HexEncode(line) << '\n';
  }
  return out.str();
}

std::string TerminalSnapshot(const VmInstance& vm) {
  std::ostringstream out;
  for (size_t i = 0; i < vm.terminal.size(); i++) {
    if (i != 0) {
      out << '\n';
    }
    out << vm.terminal[i];
  }
  return out.str();
}

std::string StatusLine(const VmInstance& vm) {
  return "power=on os=" + (vm.installed_os.empty() ? std::string("none") : vm.installed_os)
      + " ram=" + std::to_string(vm.memory.size() / (1024 * 1024)) + "MB"
      + " disk=" + std::to_string(vm.disk_size_mb) + "MB"
      + " ticks=" + std::to_string(vm.ticks);
}

void RenderFramebuffer(VmInstance& vm) {
  std::ostringstream out;
  out << "+--------------------------------------+\n";
  out << "| " << (vm.installed_os.empty() ? "NeoBIOS" : vm.installed_os);
  std::string title = vm.installed_os.empty() ? "NeoBIOS" : vm.installed_os;
  for (int i = static_cast<int>(title.size()); i < 36; i++) {
    out << ' ';
  }
  out << " |\n";
  out << "| boot=" << vm.boot_count << " ticks=" << vm.ticks;
  std::string metrics = "boot=" + std::to_string(vm.boot_count) + " ticks=" + std::to_string(vm.ticks);
  for (int i = static_cast<int>(metrics.size()); i < 36; i++) {
    out << ' ';
  }
  out << " |\n";
  out << "| prompt: " << (vm.installed_os.empty() ? "NeoBIOS>" : "root@neocomputer:~#");
  std::string prompt = "prompt: " + std::string(vm.installed_os.empty() ? "NeoBIOS>" : "root@neocomputer:~#");
  for (int i = static_cast<int>(prompt.size()); i < 36; i++) {
    out << ' ';
  }
  out << " |\n";
  out << "+--------------------------------------+";
  vm.framebuffer_cache = out.str();
}

void RefreshCaches(VmInstance& vm) {
  vm.disk_image_cache = SerializeDiskImage(vm);
  vm.terminal_cache = TerminalSnapshot(vm);
  RenderFramebuffer(vm);
}

void InstallNeoLinux(VmInstance& vm) {
  if (vm.disk_size_mb < 128) {
    AppendLine(vm, "Install failed: at least 128 MB storage is required.");
    return;
  }
  AppendLine(vm, "Formatting disk " + std::to_string(vm.disk_size_mb) + "MB...");
  AppendLine(vm, "Installing NeoLinux Alpine...");
  vm.installed_os = std::string(kDefaultOsName);
  AppendLine(vm, "Install complete. Reboot to start Linux.");
}

VmInstance* ToVm(vm_handle_t handle) {
  return reinterpret_cast<VmInstance*>(handle);
}

VmInstance* ToVm(jlong handle) {
  return reinterpret_cast<VmInstance*>(static_cast<intptr_t>(handle));
}

jstring ToJavaString(JNIEnv* env, const char* value) {
  return env->NewStringUTF(value == nullptr ? "" : value);
}

}  // namespace

extern "C" VM_BRIDGE_API vm_handle_t create_vm(int32_t memory_size_mb, int32_t disk_size_mb, const char* disk_image) {
  constexpr size_t kBytesInMb = 1024ull * 1024ull;
  if (memory_size_mb < 0 || disk_size_mb < 0) {
    return nullptr;
  }
  if (static_cast<size_t>(memory_size_mb) > (std::numeric_limits<size_t>::max() / kBytesInMb)) {
    return nullptr;
  }

  auto* vm = new (std::nothrow) VmInstance{};
  if (vm == nullptr) {
    return nullptr;
  }

  vm->memory.resize(static_cast<size_t>(memory_size_mb) * kBytesInMb);
  vm->disk_size_mb = disk_size_mb;
  ParseDiskImage(*vm, disk_image);
  Boot(*vm);
  RefreshCaches(*vm);
  return reinterpret_cast<vm_handle_t>(vm);
}

extern "C" VM_BRIDGE_API void tick_vm(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  if (vm == nullptr || vm->halted) {
    return;
  }
  vm->ticks++;
  RefreshCaches(*vm);
}

extern "C" VM_BRIDGE_API void vm_submit_command(vm_handle_t handle, const char* command) {
  VmInstance* vm = ToVm(handle);
  if (vm == nullptr || command == nullptr) {
    return;
  }

  const std::string trimmed = Trim(command);
  if (trimmed.empty()) {
    return;
  }
  const std::string normalized = Lower(trimmed);

  if (normalized == "clear") {
    vm->terminal.clear();
    RefreshCaches(*vm);
    return;
  }

  if (vm->halted && normalized != "reboot") {
    AppendLine(*vm, "System halted. Type reboot or power cycle.");
    RefreshCaches(*vm);
    return;
  }

  AppendLine(*vm, "> " + trimmed);
  if (normalized == "help") {
    AppendLine(*vm, "commands: help, status, install alpine, uname, ls, df, clear, shutdown, reboot");
  } else if (normalized == "status") {
    AppendLine(*vm, StatusLine(*vm));
  } else if (normalized == "install alpine" || normalized == "install neolinux") {
    InstallNeoLinux(*vm);
  } else if (normalized == "uname") {
    AppendLine(*vm, vm->installed_os.empty() ? "NeoBIOS firmware shell" : "NeoLinux neocomputer 0.1-alpine");
  } else if (normalized == "ls") {
    AppendLine(*vm, vm->installed_os.empty() ? "boot storage firmware" : "bin boot dev etc home root usr var");
  } else if (normalized == "df") {
    AppendLine(*vm, "/dev/sda " + std::to_string(vm->disk_size_mb) + "MB ncfs");
  } else if (normalized == "shutdown" || normalized == "poweroff") {
    AppendLine(*vm, "System halted.");
    vm->halted = true;
  } else if (normalized == "reboot") {
    AppendLine(*vm, "Rebooting.");
    Boot(*vm);
  } else {
    AppendLine(*vm, "command not found: " + trimmed);
  }
  RefreshCaches(*vm);
}

extern "C" VM_BRIDGE_API const char* vm_disk_image(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  return vm == nullptr ? "" : vm->disk_image_cache.c_str();
}

extern "C" VM_BRIDGE_API const char* vm_terminal_snapshot(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  return vm == nullptr ? "" : vm->terminal_cache.c_str();
}

extern "C" VM_BRIDGE_API const char* vm_framebuffer_snapshot(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  return vm == nullptr ? "" : vm->framebuffer_cache.c_str();
}

extern "C" VM_BRIDGE_API const char* vm_installed_os(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  return vm == nullptr ? "" : vm->installed_os.c_str();
}

extern "C" VM_BRIDGE_API int32_t vm_is_halted(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  return vm != nullptr && vm->halted ? 1 : 0;
}

extern "C" VM_BRIDGE_API int32_t vm_boot_count(vm_handle_t handle) {
  VmInstance* vm = ToVm(handle);
  return vm == nullptr ? 0 : vm->boot_count;
}

extern "C" VM_BRIDGE_API void destroy_vm(vm_handle_t handle) {
  delete ToVm(handle);
}

extern "C" JNIEXPORT jlong JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_createVm(
    JNIEnv* env, jobject, jint memory_size_mb, jint disk_size_mb, jstring disk_image) {
  const char* disk_image_chars = disk_image == nullptr ? nullptr : env->GetStringUTFChars(disk_image, nullptr);
  VmInstance* vm = reinterpret_cast<VmInstance*>(create_vm(memory_size_mb, disk_size_mb, disk_image_chars));
  if (disk_image_chars != nullptr) {
    env->ReleaseStringUTFChars(disk_image, disk_image_chars);
  }
  return static_cast<jlong>(reinterpret_cast<intptr_t>(vm));
}

extern "C" JNIEXPORT void JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_tickVm(
    JNIEnv*, jobject, jlong handle) {
  tick_vm(reinterpret_cast<vm_handle_t>(ToVm(handle)));
}

extern "C" JNIEXPORT void JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_submitCommand(
    JNIEnv* env, jobject, jlong handle, jstring command) {
  const char* command_chars = command == nullptr ? nullptr : env->GetStringUTFChars(command, nullptr);
  vm_submit_command(reinterpret_cast<vm_handle_t>(ToVm(handle)), command_chars);
  if (command_chars != nullptr) {
    env->ReleaseStringUTFChars(command, command_chars);
  }
}

extern "C" JNIEXPORT jstring JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_diskImage(
    JNIEnv* env, jobject, jlong handle) {
  return ToJavaString(env, vm_disk_image(reinterpret_cast<vm_handle_t>(ToVm(handle))));
}

extern "C" JNIEXPORT jstring JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_terminalSnapshot(
    JNIEnv* env, jobject, jlong handle) {
  return ToJavaString(env, vm_terminal_snapshot(reinterpret_cast<vm_handle_t>(ToVm(handle))));
}

extern "C" JNIEXPORT jstring JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_framebufferSnapshot(
    JNIEnv* env, jobject, jlong handle) {
  return ToJavaString(env, vm_framebuffer_snapshot(reinterpret_cast<vm_handle_t>(ToVm(handle))));
}

extern "C" JNIEXPORT jstring JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_installedOs(
    JNIEnv* env, jobject, jlong handle) {
  return ToJavaString(env, vm_installed_os(reinterpret_cast<vm_handle_t>(ToVm(handle))));
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_isHalted(
    JNIEnv*, jobject, jlong handle) {
  return vm_is_halted(reinterpret_cast<vm_handle_t>(ToVm(handle))) != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_bootCount(
    JNIEnv*, jobject, jlong handle) {
  return vm_boot_count(reinterpret_cast<vm_handle_t>(ToVm(handle)));
}

extern "C" JNIEXPORT void JNICALL Java_com_dimsimd_neocomputers_vm_JniVmBridge_destroyVm(
    JNIEnv*, jobject, jlong handle) {
  destroy_vm(reinterpret_cast<vm_handle_t>(ToVm(handle)));
}
