package org.draken.serialization.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Suppress("unused")
class SerializationPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.gradle.projectsEvaluated {
			project.rootProject.allprojects { proj ->
				proj.tasks.matching { it.name.contains(Regex("DuplicateClasses|minify|mergeReleaseJavaResource")) }.configureEach { task ->
					val backups = mutableMapOf<String, ByteArray?>()
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

						inputFiles.toList().filter { it.exists() }.distinct().forEach { file ->
							if (file.isDirectory) {
								if (!isUsagiSerializationLibrary(file)) {
									val targetClassFile = File(file, TARGET_CLASS_NAME)
									if (targetClassFile.exists()) {
										backups[targetClassFile.absolutePath] = targetClassFile.readBytes()
										targetClassFile.setWritable(true)
										targetClassFile.delete()
									}
								}
								file.walk().filter { it.isFile && it.name.endsWith(".jar") }.forEach { jarFile ->
									val backup = stripTargetClass(jarFile)
									if (backup != null) {
										backups[jarFile.absolutePath] = backup
									}
								}
							} else if (file.name.endsWith(".jar")) {
								val backup = stripTargetClass(file)
								if (backup != null) {
									backups[file.absolutePath] = backup
								}
							}
						}
					}
					task.doLast {
						backups.forEach { (path, bytes) ->
							val f = File(path)
							if (bytes != null) {
								f.parentFile?.mkdirs()
								f.setWritable(true)
								f.writeBytes(bytes)
							} else {
								if (f.exists()) {
									f.setWritable(true)
									f.delete()
								}
							}
						}
					}
				}
			}
		}
	}

	private fun isUsagiSerializationLibrary(file: File): Boolean {
		val path = file.absolutePath.replace('\\', '/')
		return file.name.startsWith("library") ||
				path.contains("com.github.UsagiApp.serialization") ||
				path.contains("serialization/library") ||
				path.contains("UsagiApp.serialization")
	}

	private fun stripTargetClass(file: File): ByteArray? {
		if (isUsagiSerializationLibrary(file)) return null

		val entryExists = try {
			ZipFile(file).use { zf ->
				zf.getEntry(TARGET_CLASS_NAME) != null
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
						if (entry.name != TARGET_CLASS_NAME) {
							out.putNextEntry(ZipEntry(entry.name))
							src.getInputStream(entry).use { input ->
								input.copyTo(out)
							}
							out.closeEntry()
						}
					}
				}
			}

			file.setWritable(true)
			file.writeBytes(temp.readBytes())
			temp.delete()
			return bytes
		} catch (_: Exception) {
			if (temp.exists()) {
				temp.delete()
			}
			return null
		}
	}

	companion object {
		private const val TARGET_CLASS_NAME = "kotlinx/serialization/internal/PluginGeneratedSerialDescriptor.class"
	}
}
