package org.gradle.profiler.idea

import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.ide.starter.ide.installer.StandardInstaller
import com.intellij.ide.starter.models.IdeInfo
import java.nio.file.Path

internal class DefaultIdeInstallerFactory(private val installersDownloadDir: Path) : IdeInstallerFactory() {
    override fun createInstaller(
        ideInfo: IdeInfo,
        downloader: IdeDownloader
    ): IdeInstaller = StandardInstaller(downloader, installersDownloadDir)
}
