package org.gradle.profiler.studio.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

object FolderPicker {
    fun pick(parent: Frame? = null, title: String = "Choose project folder"): File? {
        return if (System.getProperty("os.name").lowercase().contains("mac")) {
            pickMac(parent, title)
        } else {
            pickSwing(parent, title)
        }
    }

    private fun pickMac(parent: Frame?, title: String): File? {
        val previous = System.getProperty("apple.awt.fileDialogForDirectories")
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = FileDialog(parent, title, FileDialog.LOAD).apply {
                isVisible = true
            }
            val name = dialog.file ?: return null
            val dir = dialog.directory ?: return null
            return File(dir, name)
        } finally {
            if (previous == null) System.clearProperty("apple.awt.fileDialogForDirectories")
            else System.setProperty("apple.awt.fileDialogForDirectories", previous)
        }
    }

    private fun pickSwing(parent: Frame?, title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
