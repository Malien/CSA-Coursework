package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val model = ConcurrentHashMap<ProductID, Product>(100)
val groups = ConcurrentHashMap<GroupID, HashSet<Product>>(100)

/*
    I can imagine we will have the following 3 tables in database:

    Products:
        id INT PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        count INT NOT NULL DEFAULT(0),
        price REAL NOT NULL         |or|        price DECIMAL(8,4) NOT NULL

    Groups:
        id INT PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,

    ProductGroups:
        productID INT REFERENCES Products(id),
        groupID INT REFERENCES Group(id)

 */

// TODO: KDoc this stuff

data class Criteria(
    val name: String? = null,
    val fromPrice: Double? = null,
    val toPrice: Double? = null,
    val inGroups: Set<String> = emptySet()
)

fun getProduct(id: ProductID): Either<ModelException, Product> {
    // TODO: Implement
    return Left(ModelException.NotImplemented())
}

fun getProducts(criteria: Criteria, offset: Int? = null, amount: Int? = null): Either<ModelException, List<Product>> {
    // TODO: Implement
    return Left(ModelException.NotImplemented())
}

fun removeProduct(id: ProductID): Either<ModelException, Unit> {
    // TODO: Implement
    return Left(ModelException.NotImplemented())
}

fun addProduct(product: Product): Either<ModelException.ProductAlreadyExists, Unit> {
    if (product.id != ProductID.UNSET) {} // TODO: This is a error. ProductID is already set, when trying to add new product
    if (model.containsKey(product.id)) return Left(ModelException.ProductAlreadyExists(product.id))
    model[product.id] = product
    return Right(Unit)
}

/** Can be removed, cause we have [getProduct] */
fun getQuantity(id: ProductID): Either<ModelException.ProductDoesNotExist, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return Right(product.count)
    }
}

fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return if (quantity > 0 && quantity <= product.count) {
            product.count -= quantity
            Right(product.count)
        } else Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
    }
}

fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return if (quantity > 0) {
            product.count += quantity
            Right(product.count)
        } else Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
    }
}

fun addGroup(newGroup: String): Either<ModelException.GroupAlreadyExists, Group> {
    val group = Group(id = GroupID.assign(), name = newGroup) // TODO: Should be set by the database
    val existingGroup = groups[group.id]
    if (existingGroup != null) {
        return Left(ModelException.GroupAlreadyExists(group.id))
    }
    groups[group.id] = HashSet()
    return Right(group)
}

fun assignGroup(id: ProductID, groupID: GroupID): Either<ModelException, Unit> {
    if (groupID == GroupID.UNSET) {} // TODO: This is an error
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    val group = groups[groupID] ?: return Left(ModelException.GroupDoesNotExist(groupID))
    synchronized(product) {
        synchronized(group) {
            return if (product in group) {
                Left(ModelException.ProductAlreadyInGroup(product, groupID))
            } else {
                group.add(product)
                Right(Unit)
            }
        }
    }
}

fun setPrice(id: ProductID, price: Double): Either<ModelException, Double> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        if (price > 0) {
            product.price = price
        } else return Left(ModelException.ProductCanNotHaveThisPrice(id, price))
        return Right(product.price)
    }
}
