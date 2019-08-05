package org.gradle.profiler.buildops;

public class BuildOperationUtil {
    public static String getSimpleBuildOperationName(String buildOperationDetailsClass) {
        int lastDot = buildOperationDetailsClass.lastIndexOf('.');
        return lastDot == -1
            ? buildOperationDetailsClass
            : buildOperationDetailsClass.substring(lastDot + 1);
    }
}
