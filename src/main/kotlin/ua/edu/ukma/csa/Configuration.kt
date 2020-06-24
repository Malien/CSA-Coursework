package ua.edu.ukma.csa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.protobuf.ProtoBuf

object Configuration {
    val json = Json(JsonConfiguration.Stable.copy(encodeDefaults = false))
    val protobuf = ProtoBuf(encodeDefaults = false)
}
