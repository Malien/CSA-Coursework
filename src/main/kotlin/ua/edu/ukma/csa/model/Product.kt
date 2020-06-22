package ua.edu.ukma.csa.model

import kotlinx.serialization.*

/** GOD I WISH THIS COULD BE AN INLINE CLASS */
@Serializable
data class ProductID(val id: Int) {
    override fun toString() = "ProductID($id)"

    @Serializer(forClass = ProductID::class)
    companion object : KSerializer<ProductID> {
        val UNSET = ProductID(0)

        override val descriptor = PrimitiveDescriptor("ProductID", PrimitiveKind.INT)
        override fun deserialize(decoder: Decoder) = ProductID(decoder.decodeInt())
        override fun serialize(encoder: Encoder, value: ProductID) { encoder.encodeInt(value.id) }
    }
}

@Serializable
data class Product(
    @Required
    val id: ProductID = ProductID.UNSET,
    val name: String,
    val count: Int = 0,
    val price: Double,
    @Required
    val groups: Set<GroupID> = emptySet()
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

