package ua.edu.ukma.csa.network

import ua.edu.ukma.csa.network.MessageType.ERR
import ua.edu.ukma.csa.network.MessageType.OK
import java.security.Key
import javax.crypto.Cipher

val invalidRequest = Message.Decrypted(ERR, 0)

val okResponse = Message.Decrypted(OK, 0)

fun process(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR -> invalidRequest
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
