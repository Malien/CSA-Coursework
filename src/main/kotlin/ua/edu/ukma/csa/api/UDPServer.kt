package ua.edu.ukma.csa.api

import arrow.core.Either
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.udp.UDPServer
import ua.edu.ukma.csa.network.udp.send
import java.security.Key
import javax.crypto.Cipher
import kotlin.concurrent.thread

/**
 * Starts serving UDP requests. Will block the thread. On every data chunk received this function will form a
 * corresponding Packet with **decrypted** message within it (aka. [Packet<Message.Decrypted>][Packet]). Every
 * successfully formed packet will be processed by handlePacket function. Every invalid packet will send a
 * [Response.Error] to the sender.
 * @return newly created thread which handles the processing
 */
fun UDPServer.serve(model: ModelSource) = serve { (data, address, packetCount) ->
    thread(name = "UDP-Processing-Thread") {
        socket.send(
            when (val request = Packet.decode<Message.Decrypted>(data)) {
                is Either.Right -> {
                    if (packetCount >= request.b.packetID) {
                        val response = Response.PacketBehind.toMessage().handleWithThrow()
                        Packet(clientID = 0u, message = response, packetID = request.b.packetID)
                    } else model.handlePacket(request.b)
                }
                is Either.Left -> {
                    val response = Response.Error(request.a.message ?: "").toMessage().handleWithThrow()
                    Packet(clientID = 0u, message = response, packetID = request.a.packetID ?: 0UL)
                }
            }, address
        )
    }
}

/**
 * Starts serving UDP requests. Will block the thread. On every data chunk received this function will form a
 * corresponding Packet with **encrypted** message within it (aka. [Packet<Message.Encrypted>][Packet]). Every
 * successfully formed packet will be processed by [handlePacket] function. Every invalid packet will send a
 * [Response.Error] to the sender. **NOTE that this function will consume cipher** to its own use. Most probably this
 * will run in a separate thread and every unintended operation outside of this function will lead to
 * undefined behaviour.
 * @param key key which will be used to decrypt message
 * @param cipherFactory function that produces ciphers to be used by different threads
 * @return newly created thread which handles the processing
 */
fun UDPServer.serve(model: ModelSource, key: Key, cipherFactory: () -> Cipher) = serve { (data, address, packetCount) ->
    thread(name = "UDP-Processing-Thread") {
        val cipher = cipherFactory()
        socket.send(
            when (val request = Packet.decode<Message.Encrypted>(data)) {
                is Either.Right -> {
                    if (packetCount >= request.b.packetID) {
                        val response = Response.PacketBehind.toMessage().handleWithThrow().encrypted(key, cipher)
                        Packet(clientID = 0u, message = response, packetID = request.b.packetID)
                    } else model.handlePacket(request.b, key, cipher)
                }
                is Either.Left -> {
                    val response =
                        Response.Error(request.a.message ?: "").toMessage().handleWithThrow().encrypted(key, cipher)
                    Packet(clientID = 0u, message = response, packetID = request.a.packetID ?: 0UL)
                }
            }, address
        )
    }
}
