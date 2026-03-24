enum class Os(val requirementName: String) {
    linux("Linux"),
    macos("Mac OS X"),
    windows("Windows")
}

enum class Arch(val nameOnLinuxWindows: String, val nameOnMac: String, val jdkName: String) {
    AMD64("amd64", "x86_64", "64bit"),
    AARCH64("aarch64", "aarch64", "aarch64"),;
}

fun Arch.localName(os: Os): String = if (os == Os.macos) {
    nameOnMac
} else {
    nameOnLinuxWindows
}
