package ua.edu.ukma.csa.model

import java.sql.SQLException

sealed class ModelException(msg: String) : RuntimeException(msg) {
    class ProductAlreadyExists(id: ProductID) : ModelException("Product with id $id already exists")
    class ProductDoesNotExist(id: ProductID) : ModelException("Product with id $id does not exist")
    class ProductCanNotHaveThisPrice(price: Double) :
        ModelException("Product can`t have price of $price")

    class ProductCanNotHaveThisCount(count: Int) :
        ModelException("Product can`t have count of $count")

    class ProductAlreadyInGroup(product: Product, group: GroupID) :
        ModelException("Product $product is already in group $group")

    class GroupAlreadyExists(group: GroupID) :
        ModelException("Product with group $group already exists")

    class InvalidRequest(message: String) : ModelException(message)

    data class GroupsNotPresent(val missingGroups: Set<GroupID>) :
        ModelException("Not all of the groups fom the set are present")

    class GroupDoesNotExist(group: GroupID) :
        ModelException("Group with $group doesn't exist")

    data class SQL(val error: SQLException) : ModelException("SQL exception raised: $error") {
        override fun toString() = "ModelException.SQL($error)"
    }

    // TODO: Remove this one
    class NotImplemented() :
        ModelException("Not implemented")
}

