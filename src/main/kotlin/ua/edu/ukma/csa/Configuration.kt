package ua.edu.ukma.csa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

object Configuration {
    val json = Json(JsonConfiguration.Stable.copy(encodeDefaults = false))

}
