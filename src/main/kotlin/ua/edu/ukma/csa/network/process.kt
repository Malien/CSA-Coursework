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

fun process(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR -> errorMessage("Cannot process request of type ${message.type}")
    else -> okResponse
}

fun handlePacket(packet: Packet<Message.Decrypted>): Packet<Message.Decrypted> = Packet(
    clientID = 0,
    message = process(packet.message),
    packetID = packet.packetID
)

fun handlePacket(packet: Packet<Message.Encrypted>, key: Key, cipher: Cipher): Packet<Message.Encrypted> = Packet(
    clientID = 0,
    message = process(packet.message.decrypted(key, cipher)).encrypted(key, cipher),
    packetID = packet.packetID
)

fun handleStream(inputStream: InputStream, outputStream: OutputStream) =
    Packet.sequenceFrom<Message.Decrypted>(inputStream)
        .map { it.map(::handlePacket).getOrHandle(::errorPacket) }
        .forEach { outputStream.write(it.data) }

fun handleEncryptedStream(inputStream: InputStream, outputStream: OutputStream, key: Key, cipher: Cipher) =
    Packet.sequenceFrom<Message.Encrypted>(inputStream)
        .map {
            it.map { packet -> handlePacket(packet, key, cipher) }
                .getOrHandle { error -> errorPacket(error, key, cipher) }
        }.forEach { outputStream.write(it.data) }
