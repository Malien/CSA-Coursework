package ua.edu.ukma.csa.network

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import ua.edu.ukma.csa.Configuration.protobuf
import ua.edu.ukma.csa.kotlinx.serialization.fdump
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.model.UserID

@Serializable
sealed class Response() {
    open val type: MessageType get() = MessageType.OK

    @Serializable
    data class Error(val message: String) : Response() {
        override val type get() = MessageType.ERR
    }

    @Serializable
    object Ok : Response()

    @Serializable
    object PacketBehind: Response() {
        override val type get() = MessageType.PACKET_BEHIND
    }

    @Serializable
    data class Product(val product: ua.edu.ukma.csa.model.Product) : Response()

    @Serializable
    data class Group(val group: ua.edu.ukma.csa.model.Group) : Response()

    @Serializable
    data class ProductList(val products: List<ua.edu.ukma.csa.model.Product>) : Response()

    @Serializable
    data class Price(val id: ProductID, val count: Double) : Response()

}

inline fun <reified T : Response> T.toMessage(userID: UserID = UserID.UNSET) =
    serialize().map { Message.Decrypted(type, userID, message = it) }

fun <T : Response> T.toMessage(userID: UserID = UserID.UNSET, serializer: KSerializer<T>) =
    serialize(serializer).map { Message.Decrypted(type, userID, message = it) }

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Response> T.serialize() =
    protobuf.fdump(T::class.serializer(), this)

fun <T : Response> T.serialize(serializer: KSerializer<T>) =
    protobuf.fdump(serializer, this)

