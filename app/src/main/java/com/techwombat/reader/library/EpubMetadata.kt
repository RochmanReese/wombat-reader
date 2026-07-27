package com.techwombat.reader.library

import org.readium.r2.shared.publication.Publication

object EpubMetadata {
    fun title(publication: Publication): String? = publication.metadata.title
    fun author(publication: Publication): String? = publication.metadata.authors.joinToString(", ") { it.name }.ifBlank { null }
}
