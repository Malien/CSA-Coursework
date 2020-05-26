package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val model = ConcurrentHashMap<UUID, Product>(100)

// Here you can write your processing methods, for e.g.

fun addProduct(product: Product): Either<ModelException, Unit> {
    if (model.containsKey(product.id)) return Left(ModelException.ProductAlreadyExists(product.id))
    model[product.id] = product
    return Right(Unit)
}

fun getQuantity(id: UUID): Either<ModelException, Int> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        return Right(product.count)
    }
}

fun deleteQuantityOfProduct(id: UUID, quantity: Int): Either<ModelException, Unit> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        if (quantity > 0 && quantity <= model[id]!!.count)
            model[id]!!.count -= quantity
        else return Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
    }
    return Right(Unit)
}

fun addQuantityOfProduct(id: UUID, quantity: Int): Either<ModelException, Unit> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        if (quantity > 0)
            model[id]!!.count += quantity
        else return Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
    }
    return Right(Unit)
}

fun addGroup(id: UUID, newGroup: String): Either<ModelException, Unit> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        val setOfGroup: MutableSet<String> = mutableSetOf()
        setOfGroup.add(newGroup)
    }
    return Right(Unit)
}

//fun addGroupToProduct(id: UUID, groupName: String): Either<ModelException, Unit> {
//    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
//    synchronized(product) {
//    }
//    return Right(Unit)
//}

fun setPrice(id: UUID, price: Double): Either<ModelException, Unit> {
    val product = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
    synchronized(product) {
        if (price > 0) {
            var newPrice = price
            product!!.price = newPrice
        } else return Left(ModelException.ProductCanNotHaveThisPrice(id, price))
        return Right(Unit)
    }
}
