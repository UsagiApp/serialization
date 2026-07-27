@file:Suppress(
	"DEPRECATION", "OPT_IN_USAGE_ERROR", "OPT_IN_OVERRIDE_ERROR",
	"OPT_IN_USAGE", "SUBCLASS_OPT_IN_REQUIRED", "unused", "REDUNDANT_VISIBILITY_MODIFIER",
)
@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)

package kotlinx.serialization.internal

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class, SealedSerializationApi::class)
@SubclassOptInRequired(InternalSerializationApi::class)
@InternalSerializationApi
public open class PluginGeneratedSerialDescriptor(
	override val serialName: String,
	private val generatedSerializer: GeneratedSerializer<*>? = null,
	final override val elementsCount: Int
) : SerialDescriptor {

	private companion object {
		private val LOCAL_EMPTY_SERIALIZER_ARRAY: Array<KSerializer<*>> = emptyArray()
		private val LOCAL_EMPTY_DESCRIPTOR_ARRAY: Array<SerialDescriptor> = emptyArray()
	}

	override val kind: SerialKind get() = StructureKind.CLASS
	override var annotations: List<Annotation> = emptyList()

	private val names: Array<String> = Array(elementsCount) { "[uninitialized]" }
	private val propertiesAnnotations: Array<List<Annotation>?> = arrayOfNulls(elementsCount)
	private val elementsOptionality: BooleanArray = BooleanArray(elementsCount)

	private var indices: Map<String, Int> = emptyMap()

	private val childSerializers: Array<KSerializer<*>> by lazy {
		generatedSerializer?.childSerializers() ?: LOCAL_EMPTY_SERIALIZER_ARRAY
	}

	private val typeParameterDescriptors: Array<SerialDescriptor> by lazy {
		val serializers = try {
			generatedSerializer?.typeParametersSerializers()
		} catch (_: Throwable) {
			LOCAL_EMPTY_SERIALIZER_ARRAY
		}
		serializers?.map { it.descriptor }?.toTypedArray() ?: LOCAL_EMPTY_DESCRIPTOR_ARRAY
	}

	private var _hashCode: Int = -1

	public fun addElement(name: String, isOptional: Boolean = false) {
		val slot = names.indexOf("[uninitialized]")
		require(slot != -1) { "Cannot add element $name, maximum elements count ($elementsCount) reached" }
		names[slot] = name
		elementsOptionality[slot] = isOptional
		propertiesAnnotations[slot] = null
		if (slot == elementsCount - 1) {
			indices = buildIndices()
		}
	}

	public fun pushAnnotation(annotation: Annotation) {
		val lastSlot = names.lastIndexOf("[uninitialized]").let { if (it == -1) elementsCount - 1 else it - 1 }
		val currentList = propertiesAnnotations[lastSlot] ?: ArrayList<Annotation>().also { propertiesAnnotations[lastSlot] = it }
		@Suppress("UNCHECKED_CAST")
		(currentList as MutableList<Annotation>).add(annotation)
	}

	public fun pushClassAnnotation(annotation: Annotation) {
		if (annotations.isEmpty()) {
			annotations = ArrayList()
		}
		@Suppress("UNCHECKED_CAST")
		(annotations as MutableList<Annotation>).add(annotation)
	}

	override fun getElementName(index: Int): String = names[index]
	override fun getElementIndex(name: String): Int = indices[name] ?: CompositeDecoder.UNKNOWN_NAME
	override fun getElementAnnotations(index: Int): List<Annotation> = propertiesAnnotations[index] ?: emptyList()
	override fun getElementDescriptor(index: Int): SerialDescriptor = childSerializers[index].descriptor
	override fun isElementOptional(index: Int): Boolean = elementsOptionality[index]

	public open val serialNames: Set<String> get() = indices.keys

	private fun buildIndices(): Map<String, Int> {
		val map = HashMap<String, Int>(names.size)
		for (i in names.indices) {
			map[names[i]] = i
		}
		return map
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is PluginGeneratedSerialDescriptor) return false
		if (serialName != other.serialName) return false
		if (!typeParameterDescriptors.contentEquals(other.typeParameterDescriptors)) return false
		if (elementsCount != other.elementsCount) return false
		for (i in 0 until elementsCount) {
			if (getElementDescriptor(i).serialName != other.getElementDescriptor(i).serialName) return false
			if (getElementDescriptor(i).kind != other.getElementDescriptor(i).kind) return false
		}
		return true
	}

	override fun hashCode(): Int {
		if (_hashCode != -1) return _hashCode
		var result = serialName.hashCode()
		result = 31 * result + typeParameterDescriptors.contentHashCode()
		val elementDescriptorsHashCode = (0 until elementsCount).fold(1) { hash, i ->
			31 * hash + getElementDescriptor(i).serialName.hashCode()
		}
		result = 31 * result + elementDescriptorsHashCode
		_hashCode = result
		return result
	}

	override fun toString(): String {
		return (0 until elementsCount).joinToString(", ", "$serialName(", ")") { i ->
			"${getElementName(i)}: ${getElementDescriptor(i).serialName}"
		}
	}
}
