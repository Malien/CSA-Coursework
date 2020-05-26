package ua.edu.ukma.csa.network

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import arrow.core.getOrHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import ua.edu.ukma.csa.kotlinx.serialization.functionalParse
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

val json = Json(JsonConfiguration.Stable)

fun <Req : Request, Res : Response> processMessage(
    requestSerializer: KSerializer<Req>,
    responseSerializer: KSerializer<Res>,
    message: Message.Decrypted,
    handler: (request: Req) -> Either<ModelException, Res>
) = json.functionalParse(requestSerializer, String(message.message))
    .flatMap(handler)
    .flatMap { it.toMessage(serializer = responseSerializer) }

/**
 * Dispatch message into according handling functions, and return response message
 * @param message message to be processed
 * @return server response message
 */
fun handleMessage(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR -> Left(RuntimeException("Cannot process request of type ${message.type}"))
    GET_COUNT -> processMessage(Request.GetQuantity.serializer(), Response.Quantity.serializer(), message) { request ->
        getQuantity(request.id).map { Response.Quantity(id = request.id, count = it) }
    }
    INCLUDE -> processMessage(Request.Include.serializer(), Response.Quantity.serializer(), message) { request ->
        addQuantityOfProduct(request.id, request.count).map { Response.Quantity(id = request.id, count = it) }
    }
    EXCLUDE -> processMessage(Request.Exclude.serializer(), Response.Quantity.serializer(), message) { request ->
        deleteQuantityOfProduct(request.id, request.count).map { Response.Quantity(id = request.id, count = it) }
    }
    ADD_GROUP -> processMessage(Request.AddGroup.serializer(), Response.Ok.serializer(), message) { request ->
        addGroup(request.name).map { Response.Ok }
    }
    ASSIGN_GROUP -> processMessage(Request.AssignGroup.serializer(), Response.Ok.serializer(), message) { request ->
        assignGroup(request.id, request.group).map { Response.Ok }
    }
    SET_PRICE -> processMessage(Request.SetPrice.serializer(), Response.Price.serializer(), message) { request ->
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
