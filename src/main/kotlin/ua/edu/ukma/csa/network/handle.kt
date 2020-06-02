package ua.edu.ukma.csa.network

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import arrow.core.getOrHandle
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import ua.edu.ukma.csa.kotlinx.serialization.fload
import ua.edu.ukma.csa.model.*
import ua.edu.ukma.csa.network.MessageType.*
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import javax.crypto.Cipher

fun errorMessage(message: String) = errorMessage(message.toByteArray())
fun errorMessage(message: ByteArray = ByteArray(0)) = Message.Decrypted(ERR, 0, message)

fun errorPacket(error: Exception) = Packet(clientID = 0, message = errorMessage(error.message!!))
fun errorPacket(error: Exception, key: Key, cipher: Cipher) =
    Packet(clientID = 0, message = errorMessage(error.message!!).encrypted(key, cipher))

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified Req : Request, reified Res : Response> processMessage(
    message: Message.Decrypted,
    noinline handler: (request: Req) -> Either<ModelException, Res>
) = ProtoBuf.fload(Req::class.serializer(), message.message)
    .flatMap(handler)
    .flatMap { it.toMessage() }

/**
 * Dispatch message into according handling functions, and return response message
 * @param message message to be processed
 * @return server response message
 */
fun handleMessage(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR, PACKET_BEHIND -> Left(RuntimeException("Cannot process request of type ${message.type}"))
    GET_COUNT -> processMessage(message) { request: Request.GetQuantity ->
        getQuantity(request.id).map { Response.Quantity(id = request.id, count = it) }
    }
    INCLUDE -> processMessage(message) { request: Request.Include ->
        addQuantityOfProduct(request.id, request.count).map { Response.Quantity(id = request.id, count = it) }
    }
    EXCLUDE -> processMessage(message) { request: Request.Exclude ->
        deleteQuantityOfProduct(request.id, request.count).map { Response.Quantity(id = request.id, count = it) }
    }
    ADD_GROUP -> processMessage(message) { request: Request.AddGroup ->
        addGroup(request.name).map { Response.Ok }
    }
    ASSIGN_GROUP -> processMessage(message) { request: Request.AssignGroup ->
        assignGroup(request.id, request.group).map { Response.Ok }
    }
    SET_PRICE -> processMessage(message) { request: Request.SetPrice ->
        setPrice(request.id, request.price).map { Response.Price(id = request.id, count = it) }
    }
}.getOrHandle { error -> errorMessage(error.message ?: "") }

/**
 * Handle incoming packet, and return response packet
 * @param packet incoming packet
 * @return unencrypted response packet
 */
fun handlePacket(packet: Packet<Message.Decrypted>): Packet<Message.Decrypted> = Packet(
    clientID = 0,
    message = handleMessage(packet.message),
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
    message = handleMessage(packet.message.decrypted(key, cipher)).encrypted(key, cipher),
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
