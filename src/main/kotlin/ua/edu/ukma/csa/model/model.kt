package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val model = ConcurrentHashMap<UUID, Product>(100)
val groups = ConcurrentHashMap<String, HashSet<Product>>(100)

// TODO: JavaDoc this stuff

fun addProduct(product: Product): Either<ModelException.ProductAlreadyExists, Unit> {
    if (model.containsKey(product.id)) return Left(ModelException.ProductAlreadyExists(product.id))
    model[product.id] = product
    return Right(Unit)
}

fun getQuantity(id: UUID): Either<ModelException.ProductDoesNotExist, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return Right(product.count)
    }
}

fun deleteQuantityOfProduct(id: UUID, quantity: Int): Either<ModelException, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return if (quantity > 0 && quantity <= product.count) {
            product.count -= quantity
            Right(product.count)
        } else Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
    }
}

fun addQuantityOfProduct(id: UUID, quantity: Int): Either<ModelException, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return if (quantity > 0) {
            product.count += quantity
            Right(product.count)
        } else Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
    }
}

fun addGroup(newGroup: String): Either<ModelException.GroupAlreadyExists, Unit> {
    val existingGroup = groups[newGroup]
    if (existingGroup != null) {
        return Left(ModelException.GroupAlreadyExists(newGroup))
    }
    groups[newGroup] = HashSet()
    return Right(Unit)
}

fun assignGroup(id: UUID, groupName: String): Either<ModelException, Product> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    val group = groups[groupName] ?: return Left(ModelException.GroupDoesNotExist(groupName))
    synchronized(product) {
        synchronized(group) {
            return if (product in group) {
                Left(ModelException.ProductAlreadyInGroup(product, groupName))
            } else {
                group.add(product)
                Right(product)
            }
        }
    }
}

fun setPrice(id: UUID, price: Double): Either<ModelException, Double> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        if (price > 0) {
            product.price = price
        } else return Left(ModelException.ProductCanNotHaveThisPrice(id, price))
        return Right(product.price)
    }
}
