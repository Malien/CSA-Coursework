package ua.edu.ukma.csa.model

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import arrow.syntax.function.partially2
import com.zaxxer.hikari.HikariDataSource
import ua.edu.ukma.csa.kotlinx.arrow.core.bind
import ua.edu.ukma.csa.kotlinx.java.sql.execute
import ua.edu.ukma.csa.kotlinx.java.sql.executeUpdate
import ua.edu.ukma.csa.kotlinx.java.sql.iterator
import ua.edu.ukma.csa.kotlinx.java.sql.transaction
import ua.edu.ukma.csa.kotlinx.transformNotNull
import java.io.Closeable
import java.sql.PreparedStatement
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
class SQLiteModel(private val dbName: String) : ModelSource, Closeable {
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

    private val source: HikariDataSource

//    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$dbName")

    init {
        Class.forName("org.sqlite.JDBC")
        source = HikariDataSource()
        source.jdbcUrl = "jdbc:sqlite:$dbName"
        source.connection.use { connection ->
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
    }

    /**
     * Retrieve [product][Product] from model by it's id.
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Product] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun getProduct(id: ProductID): Either<ModelException, Product> = source.connection.use { connection ->
        connection.prepareStatement("SELECT id, name, count, price FROM product WHERE id = ?").use { statement ->
            statement.setInt(1, id.id)
            val result = statement.executeQuery()
            if (result.next()) {
                Right(
                    Product(
                        id = ProductID(result.getInt("id")),
                        name = result.getString("name"),
                        count = result.getInt("count"),
                        price = result.getDouble("price")
                    )
                )
            } else Left(ModelException.ProductDoesNotExist(id))
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
        ordering: List<Ordering>,
        offset: Int?,
        amount: Int?
    ): Either<ModelException, List<Product>> = when {
        offset != null && offset < 0 -> Left(ModelException.InvalidRequest("offset should be a positive integer"))
        amount != null && amount < 0 -> Left(ModelException.InvalidRequest("amount should be a positive integer"))
        offset != null && amount == null ->
            Left(ModelException.InvalidRequest("Cannot specify offset without also setting amount"))
        else -> source.connection.use { connection ->
            val whenStatement = listOfNotNull(
                criteria.preparedNameSQL("name"),
                criteria.preparedPriceSQL("price"),
                criteria.preparedGroupSQL("group_id").transformNotNull {
                    "id IN (SELECT product_id FROM product_product_group WHERE $it)"
                }
            ).joinToString(separator = " AND ")
            val query = listOfNotNull(
                "SELECT id, name, count, price FROM product",
                if (whenStatement.isEmpty()) null else "WHERE $whenStatement",
                preparedSQLLimit(amount),
                preparedSQLOffset(offset)
            ).joinToString(separator = " ")

            try {
                connection.prepareStatement(query).use { statement ->
                    listOf(
                        criteria::insertNamePlaceholders,
                        criteria::insertPricePlaceholders,
                        criteria::insertGroupsPlaceholders,
                        ::insertSQLLimit.partially2(amount),
                        ::insertSQLOffset.partially2(offset)
                    )
                        .map { it.bind(statement) }
                        .fold(1) { idx, inserter -> inserter(idx) }

                    val result = statement.executeQuery()

                    val products = result.iterator().asSequence().map { row ->
                        val id = row.getInt("id")
                        val name = row.getString("name")
                        val count = row.getInt("count")
                        val price = row.getDouble("price")
                        val groups = connection.createStatement().use { groupStatement ->
                            groupStatement.executeQuery(
                                "SELECT group_id FROM product_product_group WHERE product_id = $id"
                            ).iterator().asSequence()
                                .map { it.getInt("group_id") }
                                .map { GroupID(it) }
                                .toSet()
                        }
                        Product(ProductID(id), name, count, price, groups)
                    }

                    Right(products.toList())
                }
            } catch (e: SQLException) {
                Left(ModelException.SQL(e))
            }
        }
    }

    /**
     * Remove product from model
     * @param id [ProductID] of product specified
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or [Unit] otherwise
     * if product does not exist, [Left] of [ModelException.ProductDoesNotExist] will be returned
     */
    override fun removeProduct(id: ProductID): Either<ModelException, Unit> = source.connection.use { connection ->
        if (id.id == 0) return Left(ModelException.ProductDoesNotExist(id))
        connection.prepareStatement("""DELETE FROM product WHERE id = ? """).use { statement ->
            statement.setInt(1, id.id)
            statement.executeUpdate()
            statement.close()
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
        name: String,
        count: Int,
        price: Double,
        groups: Set<GroupID>
    ): Either<ModelException, Product> = when {
        count < 0 -> Left(ModelException.ProductCanNotHaveThisCount(count))
        price < 0 -> Left(ModelException.ProductCanNotHaveThisPrice(price))
        // Wrap everything in a transaction
        else -> source.connection.use { connection ->
            connection.transaction {
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

                val productInsertStatement = connection.prepareStatement(
                    "INSERT INTO product (name, count, price) VALUES (?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
                )
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
            }                                       // Either<SQLException,       Either<ModelException, Product>>
                .mapLeft { ModelException.SQL(it) } // Either<ModelException.SQL, Either<ModelException, Product>>
                .flatMap { it }                     // Either<ModelException,     Product>
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
    override fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Unit> {
        return if (quantity < 0) {
            Left(ModelException.ProductCanNotHaveThisCount(quantity))
        } else {
            source.connection.autoCommit = false
            val updateCount =
                source.connection.prepareStatement("""UPDATE product SET count = count - $quantity WHERE id = ${id.id}""")
            updateCount.executeUpdate()
            Right(Unit)
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
    override fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Unit> {
        return if (quantity < 0) {
            Left(ModelException.ProductCanNotHaveThisCount(quantity))
        } else {
            source.connection.autoCommit = false
            val updateCount =
                source.connection.prepareStatement("""UPDATE product SET count = count + $quantity WHERE id = ${id.id}""")
            updateCount.executeUpdate()
            Right(Unit)
        }
    }

    /**
     * Add new group to the model
     * @param name name of the new group
     * @return [Either] a [ModelException], in case operation cannot be fulfilled or newly created [Group] otherwise
     */
    override fun addGroup(name: String): Either<ModelException, Group> {

        val checkIfExistGroup =
            source.connection.prepareStatement("SELECT id, name FROM product_group WHERE name = ?")
        checkIfExistGroup.executeUpdate()
        source.connection.close()

        val groupInsertStatement = source.connection.prepareStatement(
            "INSERT INTO product_group (name) VALUES (?)",
            Statement.RETURN_GENERATED_KEYS
        )
        source.connection.close()

        groupInsertStatement.executeUpdate()
        groupInsertStatement.setString(1, name)
        val id = groupInsertStatement.generatedKeys.use { keys ->
            keys.next()
            keys.getInt(1)
        }
        return if (checkIfExistGroup.execute()) {
            Left(ModelException.GroupAlreadyExists(GroupID(id)))
        } else {
            Right(Group(GroupID(id), name))
        }
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

        val checkIfExistGroup =
            source.connection.prepareStatement("SELECT id, name FROM product_group WHERE id = ?")
        checkIfExistGroup.executeUpdate()
        source.connection.close()

        val checkIfExistProduct =
            source.connection.prepareStatement("SELECT id, name FROM product WHERE id = ?")
        checkIfExistProduct.executeUpdate()
        source.connection.close()

        source.connection.use { connection ->
            connection.prepareStatement(
                """
                        INSERT INTO product_product_group (productID, groupID) 
                        VALUES (?,?)
                        """
            ).executeUpdate()
        }
        source.connection.close()
        return when {
            checkIfExistGroup.execute() ->
                Left(ModelException.GroupAlreadyExists(groupId))

            checkIfExistProduct.execute() ->
                Left(ModelException.ProductAlreadyExists(product))

            else -> Right(Unit)
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
    override fun setPrice(id: ProductID, price: Double): Either<ModelException, Unit> {
        return if (price < 0) {
            Left(ModelException.ProductCanNotHaveThisPrice(price))
        } else {
            source.connection.autoCommit = false
            val setPrice =
                source.connection.prepareStatement("""UPDATE product SET price=$price WHERE id = ${id.id}""")
            setPrice.executeUpdate()
            Right(Unit)
        }
    }

    /**
     * Erase all of the data from the model. **NOTE: USE REALLY CAREFULLY AND IN TESTS ONLY**
     * If model prohibits clears [ModelException] is returned or [Unit] otherwise
     */
    @TestingOnly
    override fun clear(): Either<ModelException, Unit> = source.connection.use { connection ->
        connection.executeUpdate("DELETE FROM product")
        connection.executeUpdate("DELETE FROM product_group")
        return Right(Unit)
    }

    override fun close() {
        source.close()
    }

    companion object {
        fun preparedSQLOffset(offset: Int?) = offset.transformNotNull { "OFFSET ?" }
        fun preparedSQLLimit(amount: Int?) = amount.transformNotNull { "LIMIT ?" }

        fun insertSQLOffset(statement: PreparedStatement, offset: Int?, idx: Int) =
            idx + if (offset != null) {
                statement.setInt(idx, offset)
                1
            } else 0

        fun insertSQLLimit(statement: PreparedStatement, amount: Int?, idx: Int) =
            idx + if (amount != null) {
                statement.setInt(idx, amount)
                1
            } else 0

    }
}

fun main() {
    val model = SQLiteModel("test.db")

//    val product = model.addProduct(name = "Pr1", price = 1.2)
//    println(product)
//
//    val invalidProducts = listOf(
//        model.addProduct(name = "Pr2", count = -3, price = 1.2),
//        model.addProduct(name = "Pr2", count = 10, price = -1.2),
//        model.addProduct(
//            name = "Pr2",
//            price = 1.2,
//            groups = listOf(1, 2).map { GroupID(it) }.toSet()
//        )
//    )
//
//    println(invalidProducts)

    val groups = (1..2).map { GroupID(it) }.toSet()

    val productRequests = listOf(
        model.getProducts(),
        model.getProducts(offset = 1, amount = 1),
        model.getProducts(Criteria(name = "it"), offset = 0, amount = 5),
        model.getProducts(
            Criteria(fromPrice = 10.0),
            ordering = Ordering.by(ProductProperty.NAME).andThen(ProductProperty.PRICE, Order.DESCENDING)
        ),
        model.getProducts(Criteria(fromPrice = 10.0, toPrice = 15.0)),
        model.getProducts(Criteria(inGroups = groups))
    )
    productRequests.forEach(::println)

    val product = model.addProduct(name = "Pr1", count = 10, price = 1.2)
    println(product)

    val decreaseProductCount = product.flatMap { model.deleteQuantityOfProduct(it.id, 5) }
    println(decreaseProductCount)

    val decreaseInvalidProductCount = product.flatMap { model.deleteQuantityOfProduct(it.id, -5) }
    println(decreaseInvalidProductCount)

    val increaseProductCount = product.flatMap { model.addQuantityOfProduct(it.id, 3) }
    println(increaseProductCount)

    val increaseInvalidProductCount = product.flatMap { model.addQuantityOfProduct(it.id, -2) }
    println(increaseInvalidProductCount)

    val setProductPrice = product.flatMap { model.setPrice(it.id, 8.5) }
    println(setProductPrice)

    val getProduct = product.flatMap { model.getProduct(it.id) }
    println(getProduct)

    val prod2 = model.addProduct(name = "Pr2", count = 10, price = 1.2)
    println(prod2)

    val deleteProduct = product.flatMap { model.removeProduct(it.id) }
    println(deleteProduct)

    val addGroupOfProduct = product.flatMap { model.addGroup("Gr1") }
    println(addGroupOfProduct)
    println(product)

//    val assignGroupToProduct = product.flatMap { model.assignGroup(it.id, GroupID(it)) }
//    println(assignGroupToProduct)
}