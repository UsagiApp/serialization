package org.draken.serialization.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SerializationPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.gradle.projectsEvaluated {
			project.rootProject.allprojects { proj ->
				proj.tasks.matching { it.name.contains(Regex("DuplicateClasses|minify|mergeReleaseJavaResource")) }.configureEach { task ->
					val backups = mutableMapOf<String, ByteArray>()
					task.doFirst {
						val inputFiles = mutableListOf<File>()
						try {
							inputFiles.addAll(task.inputs.files.files)
						} catch (_: Exception) {}

						try {
							inputFiles.addAll(
								proj.configurations
									.filter { it.isCanBeResolved }
									.flatMap { it.incoming.files.files }
							)
						} catch (_: Exception) {}

						inputFiles.filterNotNull().filter { it.exists() }.distinct().forEach { file ->
							val backup = stripTargetClass(file)
							if (backup != null) {
								backups[file.absolutePath] = backup
							}
						}
					}
					task.doLast {
						backups.forEach { (path, bytes) ->
							File(path).writeBytes(bytes)
						}
					}
				}
			}
		}
	}

	private fun stripTargetClass(file: File): ByteArray? {
		if (!file.name.contains("kotlinx-serialization-core") || !file.name.endsWith(".jar")) return null

		val entryExists = try {
			ZipFile(file).use { zf ->
				zf.getEntry("kotlinx/serialization/internal/PluginGeneratedSerialDescriptor.class") != null
			}
		} catch (_: Exception) {
			false
		}

		if (!entryExists) return null

		val bytes = file.readBytes()
		val temp = File(file.parentFile, "${file.name}.tmp")

		try {
			ZipFile(file).use { src ->
				ZipOutputStream(FileOutputStream(temp)).use { out ->
					val entries = src.entries()
					while (entries.hasMoreElements()) {
						val entry = entries.nextElement()
						if (entry.name != "kotlinx/serialization/internal/PluginGeneratedSerialDescriptor.class") {
							out.putNextEntry(ZipEntry(entry.name))
							src.getInputStream(entry).use { input ->
								input.copyTo(out)
							}
							out.closeEntry()
						}
					}
				}
			}

			if (file.delete()) {
				temp.renameTo(file)
			}
		} catch (_: Exception) {
			if (temp.exists()) {
				temp.delete()
			}
			return null
		}

		return bytes
	}
}
