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
