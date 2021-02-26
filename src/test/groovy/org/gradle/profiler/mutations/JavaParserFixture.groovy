package org.gradle.profiler.mutations

import com.github.javaparser.JavaParser

trait JavaParserFixture {
    def parse(File sourceFile) {
        return new JavaParser().parse(sourceFile).getResult().get()
    }

    def parse(String code) {
        return new JavaParser().parse(code).getResult().get()
    }
}