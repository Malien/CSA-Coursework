package ua.edu.ukma.csa.model

import kotlinx.serialization.*
import java.util.concurrent.atomic.AtomicInteger

/** GOD I WISH THIS COULD BE AN INLINE CLASS */
@Serializable
data class GroupID(val id: Int) {
    override fun toString() = "GroupID($id)"

    @Serializer(forClass = ProductID::class)
    companion object : KSerializer<ProductID> {
        val UNSET = GroupID(0)

        private var assignedIDs = AtomicInteger(0)

        /** Temporary solution for assigning IDs. This should be handled by the database */
        fun assign() = GroupID(assignedIDs.incrementAndGet())

        override val descriptor = PrimitiveDescriptor("ProductID", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) = ProductID(decoder.decodeInt())
        override fun serialize(encoder: Encoder, value: ProductID) { encoder.encodeInt(value.id) }
    }
}

@Serializable
data class Group(val id: GroupID = GroupID.UNSET, val name: String)
