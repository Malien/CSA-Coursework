package ua.edu.ukma.csa.model

import arrow.core.Either
import java.io.Closeable
import java.sql.Connection

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

    val connection: Connection = TODO("Not yet implemented")

    init {
        // Here we initialize connection and other db stuff
    }

    override fun getProduct(id: ProductID): Either<ModelException, Product> {
        TODO("Not yet implemented")
    }

    override fun getProducts(criteria: Criteria, offset: Int?, amount: Int?): Either<ModelException, List<Product>> {
        TODO("Not yet implemented")
    }

    override fun removeProduct(id: ProductID): Either<ModelException, Unit> {
        TODO("Not yet implemented")
    }

    override fun addProduct(
        name: String,
        count: Int,
        price: Double,
        groups: Set<GroupID>
    ): Either<ModelException, Product> {
        TODO("Not yet implemented")
    }

    override fun deleteQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int> {
        TODO("Not yet implemented")
    }

    override fun addQuantityOfProduct(id: ProductID, quantity: Int): Either<ModelException, Int> {
        TODO("Not yet implemented")
    }

    override fun addGroup(newGroup: String): Either<ModelException.GroupAlreadyExists, Group> {
        TODO("Not yet implemented")
    }

    override fun assignGroup(id: ProductID, groupID: GroupID): Either<ModelException, Unit> {
        TODO("Not yet implemented")
    }

    override fun setPrice(id: ProductID, price: Double): Either<ModelException, Double> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Here we get free any resources left. This includes database connections")
        // connection.close() // Like this
    }
}