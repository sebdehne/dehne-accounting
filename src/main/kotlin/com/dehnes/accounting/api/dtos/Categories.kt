package com.dehnes.accounting.api.dtos


data class MergeCategoriesRequest(
    val sourceCategoryId: String,
    val destinationCategoryId: String,
)

