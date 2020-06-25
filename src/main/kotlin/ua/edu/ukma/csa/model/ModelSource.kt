package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import kotlinx.serialization.Serializable

enum class Order { ASCENDING, DESCENDING }

enum class ProductProperty { ID, NAME, PRICE, COUNT }

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This feature is supposed to be used only in testing. Production invocations are prohibited."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class TestingOnly

typealias Orderings = List<Ordering>

@Serializable
data class Ordering internal constructor(
    val properties: ProductProperty,
    val order: Order
) {
    companion object {
        fun by(property: ProductProperty, order: Order = Order.ASCENDING): Orderings =
            listOf(Ordering(property, order))
    }
}

fun Orderings.andThen(property: ProductProperty, order: Order = Order.ASCENDING) =
    this + Ordering(property, order)

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
     * @param orderings [Ordering] which is used to sort out results. If set to null, model's native ordering is applied.
     *                 _Defaults to `null`_
     * @param offset index from which to load data. `null` specifies that products should be retrieved from the
     *               beginning. _Defaults to `null`_
     * @param amount total amount of products to be retrieved
     * @return [Either] a [ModelException] in case operation cannot be fulfilled or a list of [Product]s otherwise
     */
    fun getProducts(
        criteria: Criteria = Criteria(),
        orderings: Orderings = emptyList(),
        offset: Int? = null,
        amount: Int? = null
    ): Either<ModelException, List<Product>>

    /**
     * Retrieve a count of the products
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Int] otherwise
     */
    fun getProductCount(): Either<ModelException, Int>

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
    fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Unit>

    /**
     * Remove some amount of product to the model
     * @param id [ProductID] of product specified
     * @param quantity amount of product to be removed
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or product's current count otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * @note might want to unite [deleteQuantityOfProduct] and [addQuantityOfProduct] to use signed ints instead.
     */
    fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Unit>

    /**
     * Add new group to the model
     * @param name name of the new group
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Group] otherwise
     */
    fun addGroup(name: String): Either<ModelException, Group>

    // TODO: Docs and tests
    fun removeGroup(id: GroupID): Either<ModelException, Unit>

    // TODO: Docs and tests
    fun getGroups(): Either<ModelException, List<Group>>

    /**
     * Assign group by it's [id][GroupID] to the product
     * @param productID [ProductID] of a product to assign group to
     * @param groupID [GroupID] of a group that is assigned to the product
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if group does not exist, [Left] of [ModelException.GroupDoesNotExist] will be returned
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    fun assignGroup(productID: ProductID, groupID: GroupID): Either<ModelException, Unit>

    /**
     * Update the price of product in the model
     * @param id [ProductID] of a product which price will be set
     * @param price new price of a product
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * if product's price is invalid, [Left] of [ModelException.ProductCanNotHaveThisPrice] will be returned
     */
    fun setPrice(id: ProductID, price: Double): Either<ModelException, Unit>

    /**
     * Update whole product
     * @param id [ProductID] of a product which should be updated
     * @param name new name of the product. If not set, name will not be changed. _Defaults to `null`_
     * @param price new price of the product. If not set, price will not be changed. _Defaults to `null`_
     * @param count new product count. If not set, count will not be changed. _Defaults to `null`_
     * @param groups new set of groups, product is assigned to. _Defaults to `null`_
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     */
    fun updateProduct(
        id: ProductID,
        name: String? = null,
        price: Double? = null,
        count: Int? = null,
        groups: Set<GroupID>? = null
    ): Either<ModelException, Unit>

    /**
     * Register user in the model
     * @param login unique login string
     * @param password users plain-text password
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [User] otherwise
     * if login does not match filter, [Left] of [ModelException.IllegalLoginCharacters] will be returned
     * if password does not match filter, [Left] of [ModelException.Password] will be returned
     */
    fun addUser(login: String, password: String): Either<ModelException, User>

    /**
     * Retrieve user by it's id.
     * @param id unique user id
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [User] otherwise
     */
    fun getUser(id: UserID): Either<ModelException, User>

    /**
     * retrieve user by it's login.
     * @param login unique user login
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [User] otherwise
     */
    fun getUser(login: String): Either<ModelException, User>

    /**
     * Check if the token specified is valid in the model
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Boolean] otherwise
     */
    fun isTokenValid(token: String): Either<ModelException, Boolean>

    /**
     * Invalidate token
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     */
    fun invalidateToken(token: String): Either<ModelException, Unit>

    /**
     * Include token into the model to be tracked as valid
     * I imagine invalid token cleanup would be done externally, like running aws lambda function every now and then.
     * This would require an additional timeout column and an index build for it.
     * @param token unique token to be tracked as valid
     * @param expiresAt UNIX epoch timestamp that signifies expiration date of the token. Invalid tokens should be
     * removed from database by something like aws lambda function every now and then
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     */
    fun approveToken(token: String, expiresAt: Long? = null): Either<ModelException, Unit>

    /**
     * Clear all of the data from model. **NOTE: USE REALLY CAREFULLY AND FOR TESTS ONLY**
     * If model prohibits clears [ModelException] is returned or [Unit] otherwise
     */
    @TestingOnly
    fun clear(): Either<ModelException, Unit>
}