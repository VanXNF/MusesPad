package com.muses.entity

data class FilterClassEntity(
    val code: String,
    val `data`: List<FilterClassData>,
    val message: String
)

data class FilterClassData(
    val categoryName: String,
    val id: Int,
    val imageUrl: String
)