package ua.edu.ukma.csa.model

import java.util.*

sealed class ModelException(msg: String): RuntimeException(msg) {
    class ProductAlreadyExists(id: UUID) : ModelException("Product with id $id already exists")
}