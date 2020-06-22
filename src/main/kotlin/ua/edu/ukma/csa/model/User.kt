package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import kotlinx.serialization.*
import java.util.concurrent.atomic.AtomicInteger

/** GOD I WISH THIS COULD BE AN INLINE CLASS */
@Serializable
data class UserID(val id: Int) {
    override fun toString() = "UserID($id)"

    @Serializer(forClass = UserID::class)
    companion object : KSerializer<UserID> {
        val UNSET = UserID(0)

        private var assignedIDs = AtomicInteger(0)

        /** Temporary solution for assigning IDs. This should be handled by the database */
        fun assign() = UserID(assignedIDs.incrementAndGet())

        override val descriptor = PrimitiveDescriptor("UserID", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) = UserID(decoder.decodeInt())
        override fun serialize(encoder: Encoder, value: UserID) {
            encoder.encodeInt(value.id)
        }
    }

}

data class User(val id: UserID, val login: String, val hash: String)

val lowercaseRegex = Regex("[a-z]")
val uppercaseRegex = Regex("[A-Z]")
val digitRegex = Regex("""\d""")

fun checkPassword(password: String): Either<ModelException.Password, String> {
    if (password.length < 6) return Left(ModelException.Password.Length(6, password.length))
    if (!password.contains(lowercaseRegex)) return Left(ModelException.Password.NoLowercase())
    if (!password.contains(uppercaseRegex)) return Left(ModelException.Password.NoUppercase())
    if (!password.contains(digitRegex)) return Left(ModelException.Password.NoDigits())
    return Right(password)
}

val loginRegex = Regex("""[\w_-]+""")

fun checkLogin(login: String): Either<ModelException.IllegalLoginCharacters, String> {
    if (!loginRegex.matches(login)) return Left(ModelException.IllegalLoginCharacters())
    return Right(login.toLowerCase())
}
