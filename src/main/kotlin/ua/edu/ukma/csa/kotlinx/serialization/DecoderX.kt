package ua.edu.ukma.csa.kotlinx.serialization

import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerializationException

inline fun Decoder.decodeStructure(
    descriptor: SerialDescriptor,
    decoder: CompositeDecoder.(index: Int) -> Unit
) {
    val struct = beginStructure(descriptor)
    loop@ while (true) {
        when (val idx = struct.decodeElementIndex(descriptor)) {
            CompositeDecoder.READ_DONE -> return
            CompositeDecoder.UNKNOWN_NAME -> throw SerializationException("Unknown name encountered while deserializing")
            else -> struct.decoder(idx)
        }
    }
}