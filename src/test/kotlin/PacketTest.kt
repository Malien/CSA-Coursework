import Packet.Companion.calculateHeaderCRC
import Packet.Companion.calculateMessageCRC
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*

internal class PacketTest {

    private lateinit var message: Message

    @BeforeEach
    fun setup() {
        message = Message(type = 1, userID = 2, message = "hello".toByteArray())
    }

    @Test
    fun decode() {
        val packet = Packet(clientID = 4, message = message, packetID = 5)
        val decoded = Packet.decode(packet.data)
        assertEquals(packet, decoded)
    }

    @Test
    fun decode0Size() {
        val data = ByteArray(0)
        assertThrows<PacketException.Length> { Packet.decode(data) }
    }

    @Test
    fun decodeIncomplete() {
        val packet = Packet(clientID = 4, message = message, packetID = 5)
        val decoded = packet.data.copyOfRange(0, packet.size - 2)
        assertThrows<PacketException.Length> { Packet.decode(decoded) }
    }

    @Test
    fun decodeWrongMessageSize() {
        val packet = Packet(
            clientID = 1,
            packetID = 2,
            messageLength = message.size - 1,
            headerCRC = calculateHeaderCRC(0x14, 1, 2, messageLength = message.size),
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertThrows<PacketException> { Packet.decode(packet.data) }
    }

    @Test
    fun decodeWrongMagic() {
        val packet = Packet(
            magic = 0x14,
            clientID = 1,
            packetID = 2,
            messageLength = message.size,
            headerCRC = calculateHeaderCRC(0x14, 1, 2, messageLength = message.size),
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertThrows<PacketException.Magic> { Packet.decode(packet.data) }
    }

    @Test
    fun decodeWrongHeaderCRC() {
        val packet = Packet(
            clientID = 1,
            packetID = 2,
            messageLength = message.size,
            headerCRC = 0,
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertThrows<PacketException.CRCCheck> { Packet.decode(packet.data) }
    }


    @Test
    fun decodeWrongMessageCRC() {
        val packet = Packet(
            clientID = 1,
            packetID = 2,
            messageLength = message.size,
            headerCRC = calculateHeaderCRC(0x14, 1, 2, messageLength = message.size),
            message = message,
            messageCRC = 0
        )
        assertThrows<PacketException.CRCCheck> { Packet.decode(packet.data) }
    }

}