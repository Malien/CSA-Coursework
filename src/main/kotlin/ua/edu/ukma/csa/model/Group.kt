package ua.edu.ukma.csa.model

import kotlinx.serialization.*

/** GOD I WISH THIS COULD BE AN INLINE CLASS */
@Serializable
data class GroupID(val id: Int) {
    override fun toString() = "GroupID($id)"

    @Serializer(forClass = GroupID::class)
    companion object : KSerializer<GroupID> {
        val UNSET = GroupID(0)

        override val descriptor = PrimitiveDescriptor("GroupID", PrimitiveKind.INT)
        override fun deserialize(decoder: Decoder) = GroupID(decoder.decodeInt())
        override fun serialize(encoder: Encoder, value: GroupID) { encoder.encodeInt(value.id) }
    }
}

@Serializable
data class Group(val id: GroupID = GroupID.UNSET, val name: String)
