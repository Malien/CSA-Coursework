package ua.edu.ukma.csa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.addProduct
import ua.edu.ukma.csa.model.groups
import ua.edu.ukma.csa.model.model
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.udp.UDPClient
import ua.edu.ukma.csa.network.udp.UDPServer
import java.net.InetAddress

@ExperimentalUnsignedTypes
class UDPClientTest {

    private val server = UDPServer(0)
    private val client = UDPClient.Decrypted(InetAddress.getLocalHost(), server.socket.localPort, 3u)
    private val biscuit = Product(name = "Biscuit", price = 17.55, count = 10)

    @BeforeEach
    fun populate() {
        model.clear()
        groups.clear()
        addProduct(biscuit)
    }

    @Test
    fun `should add group`() {
        GlobalScope.launch(Dispatchers.Default) {
            assertRight(MessageType.OK, client.addGroup("name").map { it.messageType })
            assertRight(MessageType.OK, client.assignGroup(biscuit.id, "name").map { it.messageType })
            assertTrue(groups["name"]!!.contains(biscuit))
        }
    }

    @Test
    fun `should get count`() {
        GlobalScope.launch(Dispatchers.Default) {
            val response = client.getQuantity(biscuit.id)
            assertRight(10, response.map { it.count })
            assertRight(biscuit.id, response.map { it.id })
        }
    }

}