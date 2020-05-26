package ua.edu.ukma.csa.model

import java.util.*

sealed class ModelException(msg: String) : RuntimeException(msg) {
    class ProductAlreadyExists(id: UUID) : ModelException("Product with id $id already exists")
    class ProductDoesNotExist(id: UUID) : ModelException("Product with id $id does not exist")
    class ProductCanNotHaveThisPrice(id: UUID, price: Double) : ModelException("Product with id $id can`t have this price $price")
    class ProductCanNotHaveThisCount(id: UUID, count: Int) : ModelException("Product with id $id can`t have this count $count")
    class GroupAlreadyExists(id: UUID, group: String) : ModelException("Product with id $id and group $group already exists")
}

