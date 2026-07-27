package com.techwombat.reader.storage

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

object LocatorPersistence {
    fun serialize(locator: Locator): String = locator.toJSON().toString()

    fun deserialize(serialized: String?): Locator? = serialized?.let { json ->
        runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
    }
}
