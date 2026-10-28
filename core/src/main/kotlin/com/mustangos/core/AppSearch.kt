package com.mustangos.core

import java.text.Normalizer
import java.util.Locale

object AppSearch {
    private val accents = Regex("\\p{M}+")
    private fun normalized(value: String) = accents.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
    fun filter(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
        val term = normalized(query.trim())
        return if (term.isEmpty()) apps else apps.filter { normalized(it.label).contains(term) }
    }
}
