enum class Os(val requirementName: String) {
    linux("Linux"),
    macos("Mac OS X"),
    windows("Windows")
}

enum class Arch(val nameOnLinuxWindows: String, val nameOnMac: String) {
    AMD64("amd64", "x86_64"),
    AARCH64("aarch64", "aarch64");
}
