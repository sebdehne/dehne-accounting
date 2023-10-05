package com.dehnes.accounting.services

import com.dehnes.accounting.database.CategoryDto
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import javax.sql.DataSource

class CategoryReadService(
    private val repository: Repository,
    private val dataSource: DataSource,
) {

    fun get(ledgerId: String) = dataSource.readTx { conn -> get(conn, ledgerId) }

    fun get(connection: Connection, ledgerId: String): Categories {
        val list = repository.getAllCategories(connection, ledgerId)
        return Categories(
            readTree(list),
            list
        )
    }

    private fun readTree(allCategories: List<CategoryDto>): List<CategoryLeaf> {

        fun getChildren(p: CategoryLeaf) = allCategories.filter { it.parentCategoryId == p.id }

        fun populateChildren(p: CategoryLeaf): CategoryLeaf {
            val children = getChildren(p).map {
                CategoryLeaf(
                    it.id,
                    it.name,
                    it.description,
                    it,
                    emptyList()
                )
            }.map { populateChildren(it) }

            return p.copy(children = children)
        }

        return allCategories
            .filter { it.parentCategoryId == null }
            .map {
                CategoryLeaf(
                    it.id,
                    it.name,
                    it.description,
                    it,
                    emptyList()
                )
            }
            .map { populateChildren(it) }
    }


}


data class CategoryLeaf(
    override val id: String,
    override val name: String,
    override val description: String?,
    val dto: CategoryDto,
    val children: List<CategoryLeaf>,
) : InformationElement()


data class Categories(
    val asTree: List<CategoryLeaf>,
    val asList: List<CategoryDto>,
)
