package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

val model = ConcurrentHashMap<ProductID, Product>(100)
val groups = ConcurrentHashMap<GroupID, HashSet<Product>>(100)

@Serializable
data class Criteria(
    val name: String? = null,
    val fromPrice: Double? = null,
    val toPrice: Double? = null,
    val inGroups: Set<String> = emptySet()
)

enum class Order { ASCENDING, DESCENDING }

enum class ProductProperty { ID, NAME, PRICE, COUNT }

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This feature is supposed to be used only in testing. Production invocations are prohibited."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class TestingOnly

@Serializable
data class Ordering(
    val property: ProductProperty,
    val order: Order
)

interface ModelSource {
    /**
     * Retrieve [product][Product] from model by it's id.
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Product] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    fun getProduct(id: ProductID): Either<ModelException, Product>

    /**
     * Retrieve a list of products, filtered out by the [criteria][Criteria] provided
     * @param criteria [Criteria] which is used to filter out objects
     * @param ordering [Ordering] which is used to sort out results. If set to null, model's native ordering is applied.
     *                 _Defaults to `null`_
     * @param offset index from which to load data. `null` specifies that products should be retrieved from the
     *               beginning. _Defaults to `null`_
     * @param amount total amount of products to be retrieved
     * @return [Either] a [ModelException] in case operation cannot be fulfilled or a list of [Product]s otherwise
     */
    fun getProducts(
        criteria: Criteria,
        ordering: Ordering? = null,
        offset: Int? = null,
        amount: Int? = null
    ): Either<ModelException, List<Product>>

    /**
     * Remove product from model
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    fun removeProduct(id: ProductID): Either<ModelException, Unit>

    /**
     * Add product to the model
     * @param name name of the new product
     * @param count amount of product present in the model
     * @param price price of the product
     * @param groups set of [GroupID]s to which product belongs
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Product] otherwise
     */
    fun addProduct(
        id: ProductID,
        name: String,
        count: Int = 0,
        price: Double,
        groups: Set<GroupID> = emptySet()
    ): Either<ModelException, Product>
    /**
     * Remove some amount of product to the model
     * @param id [ProductID] of product specified
     * @param quantity amount of product to be removed
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or product's current count otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * if amount of product left after removal is less than 0, [Left] of [ModelException.ProductCanNotHaveThisCount]
     * will be returned
     * @note might want to unite [deleteQuantityOfProduct] and [addQuantityOfProduct] to use signed ints instead.
     */
    fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int>

    /**
     * Remove some amount of product to the model
     * @param id [ProductID] of product specified
     * @param quantity amount of product to be removed
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or product's current count otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * @note might want to unite [deleteQuantityOfProduct] and [addQuantityOfProduct] to use signed ints instead.
     */
    fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int>

    /**
     * Add new group to the model
     * @param name name of the new group
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Group] otherwise
     */
    fun addGroup(group: GroupID, name: String): Either<ModelException, Group>

    /**
     * Assign group by it's [id][GroupID] to the product
     * @param product [ProductID] of a product to assign group to
     * @param groupId [GroupID] of a group that is assigned to the product
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if group does not exist, [Left] of [ModelException.GroupDoesNotExist] will be returned
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    fun assignGroup(product: ProductID, groupId: GroupID): Either<ModelException, Unit>

    /**
     * Update the price of product in the model
     * @param id [ProductID] of a product which price will be set
     * @param price new price of a product
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * if product's price is invalid, [Left] of [ModelException.ProductCanNotHaveThisPrice] will be returned
     */
    fun setPrice(id: ProductID, price: Double): Either<ModelException, Double>

    /**
     * Clear all of the data from model. **NOTE: USE REALLY CAREFULLY AND FOR TESTS ONLY**
     * If model prohibits clears [ModelException] is returned or [Unit] otherwise
     */
    @TestingOnly
    fun clear(): Either<ModelException, Unit>
}