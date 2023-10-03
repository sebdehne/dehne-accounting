package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.CategoryView
import com.dehnes.accounting.database.CategoryDto
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import javax.sql.DataSource

class CategoryService(
    private val repository: Repository,
    private val dataSource: DataSource,
) {

    fun get() = dataSource.readTx { conn -> get(conn) }

    fun get(connection: Connection): Categories {
        val list = repository.getAllCategories(connection)
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
) : InformationElement() {
    fun toRootTypeNull() = if (dto.parentCategoryId == null) RootCategory.entries.firstOrNull { it.id == id } else null
    fun toRootType() = toRootTypeNull() ?: error("$this is not a root category")
}


data class Categories(
    val asTree: List<CategoryLeaf>,
    val asList: List<CategoryDto>,
) {

    fun getDto(categoryId: String) =
        asList.firstOrNull { it.id == categoryId } ?: error("No such category with id=$categoryId")

    fun getView(categoryId: String) = asList
        .firstOrNull { it.id == categoryId }
        ?.let {
            CategoryView(
                it.id,
                it.name,
                it.description,
                it.parentCategoryId
            )
        } ?: error("No such category with id=$categoryId")

    fun findRoot(categoryId: String): CategoryLeaf {

        fun hasChild(leaf: CategoryLeaf, target: String): Boolean {
            return leaf.id == target || leaf.children.any { l ->
                hasChild(l, target)
            }
        }

        return asTree.first { root ->
            hasChild(root, categoryId)
        }
    }

    fun resolveAllChildIds(categoryIds: List<String>): Set<String> {
        fun findCategory(current: CategoryLeaf, wantedId: String): CategoryLeaf? {
            if (current.id == wantedId) return current
            current.children.forEach { c ->
                val r = findCategory(c, wantedId)
                if (r != null) return r
            }
            return null
        }

        fun findCategory(wantedId: String): CategoryLeaf {
            asTree.forEach { c ->
                val r = findCategory(c, wantedId)
                if (r != null) return r
            }
            error("Could not find category $wantedId")
        }

        fun getAllIds(current: CategoryLeaf): List<String> = listOf(current.id) + current.children.flatMap {
            getAllIds(it)
        }

        val finalList = categoryIds.ifEmpty { asTree.map { it.id } }

        return finalList.flatMap { nextId ->
            val categoryLeaf = findCategory(nextId)
            getAllIds(categoryLeaf)
        }.toSet()
    }
}