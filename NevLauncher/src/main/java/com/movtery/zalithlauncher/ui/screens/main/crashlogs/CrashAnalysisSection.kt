/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.main.crashlogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.crashanalyzer.CrashAnalysisResult
import com.movtery.zalithlauncher.crashanalyzer.CrashFinding
import com.movtery.zalithlauncher.crashanalyzer.CrashSeverity

/**
 * Hasil analisis crash untuk ditampilkan di layar error.
 *
 * @param result null selama analisis masih berjalan
 */
@Composable
fun CrashAnalysisSection(
    result: CrashAnalysisResult?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.crash_analysis_title),
            style = MaterialTheme.typography.titleMedium
        )

        when {
            result == null -> Text(
                text = stringResource(R.string.crash_analysis_analyzing),
                style = MaterialTheme.typography.bodyMedium
            )

            !result.hasAnything -> Text(
                text = stringResource(R.string.crash_analysis_none),
                style = MaterialTheme.typography.bodyMedium
            )

            else -> {
                result.reportDescription?.let { description ->
                    Text(
                        text = stringResource(R.string.crash_analysis_description, description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.findings.forEach { finding ->
                    FindingItem(finding)
                }
                if (result.suspectedMods.isNotEmpty()) {
                    DetailList(
                        label = stringResource(R.string.crash_analysis_suspected_mods),
                        items = result.suspectedMods
                    )
                }
            }
        }
    }
}

@Composable
private fun FindingItem(finding: CrashFinding) {
    val text = finding.cause.text()
    //argumen selalu diberikan: yang tidak memakai %1$s mengabaikannya, dan "?" mencegah crash bila argumen kosong
    val fix = stringResource(text.fix, finding.args.firstOrNull() ?: "?")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(finding.cause.severity.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = when (finding.cause.severity) {
                CrashSeverity.DEFINITE -> MaterialTheme.colorScheme.error
                CrashSeverity.PROBABLE -> MaterialTheme.colorScheme.primary
                CrashSeverity.HINT -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = stringResource(text.title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.crash_analysis_how_to_fix) + ": " + fix,
            style = MaterialTheme.typography.bodyMedium
        )
        if (finding.details.isNotEmpty()) {
            DetailList(
                label = stringResource(R.string.crash_analysis_details),
                items = finding.details
            )
        }
        finding.evidence?.let { evidence ->
            SelectionContainer {
                Text(
                    text = evidence,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailList(label: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        SelectionContainer {
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
