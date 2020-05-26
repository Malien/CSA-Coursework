package ua.edu.ukma.csa.network

import arrow.core.getOrHandle
import ua.edu.ukma.csa.network.MessageType.ERR
import ua.edu.ukma.csa.network.MessageType.OK
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import javax.crypto.Cipher

val okResponse = Message.Decrypted(OK, 0)

fun errorMessage(message: String) = errorMessage(message.toByteArray())
fun errorMessage(message: ByteArray = ByteArray(0)) = Message.Decrypted(ERR, 0, message)

fun errorPacket(error: PacketException) = Packet(clientID = 0, message = errorMessage(error.message!!))
fun errorPacket(error: PacketException, key: Key, cipher: Cipher) =
    Packet(clientID = 0, message = errorMessage(error.message!!).encrypted(key, cipher))

/**
 * Dispatch message into according handling functions, and return response message
 * @param message message to be processed
 * @return server response message
 */
fun process(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR -> errorMessage("Cannot process request of type ${message.type}")
    else -> okResponse
}

/**
 * Handle incoming packet, and return response packet
 * @param packet incoming packet
 * @return unencrypted response packet
 */
fun handlePacket(packet: Packet<Message.Decrypted>): Packet<Message.Decrypted> = Packet(
    clientID = 0,
    message = process(packet.message),
    packetID = packet.packetID
)

/**
 * Handle incoming encrypted packet, and return response packet
 * @param packet incoming packet
 * @param key key which will be used for encryption and decryption. TODO: support for asymmetric encryption
 * @param cipher cipher which will be used for encryption and decryption
 * @return encrypted response packet
 */
fun handlePacket(packet: Packet<Message.Encrypted>, key: Key, cipher: Cipher): Packet<Message.Encrypted> = Packet(
    clientID = 0,
    message = process(packet.message.decrypted(key, cipher)).encrypted(key, cipher),
    packetID = packet.packetID
)

/**
 * Handles unencrypted requests from a binary stream and writes unencrypted responses to the binary output stream
 * @param inputStream stream from which request will come
 * @param outputStream stream to which server responses will be written to
 */
fun handleStream(inputStream: InputStream, outputStream: OutputStream) =
    Packet.sequenceFrom<Message.Decrypted>(inputStream)
        .map { it.map(::handlePacket).getOrHandle(::errorPacket) }
        .forEach { outputStream.write(it.data) }

/**
 * Handles encrypted requests from a binary stream and writes encrypted responses to the binary output stream
 * @param inputStream stream from which request will come
 * @param outputStream stream to which server responses will be written to
 * @param key key which will be used for encryption and decryption. TODO: support for asymmetric encryption
 * @param cipher cipher which will be used for encryption and decryption
 */
fun handleStream(inputStream: InputStream, outputStream: OutputStream, key: Key, cipher: Cipher) =
    Packet.sequenceFrom<Message.Encrypted>(inputStream)
        .map {
            it.map { packet -> handlePacket(packet, key, cipher) }
                .getOrHandle { error -> errorPacket(error, key, cipher) }
        }.forEach { outputStream.write(it.data) }
