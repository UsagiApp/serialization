package org.draken.serialization.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Suppress("unused")
class SerializationPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		val stripped = Attribute.of("serialization-stripped", Boolean::class.javaObjectType)

		project.allprojects { proj ->
			proj.dependencies.attributesSchema {
				it.attribute(stripped)
			}

			proj.dependencies.artifactTypes.configureEach { artifactType ->
				if (artifactType.name == org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE) {
					artifactType.attributes.attribute(stripped, false)
				}
			}

			proj.dependencies.registerTransform(StripDuplicateClassTransform::class.java) {
				it.from.attribute(stripped, false)
				it.to.attribute(stripped, true)
			}

			proj.afterEvaluate {
				proj.configurations.configureEach { config ->
					if (config.isCanBeResolved) {
						config.attributes.attribute(stripped, true)
					}
				}
			}
		}
	}
}

abstract class StripDuplicateClassTransform : TransformAction<TransformParameters.None> {
	@get:InputArtifact
	abstract val inputArtifact: Provider<FileSystemLocation>

	override fun transform(outputs: TransformOutputs) {
		val input = inputArtifact.get().asFile
		if (!input.name.endsWith(".jar") || !input.name.contains("kotlinx-serialization-core")) {
			outputs.file(input)
			return
		}

		val targetClassName = "kotlinx/serialization/internal/PluginGeneratedSerialDescriptor.class"

		val hasTarget = try {
			ZipFile(input).use { zf -> zf.getEntry(targetClassName) != null }
		} catch (_: Exception) {
			false
		}

		if (!hasTarget) {
			outputs.file(input)
			return
		}

		val outputFile = outputs.file("${input.nameWithoutExtension}-stripped.jar")
		ZipFile(input).use { src ->
			ZipOutputStream(outputFile.outputStream()).use { out ->
				val entries = src.entries()
				while (entries.hasMoreElements()) {
					val entry = entries.nextElement()
					if (entry.name != targetClassName) {
						out.putNextEntry(ZipEntry(entry.name))
						src.getInputStream(entry).use { it.copyTo(out) }
						out.closeEntry()
					}
				}
			}
		}
	}
}
