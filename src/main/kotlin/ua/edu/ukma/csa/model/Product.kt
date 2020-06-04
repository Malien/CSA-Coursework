package ua.edu.ukma.csa.model

import kotlinx.serialization.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * GOD I WISH THIS COULD BE AN INLINE CLASS
 */
@Serializable
data class ProductID(val id: Int) {
    @Serializer(forClass = ProductID::class)
    companion object : KSerializer<ProductID> {
        val UNSET = ProductID(0)

        /**
         * Temporary solution for assigning IDs. This should be handled by the database
         */
        fun assign() = ProductID(assignedIDs.incrementAndGet())

        private var assignedIDs = AtomicInteger(0)

        override val descriptor = PrimitiveDescriptor("ProductID", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) = ProductID(decoder.decodeInt())
        override fun serialize(encoder: Encoder, value: ProductID) { encoder.encodeInt(value.id) }
    }
}

data class Product(
    val id: ProductID = ProductID.UNSET,
    val name: String,
    var count: Int = 0,
    var price: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Product

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

