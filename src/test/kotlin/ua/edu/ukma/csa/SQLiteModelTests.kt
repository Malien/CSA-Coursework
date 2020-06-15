package ua.edu.ukma.csa

import arrow.core.Left
import arrow.core.Right
import arrow.core.extensions.list.functor.mapConst
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.*

class SQLiteModelTests {

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    private val model = SQLiteModel(":memory:")

    private lateinit var sweets: Group
    private lateinit var cosmetics: Group
    private lateinit var diary: Group

    private val source: HikariDataSource = HikariDataSource()


    @BeforeEach
    fun populate() {
        model.clear()

        biscuit = model.addProduct(name = "Biscuit", count = 100, price = 20.5).handleWithThrow()
        conditioner = model.addProduct(name = "Hair conditioner", count = 20, price = 13.75).handleWithThrow()
        iceCream = model.addProduct(name = "Vanilla Ice Cream", count = 50, price = 7.59).handleWithThrow()

        sweets = model.addGroup("Sweets").handleWithThrow()
        cosmetics = model.addGroup("Cosmetics").handleWithThrow()
        diary = model.addGroup("Diary").handleWithThrow()


    }

    @AfterAll
    fun closeDB() {
        model.close()
    }

    @Test
    fun addProductCheck() {
        val lego = model.addProduct(name = "Ninjago", count = -5, price = -1.3)
        assertLeftType<ModelException>(lego)
    }

    @Test
    fun removeProduct() {
        val deleteProduct = model.removeProduct(conditioner.id)
        assertRight(Unit, deleteProduct)
        val getDeletedProduct = model.getProduct(conditioner.id)
        assertLeftType<ModelException.ProductDoesNotExist>(getDeletedProduct)
    }

    @Test
    fun removeProductInvalidate() {
        val robot = Product(name = "Robot Technic", count = 5, price = 4567.9)
        val deleteProduct = model.removeProduct(robot.id)
        assertLeftType<ModelException.ProductDoesNotExist>(deleteProduct)
    }

    @Test
    fun addGroup() {
        val newGroup = model.addGroup("Sweets")
        assertLeftType<ModelException.GroupAlreadyExists>(newGroup)
    }

    @Test
    fun assignGroup() {
        model.assignGroup(biscuit.id, cosmetics.id).handleWithThrow()
        assertLeftType<ModelException.GroupDoesNotExist>(model.assignGroup(biscuit.id, GroupID.UNSET))
    }

    @Test
    fun getProduct() {
        val getProduct = model.getProduct(biscuit.id)
        assertRight(biscuit, getProduct)
    }

    @Test
    fun getProductCheck() {
        val getProduct = model.getProduct(ProductID.UNSET)
        assertLeftType<ModelException.ProductDoesNotExist>(getProduct)
    }

    @Test
    fun getProducts() {
        val groups = (1..2).map { GroupID(it) }.toSet()
        listOf(
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
    }

    @Test
    fun getProductsInvalidate() {
        val groups = (4..5).map { GroupID(it) }.toSet()
        listOf(
            model.getProducts(),
            model.getProducts(offset = -2, amount = -1),
            model.getProducts(Criteria(name = "it"), offset = 1, amount = 5),
            model.getProducts(
                Criteria(fromPrice = 10.0),
                ordering = Ordering.by(ProductProperty.NAME).andThen(ProductProperty.PRICE, Order.DESCENDING)
            ),
            model.getProducts(Criteria(fromPrice = 10.0, toPrice = 15.0)),
            model.getProducts(Criteria(inGroups = groups))
        )
    }

    @Test
    fun setPriceValidate() {
        val newPrice = model.setPrice(biscuit.id, 15.6)
        assertRight(Unit, newPrice)
        val getProduct = model.getProduct(biscuit.id)
        assertRight(15.6, getProduct.map { it.price })

    }

    @Test
    fun setPriceInvalidate() {
        val result = model.setPrice(biscuit.id, -1.5)
        assertLeftType<ModelException.ProductCanNotHaveThisPrice>(result)
    }

    @Test
    fun deleteQuantityOfProductValidate() {
        val newCount = model.deleteQuantityOfProduct(biscuit.id, 20)
        assertRight(Unit, newCount)
    }

    @Test
    fun deleteQuantityOfProductNegativeMeaning() {
        assertLeftType<ModelException.SQL>(model.deleteQuantityOfProduct(biscuit.id, 120))
    }

    @Test
    fun deleteQuantityOfProductLowerThanNull() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(model.deleteQuantityOfProduct(biscuit.id, -5))
    }

    @Test
    fun addQuantityOfProductValidate() {
        val newCount = model.addQuantityOfProduct(biscuit.id, 50)
        assertRight(Unit, newCount)
    }

    @Test
    fun addQuantityOfProductInvalidate() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(model.addQuantityOfProduct(biscuit.id, -50))
    }
}
