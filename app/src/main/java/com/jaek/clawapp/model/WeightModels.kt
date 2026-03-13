package com.jaek.clawapp.model

data class WeightEntry(
    val date: String,       // "YYYY-MM-DD"
    val weight: Float,
    val notes: String? = null
)
