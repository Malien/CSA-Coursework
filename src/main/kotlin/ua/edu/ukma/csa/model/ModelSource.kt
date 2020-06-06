package ua.edu.ukma.csa.model

import arrow.core.Either

interface ModelSource {
    fun getProduct(id: ProductID): Either<ModelException, Product>
    fun getProducts(criteria: Criteria, offset: Int? = null, amount: Int? = null): Either<ModelException, List<Product>>
    fun removeProduct(id: ProductID): Either<ModelException, Unit>
    fun addProduct(
        name: String,
        count: Int = 0,
        price: Double,
        groups: Set<GroupID> = emptySet()
    ): Either<ModelException, Product>
    fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int>
    fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int>
    fun addGroup(newGroup: String): Either<ModelException.GroupAlreadyExists, Group>
    fun assignGroup(id: ProductID, groupID: GroupID): Either<ModelException, Unit>
    fun setPrice(id: ProductID, price: Double): Either<ModelException, Double>
}