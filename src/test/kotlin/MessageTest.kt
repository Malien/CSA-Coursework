import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*

class MessageTest {

    @Test
    fun messageEncodeDecode() {
        val message = Message(1, 2, "hello".toByteArray())

        val decoded = Message.decode(message.data)

        assertEquals(message, decoded)
        assertEquals("hello", String(decoded.message))
    }

    @Test
    fun message0Size() {
        val data = ByteArray(0)
        assertThrows<PacketException.Length> { Message.decode(data) }
    }

}