package ua.edu.ukma.csa.network

import ua.edu.ukma.csa.network.MessageType.ERR
import ua.edu.ukma.csa.network.MessageType.OK

val invalidRequest = Message.Decrypted(ERR, 0)

val okResponse = Message.Decrypted(OK, 0)

fun process(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR -> invalidRequest
    else -> okResponse
}
