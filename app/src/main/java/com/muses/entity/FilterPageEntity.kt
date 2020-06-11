package com.muses.entity

data class FilterPageEntity(
    val currentPage: Int,
    val dataList: List<FilterData>,
    val pageCount: Int,
    val pageSize: Int,
    val totalNum: Int
)

data class FilterData(
    val base64Image: Any,
    val brushIntensity: Int,
    val brushSize: Int,
    val coverImage: String,
    val description: Any,
    val filterName: String,
    val ownerId: Int,
    val publishDate: Long,
    val smooth: Int,
    val uploadId: Int,
    val vipOnly: Boolean
)