package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.services.User

data class UserView(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val active: Boolean,
    val isAdmin: Boolean,
) : InformationElement() {
    companion object {
        fun fromUser(u: User) = UserView(
            id = u.id,
            name = u.name,
            description = u.description,
            userEmail = u.userEmail,
            active = u.isActive,
            isAdmin = u.isAdmin
        )
    }
}