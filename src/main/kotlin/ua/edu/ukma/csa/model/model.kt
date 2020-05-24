package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.product
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val model = ConcurrentHashMap<UUID, Product>(100)

// Here you can write your processing methods, for e.g.

fun addProduct(product: Product): Either<ModelException, Unit> {
    if (model.containsKey(product.id)) return Left(ModelException.ProductAlreadyExists(product.id))
    model[product.id] = product
    return Right(Unit)
}

fun findOutQuantityOfProduct(product: Product): Either<ModelException, Unit> {
    if (model.containsKey(product.id)) return Left(ModelException.ProductAlreadyExists(product.id))
    model[product.id]!!.count = product.count

    return Right(Unit)
}

fun deleteQuantityOfProduct(id: UUID, quantity: Int): Either<ModelException, Unit> {
    if (model.containsKey(id)) return Left(ModelException.ProductAlreadyExists(id))
    require(quantity > 0)
    model[id]!!.count -= quantity

    return Right(Unit)
}

fun addQuantityOfProduct(id: UUID, quantity: Int): Either<ModelException, Unit> {
    if (model.containsKey(id)) return Left(ModelException.ProductAlreadyExists(id))
    require(quantity > 0)
    model[id]!!.count += quantity

    return Right(Unit)
}

fun addGroup(id: UUID, newGroup: Set<String>): Either<ModelException, Unit> {
    if (model.containsKey(id)) return Left(ModelException.ProductAlreadyExists(id))
    val set = mutableSetOf<String>(model[id]!!.groups.toString())
    set.add(newGroup.toString())

    return Right(Unit)
}

fun addNameOfProductToGroup(id: UUID, nameProduct: String, newGroup: Set<String>): Either<ModelException, Unit> {
    if (model.containsKey(id)) return Left(ModelException.ProductAlreadyExists(id))
    val set = mutableSetOf<String>(model[id]!!.groups.toString())
    val setGroup = set.groupBy { newGroup }
//finish method
    return Right(Unit)
}

fun setPrice(id: UUID, price: Double): Either<ModelException, Unit> {
    if (model.containsKey(id)) return Left(ModelException.ProductAlreadyExists(id))
    model[id]!!.price = price


    return Right(Unit)
}