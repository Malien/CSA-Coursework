package ua.edu.ukma.csa.network

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import ua.edu.ukma.csa.kotlinx.binarySearch

enum class MessageType(val typeID: Int) {
    OK(0),
    ERR(1),
    PACKET_BEHIND(2),
    GET_COUNT(10),
    EXCLUDE(11),
    INCLUDE(12),
    ADD_GROUP(13),
    ASSIGN_GROUP(14),
    SET_PRICE(15);

    companion object {
        fun fromID(typeID: Int): Option<MessageType> {
            val idx = values().binarySearch { it.typeID.compareTo(typeID) }
            if (idx < 0) return None
            return Some(values()[idx])
        }
    }
}