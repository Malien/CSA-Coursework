package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import ua.edu.ukma.csa.kotlinx.java.sql.execute
import ua.edu.ukma.csa.kotlinx.java.sql.executeUpdate
import ua.edu.ukma.csa.kotlinx.java.sql.iterator
import ua.edu.ukma.csa.kotlinx.java.sql.transaction
import java.io.Closeable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement

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
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        count INTEGER NOT NULL DEFAULT(0),
        price REAL NOT NULL         |or|        price DECIMAL(8,4) NOT NULL

    Groups:
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,

    ProductGroups:
        productID INTEGER REFERENCES Products(id),
        groupID INTEGER REFERENCES Group(id)

    */

    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$dbName")

    init {
        Class.forName("org.sqlite.JDBC")
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS product (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT(0),
                price REAL NOT NULL CHECK (price >= 0),
                CONSTRAINT count_positive CHECK (count >= 0),
                CONSTRAINT price_positive CHECK (price >= 0)
            )
            """
        )
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS product_group (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL
            )
            """
        )
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS product_product_group (
                product_id INTEGER REFERENCES products(id),
                group_id INTEGER REFERENCES groups(id),
                CONSTRAINT pkey PRIMARY KEY (product_id, group_id)
            ) WITHOUT ROWID
            """
        )
    }

    /**
     * Retrieve [product][Product] from model by it's id.
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Product] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun getProduct(id: ProductID): Either<ModelException, Product> {
        TODO()
//        val statement = connection.createStatement()
//        val resultSet = statement.executeQuery(
//            String.format("select $id from 'products'")
//        )
//        resultSet.row
//        return Right()
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
        TODO()
//        val statement = connection.createStatement()
//        val resultSet = statement.executeQuery(
//            String.format("""select from 'products' limit %s offset %s , offset, offset * amount""")
//        )

//        val products = ArrayList<Product>()
//        while (resultSet.next()) {
//            products.add(
//                Product(
//                    resultSet.getInt("id"), resultSet.getString("name"),
//                    resultSet.getInt("count"), resultSet.getDouble("price")
//                )
//            )
//        }
//        return Right(products)
    }

    /**
     * Remove product from model
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun removeProduct(id: ProductID): Either<ModelException, Unit> {
        TODO()
    }

    private val productInsertStatement = connection.prepareStatement(
        "INSERT INTO product (name, count, price) VALUES (?,?,?)",
        Statement.RETURN_GENERATED_KEYS
    )

    /**
     * Add product to the model
     * @param name name of the new product
     * @param count amount of product present in the model
     * @param price price of the product
     * @param groups set of [GroupID]s to which product belongs
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Product] otherwise
     */
    override fun addProduct(
        name: String,
        count: Int,
        price: Double,
        groups: Set<GroupID>
    ): Either<ModelException, Product> = when {
        count < 0 -> Left(ModelException.ProductCanNotHaveThisCount(count))
        price < 0 -> Left(ModelException.ProductCanNotHaveThisPrice(price))
        // Wrap everything in a transaction
        else -> connection.transaction {
            // Check if all of the groups to be added to product exist
            if (groups.isNotEmpty()) {
                createStatement().use { statement ->
                    val result = statement.executeQuery(
                        """
                        SELECT id
                        FROM product_group
                        WHERE id IN ( ${groups.map { it.id }.joinToString()} )
                        """
                    )
                    val retrievedIDs = result.iterator().asSequence()
                        .map { it.getInt("id") }
                        .map { GroupID(it) }
                    val missingGroups = groups - retrievedIDs
                    if (missingGroups.isNotEmpty()) return Left(ModelException.GroupsNotPresent(missingGroups))
                }
            }

            productInsertStatement.setString(1, name)
            productInsertStatement.setInt(2, count)
            productInsertStatement.setDouble(3, price)
            productInsertStatement.executeUpdate()
            val id = productInsertStatement.generatedKeys.use { keys ->
                keys.next()
                keys.getInt(1)
            }

            if (groups.isNotEmpty()) {
                createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        INSERT INTO product_product_group (productID, groupID) 
                        VALUES ${groups.joinToString { "($id, ${it.id})" }}
                        """
                    )
                }
            }
            Right(Product(ProductID(id), name, count, price, groups))
        }
            .mapLeft { ModelException.SQL(it) }
            .flatMap { it }
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
        TODO()
//        val statement = connection.createStatement()
//        val resultSet = statement.executeQuery(
//            String.format("update count with $quantity from 'products' with $id ")
//        )

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
        TODO()
//        val statement = connection.createStatement()
//        val resultSet = statement.executeQuery(
//            String.format("update count with $quantity from 'products' with $id ")
//        )
    }

    /**
     * Add new group to the model
     * @param name name of the new group
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Group] otherwise
     */
    override fun addGroup(name: String): Either<ModelException, Group> {
        TODO()
//        val insertStatement =
//            connection.prepareStatement("""inset into groups(id, name) values(?,?)""")
//        connection.autoCommit
//        insertStatement.setString(1, group.toString())
//        insertStatement.setString(2, name)
//        insertStatement.executeQuery()
//        val result = insertStatement.generatedKeys
//        connection.commit()
//        return Right(result)
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
        TODO()

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
        TODO()
//        val statement = connection.createStatement()
//        val resultSet = statement.executeQuery(
//            String.format("update price with $price from 'products' with $id ")
//        )

    }

    /**
     * Erase all of the data from the model. **NOTE: USE REALLY CAREFULLY AND IN TESTS ONLY**
     * If model prohibits clears [ModelException] is returned or [Unit] otherwise
     */
    @TestingOnly
    override fun clear(): Either<ModelException, Unit> {
        connection.executeUpdate("DELETE FROM product")
        connection.executeUpdate("DELETE FROM product_groups")
        return Right(Unit)
    }

    override fun close() {
        productInsertStatement.close()
        connection.close() // Like this
    }
}

fun main() {
    val model = SQLiteModel("test.db")

    val product = model.addProduct(name = "Pr1", price = 1.2)
    println(product)

    val invalidProducts = listOf(
        model.addProduct(name = "Pr2", count = -3, price = 1.2),
        model.addProduct(name = "Pr2", count = 10, price = -1.2),
        model.addProduct(
            name = "Pr2",
            price = 1.2,
            groups = listOf(1, 2).map { GroupID(it) }.toSet()
        )
    )

    println(invalidProducts)
}