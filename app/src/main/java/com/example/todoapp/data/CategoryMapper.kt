package com.example.todoapp.data

import com.google.mlkit.nl.entityextraction.Entity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryMapper @Inject constructor() {

    fun entityTypeToCategory(type: Int): String? = when (type) {
        Entity.TYPE_DATE_TIME -> "Time-bound"
        Entity.TYPE_EMAIL, Entity.TYPE_URL -> "Communication"
        Entity.TYPE_ADDRESS -> "Errands"
        Entity.TYPE_MONEY, Entity.TYPE_IBAN -> "Financial"
        else -> null
    }

    companion object {
        const val OPEN_ENDED = "Open-ended"
    }
}
