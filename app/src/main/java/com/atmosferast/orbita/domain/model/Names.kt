package com.atmosferast.orbita.domain.model

import java.text.Normalizer

private val WHITESPACE = Regex("\\s+")
private val COMBINING_MARKS = Regex("\\p{M}+")

// Stands in for the ñ while the accents are removed; it cannot be typed.
private const val ENYE_PLACEHOLDER = '\u0001'

/** A name as it is stored and shown: trimmed, with single spaces, otherwise as typed. */
fun cleanName(name: String): String = name.trim().replace(WHITESPACE, " ")

/**
 * The key two names are compared by: lowercase, without accents, with single spaces.
 * "Alimentación", "alimentacion" and "ALIMENTACIÓN" give the same key. The ñ is a letter of
 * its own, not an accented n, so it stays: "Año" and "Ano" are different names.
 *
 * It is never shown. The backend computes the same key to keep category names unique, so both
 * must give the same result (atm-orbita-api, docs/plans/05).
 */
fun normalizeName(name: String): String {
    // Composed first, so an ñ typed as n + combining tilde is protected too.
    val composed = Normalizer.normalize(cleanName(name).lowercase(), Normalizer.Form.NFC)
        .replace('ñ', ENYE_PLACEHOLDER)
    val withoutAccents = Normalizer.normalize(composed, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
    return Normalizer.normalize(withoutAccents.replace(ENYE_PLACEHOLDER, 'ñ'), Normalizer.Form.NFC)
}
