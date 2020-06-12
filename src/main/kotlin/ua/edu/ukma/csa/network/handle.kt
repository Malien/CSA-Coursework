package ua.edu.ukma.csa.network

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import arrow.core.getOrHandle
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.serialization.fload
import ua.edu.ukma.csa.model.ModelException
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.MessageType.*
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import javax.crypto.Cipher

fun errorMessage(message: String) = Response.Error(message).toMessage().handleWithThrow()

fun errorPacket(error: Exception) = Packet(clientID = 0u, message = errorMessage(error.message!!))
fun errorPacket(error: Exception, key: Key, cipher: Cipher) =
    Packet(clientID = 0u, message = errorMessage(error.message!!).encrypted(key, cipher))

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
fun ModelSource.handleMessage(message: Message.Decrypted): Message.Decrypted = when (message.type) {
    OK, ERR, PACKET_BEHIND -> Left(RuntimeException("Cannot process request of type ${message.type}"))
    INCLUDE -> processMessage(message) { request: Request.Include ->
        addQuantityOfProduct(request.id, request.count).map { Response.Quantity(id = request.id, count = request.count) }
    }
    EXCLUDE -> processMessage(message) { request: Request.Exclude ->
        deleteQuantityOfProduct(request.id, request.count).map { Response.Quantity(id = request.id, count = request.count) }
    }
    ADD_GROUP -> processMessage(message) { request: Request.AddGroup ->
        addGroup(request.name).map { Response.Group(it) }
    }
    ASSIGN_GROUP -> processMessage(message) { request: Request.AssignGroup ->
        assignGroup(request.product, request.group).map { Response.Ok }
    }
    SET_PRICE -> processMessage(message) { request: Request.SetPrice ->
        setPrice(request.id, request.price).map { Response.Price(id = request.id, count = request.price) }
    }
    ADD_PRODUCT -> processMessage(message) { request: Request.AddProduct ->
        addProduct(request.name, request.count, request.price, request.groups).map { Response.Product(it) }
    }
    GET_PRODUCT -> processMessage(message) { request: Request.GetProduct ->
        getProduct(request.id).map { Response.Product(it) }
    }
    GET_PRODUCT_LIST -> processMessage(message) { request: Request.GetProductList ->
        getProducts(request.criteria, request.ordering, request.offset, request.amount).map { Response.ProductList(it) }
    }
    REMOVE -> processMessage(message) { request: Request.RemoveProduct ->
        removeProduct(request.id).map { Response.Ok }
    }
}.getOrHandle { error -> errorMessage(error.message ?: "") }

/**
 * Handle incoming packet, and return response packet
 * @param packet incoming packet
 * @return unencrypted response packet
 */
fun ModelSource.handlePacket(packet: Packet<Message.Decrypted>): Packet<Message.Decrypted> = Packet(
    clientID = 0u,
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
fun ModelSource.handlePacket(packet: Packet<Message.Encrypted>, key: Key, cipher: Cipher): Packet<Message.Encrypted> = Packet(
    clientID = 0u,
    message = handleMessage(packet.message.decrypted(key, cipher)).encrypted(key, cipher),
    packetID = packet.packetID
)

/**
 * Handles unencrypted requests from a binary stream and writes unencrypted responses to the binary output stream
 * @param inputStream stream from which request will come
 * @param outputStream stream to which server responses will be written to
 */
fun ModelSource.handleStream(inputStream: InputStream, outputStream: OutputStream) =
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
fun ModelSource.handleStream(inputStream: InputStream, outputStream: OutputStream, key: Key, cipher: Cipher) =
    Packet.sequenceFrom<Message.Encrypted>(inputStream)
        .map {
            it.map { packet -> handlePacket(packet, key, cipher) }
                .getOrHandle { error -> errorPacket(error, key, cipher) }
        }.forEach { outputStream.write(it.data) }
