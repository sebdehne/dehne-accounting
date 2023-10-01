package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.domain.InformationElement


data class CategoryView(
    override val id: String,
    override val name: String,
    override val description: String?,
    val parentCategoryId: String?,
): InformationElement()

