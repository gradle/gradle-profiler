package org.gradle.profiler.studio

class AndroidStudioTestSupport {

    static setupLocalProperties(File localProperties) {
        Optional.ofNullable(findAndroidSdkPath()).ifPresent { path -> localProperties << "\nsdk.dir=$path\n" }
    }

    static String findAndroidSdkPath() {
        if (System.getenv("ANDROID_SDK_ROOT") != null) {
            return System.getenv("ANDROID_SDK_ROOT").replace("\\", "/")
        }
        String userDirAndroidSdkPath = "${System.getProperty("user.home").replace("\\", "/")}/Library/Android/sdk"
        if (!new File(userDirAndroidSdkPath).exists()) {
            return null
        }
        return userDirAndroidSdkPath
    }
}
