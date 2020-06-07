package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import java.io.Closeable
import java.lang.RuntimeException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Represents a SQLite data-source.
 * @see ModelSource
 * @constructor constructs a SQLiteModel with the path to database file. Initializes driver and creates connection
 * @param dbName path to a database file. If path is an empty string, a temporary database is created with unique name
 *               each time. If `":memory:"` is passed, a new in-memory database will be created. In that case no files
 *               will be created. Every invocation of constructor with `":memory:"` path created independent database.
 * @throws SQLException if database connection could not be initialized
 * @throws ClassNotFoundException if SQLite JDBC driver cannot be found
 */
class SQLiteModel(dbName: String) : ModelSource, Closeable {
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

    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite: $dbName")

    init {
        // Here we initialize connection and other db stuff

        val forName = Class.forName("org.sqlite.JDBC")
        val connect = connection
        throw ClassNotFoundException("SQLite JDBC driver cannot be found")
        throw SQLException("Database connection could not be initialized")
        throw RuntimeException("Cannot find class")
    }


    /**
     * Retrieve [product][Product] from model by it's id.
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Product] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun getProduct(id: ProductID): Either<ModelException, Product> {
        val prod = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
        synchronized(prod) {
            return Right(prod)
        }
    }

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
    override fun getProducts(
        criteria: Criteria,
        ordering: Ordering?,
        offset: Int?,
        amount: Int?
    ): Either<ModelException, List<Product>> {
        val listOfProduct = ArrayList(model.values)
        //listOfProduct.filter { }
        if (ordering == null) {
            listOfProduct.sortBy { it.name }
        } //else listOfProduct.sortBy { }
        return Right(listOfProduct)
    }

    /**
     * Remove product from model
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun removeProduct(id: ProductID): Either<ModelException, Unit> {
        val prod = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
        synchronized(prod) {
            model.remove(id)
            return Right(Unit)
        }
    }

    /**
     * Add product to the model
     * @param name name of the new product
     * @param count amount of product present in the model
     * @param price price of the product
     * @param groups set of [GroupID]s to which product belongs
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Product] otherwise
     */
    override fun addProduct(
        id: ProductID,
        name: String,
        count: Int,
        price: Double,
        groups: Set<GroupID>
    ): Either<ModelException, Product> {
        var prod = model[id] ?: return Left(ModelException.ProductAlreadyExists(id))
        synchronized(prod) {
            prod = Product(id, name, count, price, groups)
            return Right(prod)
        }
    }

    /**
     * Remove some amount of product to the model
     * @param id [ProductID] of product specified
     * @param quantity amount of product to be removed
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or product's current count otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * if amount of product left after removal is less than 0, [Left] of [ModelException.ProductCanNotHaveThisPrice]
     * will be returned
     * @note might want to unite [deleteQuantityOfProduct] and [addQuantityOfProduct] to use signed ints instead.
     */
    override fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int> {
        val prod = model[id] ?: return Left(ModelException.ProductAlreadyExists(id))
        synchronized(prod) {
            return if (quantity > 0 && quantity < prod.count) {
                prod.count -= quantity
                Right(prod.count)
            } else return Left(ModelException.ProductCanNotHaveThisCount(id, quantity))
        }
    }

    /**
     * Remove some amount of product to the model
     * @param id [ProductID] of product specified
     * @param quantity amount of product to be removed
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or product's current count otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * @note might want to unite [deleteQuantityOfProduct] and [addQuantityOfProduct] to use signed ints instead.
     */
    override fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int> {
        val prod = model[id] ?: return Left(ModelException.ProductAlreadyExists(id))
        synchronized(prod) {
            return if (quantity > 0) {
                prod.count += quantity
                Right(prod.count)
            } else return Left(ModelException.ProductDoesNotExist(id))
        }
    }

    /**
     * Add new group to the model
     * @param name name of the new group
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Group] otherwise
     */
    override fun addGroup(group: GroupID, name: String): Either<ModelException, Group> {
        val existingGroup = groups[name]
        if (existingGroup != null) {
            return Left(ModelException.GroupAlreadyExists(name))
        }
        val addGroup = Group(group, name)
        return Right(addGroup)
    }

    /**
     * Assign group by it's [id][GroupID] to the product
     * @param product [ProductID] of a product to assign group to
     * @param groupId [GroupID] of a group that is assigned to the product
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if group does not exist, [Left] of [ModelException.GroupDoesNotExist] will be returned
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun assignGroup(product: ProductID, groupId: GroupID): Either<ModelException, Unit> {

        val prod = model[product] ?: return Left(ModelException.ProductDoesNotExist(product))
        val group = groups[groupId] ?: return Left(ModelException.GroupDoesNotExist(groupId))
        synchronized(prod) {
            synchronized(group) {
                return if (prod in group) {
                    Left(ModelException.ProductAlreadyInGroup(prod, groupId))
                } else {
                    group.add(prod)
                    Right(Unit)
                }
            }
        }
    }

    /**
     * Update the price of product in the model
     * @param id [ProductID] of a product which price will be set
     * @param price new price of a product
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or product's current price otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     * if product's price is invalid, [Left] of [ModelException.ProductCanNotHaveThisPrice] will be returned
     */
    override fun setPrice(id: ProductID, price: Double): Either<ModelException, Double> {
        val prod = model[id] ?: return Left(ModelException.ProductDoesNotExist(id))
        synchronized(prod) {
            return if (price > 0) {
                prod.price = price
                Right(prod.price)
            } else return Left(ModelException.ProductCanNotHaveThisPrice(id, price))
        }
    }

    /**
     * Erase all of the data from the model. **NOTE: USE REALLY CAREFULLY AND IN TESTS ONLY**
     * If model prohibits clears [ModelException] is returned or [Unit] otherwise
     */
    @TestingOnly
    override fun clear(): Either<ModelException, Unit> {
        model.clear()
        return Right(Unit)
    }

    override fun close() {
        TODO("Here we get free any resources left. This includes database connections")
        connection.close() // Like this
    }

}