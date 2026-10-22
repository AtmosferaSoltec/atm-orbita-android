package com.atmosferast.orbita.data.remote

import com.atmosferast.orbita.domain.model.Category
import com.atmosferast.orbita.domain.model.MovementKind
import kotlinx.serialization.Serializable

// Bodies of /categories (atm-orbita-api, plan 03).

@Serializable
internal data class CategoryDto(
    val id: String,
    val name: String,
    val kind: String,
    val color: String,
) {
    fun toCategory() = Category(id = id, name = name, kind = kindOf(kind), colorHex = color)
}

@Serializable
internal data class CreateCategoryRequest(
    /** Chosen by the app: retrying with the same id never creates the category twice. */
    val id: String,
    val name: String,
    val kind: String,
    val color: String,
)

/** The kind is not sent: it does not change. */
@Serializable
internal data class UpdateCategoryRequest(val name: String, val color: String)

internal fun MovementKind.toApi(): String = when (this) {
    MovementKind.INCOME -> "income"
    MovementKind.EXPENSE -> "expense"
}

private fun kindOf(kind: String): MovementKind =
    if (kind == "income") MovementKind.INCOME else MovementKind.EXPENSE
