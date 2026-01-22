package org.gradle.profiler.studio

import org.apache.commons.io.FilenameUtils

class AndroidStudioTestSupport {

    static setupLocalProperties(File localProperties) {
        Optional.ofNullable(findAndroidSdkPath()).ifPresent { path -> localProperties << "\nsdk.dir=$path\n" }
    }

    static String findAndroidSdkPath() {
        if (System.getenv("ANDROID_HOME") != null) {
            return FilenameUtils.separatorsToUnix(System.getenv("ANDROID_HOME"))
        }
        if (System.getenv("ANDROID_SDK_ROOT") != null) {
            return FilenameUtils.separatorsToUnix(System.getenv("ANDROID_SDK_ROOT"))
        }
        String userDirAndroidSdkPath = FilenameUtils.separatorsToUnix("${System.getProperty("user.home")}/Library/Android/sdk")
        if (!new File(userDirAndroidSdkPath).exists()) {
            return null
        }
        return userDirAndroidSdkPath
    }
}
