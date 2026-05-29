package org.gradle.profiler.studio.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.gradle.profiler.studio.domain.ConfigDraft
import org.gradle.profiler.studio.domain.Daemon
import org.gradle.profiler.studio.domain.Mode
import org.gradle.profiler.studio.domain.RunUsing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun ConfigSection(
    config: ConfigDraft,
    readOnly: Boolean,
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
                FormTextField(
                    value = config.profiler,
                    onValueChange = { onChange(config.copy(profiler = it)) },
                    placeholder = "jfr | async-profiler | none",
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
                EnumPicker(Daemon.entries, config.daemon, { it.label }, enabled = !readOnly) {
                    onChange(config.copy(daemon = it))
                }
            }
            Field("Run using", Modifier.weight(1f)) {
                EnumPicker(RunUsing.entries, config.runUsing, { it.label }, enabled = !readOnly) {
                    onChange(config.copy(runUsing = it))
                }
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
            Text(
                if (config.mutators.isEmpty()) "None — coming in milestone 5" else config.mutators.joinToString(),
                style = JewelTheme.defaultTextStyle,
            )
        }
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
) {
    TextField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, style = JewelTheme.defaultTextStyle) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
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
