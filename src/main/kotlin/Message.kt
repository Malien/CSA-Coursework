import java.nio.ByteBuffer

data class Message(
    val type: Int,
    val userID: Int,
    val message: ByteArray
) {
    val size get() = 8 + message.size

    val data: ByteArray by lazy {
        ByteBuffer.allocate(size)
            .putInt(type)
            .putInt(userID)
            .put(message)
            .array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (type != other.type) return false
        if (userID != other.userID) return false
        if (!message.contentEquals(other.message)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + userID
        result = 31 * result + message.contentHashCode()
        return result
    }

    companion object {
        fun decode(bytes: ByteArray) = decode(bytes, 0, bytes.size)
        fun decode(bytes: ByteArray, offset: Int, length: Int): Message {
            if (length < 8) throw PacketException.Length(8, length)

            val buffer = ByteBuffer.wrap(bytes, offset, length)
            val type = buffer.int
            val userID = buffer.int
            val message = ByteArray(length - 8)
            buffer.get(message)

            return Message(type, userID, message)
        }
    }
}
