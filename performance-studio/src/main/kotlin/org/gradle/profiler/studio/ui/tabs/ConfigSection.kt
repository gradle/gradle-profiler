package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.Daemon
import org.gradle.profiler.studio.domain.Mode
import org.gradle.profiler.studio.domain.MutatorEntry
import org.gradle.profiler.studio.domain.ProfilerOptions
import org.gradle.profiler.studio.domain.RunUsing
import org.gradle.profiler.studio.ui.FilePicker
import org.gradle.profiler.studio.ui.components.StringDropdown
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.io.File

@Composable
fun ConfigSection(
    config: ConfigDraft,
    readOnly: Boolean,
    projectDir: File,
    onChange: (ConfigDraft) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Scenario",
                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            DefaultButton(onClick = onRun, enabled = !readOnly && config.tasks.isNotBlank()) {
                Text("Run")
            }
        }

        Field("Tasks *") {
            FormTextField(
                value = config.tasks,
                onValueChange = { onChange(config.copy(tasks = it)) },
                placeholder = "e.g. clean assemble",
                enabled = !readOnly,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Warm-ups", Modifier.width(120.dp)) {
                FormTextField(
                    value = config.warmups,
                    onValueChange = { onChange(config.copy(warmups = it.filter(Char::isDigit))) },
                    enabled = !readOnly,
                )
            }
            Field("Iterations", Modifier.width(120.dp)) {
                FormTextField(
                    value = config.iterations,
                    onValueChange = { onChange(config.copy(iterations = it.filter(Char::isDigit))) },
                    enabled = !readOnly,
                )
            }
        }

        Field("Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mode.entries.forEach { mode ->
                    RadioButtonRow(
                        text = mode.label,
                        selected = config.mode == mode,
                        onClick = { onChange(config.copy(mode = mode)) },
                        enabled = !readOnly,
                    )
                }
            }
        }

        if (config.mode == Mode.Profile) {
            Field("Profiler") {
                StringDropdown(
                    options = ProfilerOptions.profilers,
                    selected = config.profiler,
                    onSelected = { onChange(config.copy(profiler = it)) },
                    enabled = !readOnly,
                )
            }
        }

        Field("Gradle args") {
            FormTextField(
                value = config.gradleArgs,
                onValueChange = { onChange(config.copy(gradleArgs = it)) },
                placeholder = "-Dorg.gradle.parallel=true",
                enabled = !readOnly,
            )
        }

        Field("JVM args") {
            FormTextField(
                value = config.jvmArgs,
                onValueChange = { onChange(config.copy(jvmArgs = it)) },
                placeholder = "-Xmx2g",
                enabled = !readOnly,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Daemon", Modifier.weight(1f)) {
                StringDropdown(
                    options = Daemon.entries.map { it.label },
                    selected = config.daemon.label,
                    onSelected = { lbl -> onChange(config.copy(daemon = Daemon.entries.first { it.label == lbl })) },
                    enabled = !readOnly,
                )
            }
            Field("Run using", Modifier.weight(1f)) {
                StringDropdown(
                    options = RunUsing.entries.map { it.label },
                    selected = config.runUsing.label,
                    onSelected = { lbl -> onChange(config.copy(runUsing = RunUsing.entries.first { it.label == lbl })) },
                    enabled = !readOnly,
                )
            }
        }

        Field("Gradle user home (optional)") {
            FormTextField(
                value = config.gradleUserHome,
                onValueChange = { onChange(config.copy(gradleUserHome = it)) },
                placeholder = "default: ~/.gradle",
                enabled = !readOnly,
            )
        }

        Field("Build mutators (${config.mutators.size})") {
            MutatorList(
                mutators = config.mutators,
                projectDir = projectDir,
                enabled = !readOnly,
                onChange = { onChange(config.copy(mutators = it)) },
            )
        }
    }
}

@Composable
private fun MutatorList(
    mutators: List<MutatorEntry>,
    projectDir: File,
    enabled: Boolean,
    onChange: (List<MutatorEntry>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        mutators.forEachIndexed { index, entry ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StringDropdown(
                    options = ProfilerOptions.mutatorTypes,
                    selected = entry.type,
                    onSelected = { type ->
                        onChange(mutators.toMutableList().apply { set(index, entry.copy(type = type)) })
                    },
                    enabled = enabled,
                    modifier = Modifier.width(280.dp),
                )
                FormTextField(
                    value = entry.relativePath,
                    onValueChange = { path ->
                        onChange(mutators.toMutableList().apply { set(index, entry.copy(relativePath = path)) })
                    },
                    placeholder = "src/main/java/Foo.java",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        FilePicker.pick(startDir = projectDir, title = "Choose target file")?.let { picked ->
                            val rel = runCatching {
                                projectDir.toPath().relativize(picked.toPath()).toString()
                            }.getOrDefault(picked.absolutePath)
                            onChange(mutators.toMutableList().apply { set(index, entry.copy(relativePath = rel)) })
                        }
                    },
                    enabled = enabled,
                ) { Text("Browse") }
                OutlinedButton(
                    onClick = {
                        onChange(mutators.toMutableList().apply { removeAt(index) })
                    },
                    enabled = enabled,
                ) { Text("✗") }
            }
        }
        OutlinedButton(
            onClick = {
                val defaultType = ProfilerOptions.mutatorTypes.firstOrNull().orEmpty()
                onChange(mutators + MutatorEntry(defaultType, ""))
            },
            enabled = enabled,
        ) { Text("+ Add mutator") }
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium))
        content()
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var state by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (state.text != value) state = TextFieldValue(value, TextRange(value.length))
    }
    TextField(
        value = state,
        onValueChange = {
            state = it
            if (it.text != value) onValueChange(it.text)
        },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, style = JewelTheme.defaultTextStyle) },
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun <T> EnumPicker(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        values.forEach { v ->
            RadioButtonRow(
                text = label(v),
                selected = v == selected,
                onClick = { onSelect(v) },
                enabled = enabled,
            )
        }
    }
}
