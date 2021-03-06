/*
 * Copyright 2017-2019 Aljoscha Grebe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.almightyalpaca.jetbrains.plugins.discord.plugin.data

import com.almightyalpaca.jetbrains.plugins.discord.plugin.utils.application
import com.almightyalpaca.jetbrains.plugins.discord.plugin.utils.find
import com.almightyalpaca.jetbrains.plugins.discord.shared.matcher.FieldProvider
import com.almightyalpaca.jetbrains.plugins.discord.shared.matcher.Matcher
import com.almightyalpaca.jetbrains.plugins.discord.shared.utils.toSet
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.io.FilenameUtils
import java.time.OffsetDateTime

class FileData(
    private val platform: VirtualFile,
    val project: Project,
    val openedAt: OffsetDateTime,
    override val accessedAt: OffsetDateTime
) : FieldProvider, AccessedAt {

    val name: String = platform.name
    private val path: String = platform.path
    val isWriteable = platform.isWritable

    /** Path relative to the project directory */
    private val relativePath: String by lazy { FilenameUtils.separatorsToUnix(path) }

    private val baseNames: Collection<String> by lazy {
        name.find('.')
            .mapToObj { i -> name.substring(0, i) }
            .toSet()
    }

    private val extensions: Collection<String> by lazy {
        name.find('.')
            .mapToObj { i -> name.substring(i) }
            .toSet()
    }

    val uniqueName: String by lazy {
        application.runReadAction(Computable {
            if (!project.isDisposed) {
                EditorTabPresentationUtil.getUniqueEditorTabTitle(project, platform, null)
            } else {
                name
            }
        })
    }

    override fun getField(target: Matcher.Target) = when (target) {
        Matcher.Target.EXTENSION -> extensions
        Matcher.Target.NAME -> listOf(name)
        Matcher.Target.BASENAME -> baseNames
        Matcher.Target.PATH -> listOf(relativePath)
    }

    fun builder() = FileDataBuilder(project, openedAt, accessedAt)
}

class FileDataBuilder(
    val project: Project,
    openedAt: OffsetDateTime = OffsetDateTime.now(),
    accessedAt: OffsetDateTime = openedAt
) {
    var openedAt = openedAt
        set(value) {
            field = value

            if (accessedAt.isBefore(field)) {
                accessedAt = field
            }
        }

    var accessedAt = accessedAt
        set(value) {
            field = value

            if (field.isBefore(openedAt)) {
                field = openedAt
            }
        }

    fun build(file: VirtualFile) = FileData(file, project, openedAt, accessedAt)
}
