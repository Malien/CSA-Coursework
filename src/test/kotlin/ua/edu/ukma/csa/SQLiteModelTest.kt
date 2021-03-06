package ua.edu.ukma.csa

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.*
import kotlin.math.cos

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteModelTest {

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product
    private lateinit var avocado: Product

    private val model = SQLiteModel("test.db")

    private lateinit var sweets: Group
    private lateinit var cosmetics: Group
    private lateinit var diary: Group
    private lateinit var fruits: Group

    @BeforeEach
    fun populate() {
        model.clear()

        biscuit = model.addProduct(name = "Biscuit", count = 100, price = 20.5).handleWithThrow()
        conditioner = model.addProduct(name = "Hair conditioner", count = 20, price = 13.75).handleWithThrow()
        iceCream = model.addProduct(name = "Vanilla Ice Cream", count = 50, price = 7.59).handleWithThrow()
        avocado = model.addProduct(name = "Avocado Xaoss", count = 123, price = 38.59).handleWithThrow()

        sweets = model.addGroup("Sweets").handleWithThrow()
        cosmetics = model.addGroup("Cosmetics").handleWithThrow()
        diary = model.addGroup("Diary").handleWithThrow()
        fruits = model.addGroup("Fruits").handleWithThrow()
    }

    @AfterAll
    fun closeDB() {
        model.close()
    }

    @Test
    fun addProductCheck() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(
            model.addProduct(name = "Ninjago", count = -5, price = 1.3)
        )
        assertLeftType<ModelException.ProductCanNotHaveThisPrice>(
            model.addProduct(name = "Ninjago", count = 5, price = -1.3)
        )
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
        val deletedProduct = model.removeProduct(robot.id)
        assertLeftType<ModelException.ProductDoesNotExist>(deletedProduct)
    }

    @Test
    fun removeGroup() {
        val deleteGroup = model.removeGroup(fruits.id)
        assertRight(Unit, deleteGroup)
    }

    @Test
    fun removeGroupInvalidate() {
        val robot = Group(GroupID.UNSET, "Robot")
        val deletedProduct = model.removeGroup(robot.id)
        assertLeftType<ModelException.GroupDoesNotExist>(deletedProduct)
    }

    @Test
    fun addGroup() {
        val newGroup = model.addGroup("Sweets")
        assertLeftType<ModelException.GroupAlreadyExists>(newGroup)
    }

    @Test
    fun getGroupCheck() {
        val groupsMap = mapOf(sweets.id to sweets.name, cosmetics.id to cosmetics.name, diary.id to diary.name)
        val getGroupCount = model.getGroups()
        assertRight(groupsMap, getGroupCount)
    }

    @Test
    fun assignGroup() {
        model.assignGroup(biscuit.id, cosmetics.id).handleWithThrow()
        assertRight(true, model.getProduct(biscuit.id).map { it.groups }.map { cosmetics.id in it })
        assertLeftType<ModelException.ProductAlreadyInGroup>(model.assignGroup(biscuit.id, cosmetics.id))
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
        model.assignGroup(biscuit.id, sweets.id).handleWithThrow()
        model.assignGroup(conditioner.id, cosmetics.id).handleWithThrow()
        model.assignGroup(iceCream.id, sweets.id).handleWithThrow()
        model.assignGroup(iceCream.id, diary.id).handleWithThrow()

        val allProducts = model.getProducts().handleWithThrow()
        assertTrue(biscuit in allProducts)
        assertTrue(conditioner in allProducts)
        assertTrue(iceCream in allProducts)

        assertLeftType<ModelException.InvalidRequest>(model.getProducts(offset = allProducts[0].id.id, amount = -1))
        val afterFirst = model.getProducts(offset = 1, amount = 1).handleWithThrow()
        assertEquals(1, afterFirst.size)
        assertEquals(allProducts[1], afterFirst[0])

        val containsIt = model.getProducts(Criteria(name = "it")).handleWithThrow()
        assertTrue(biscuit in containsIt)
        assertTrue(conditioner in containsIt)
        assertTrue(iceCream !in containsIt)

        val startingAtPrice = model.getProducts(Criteria(fromPrice = 10.0)).handleWithThrow()
        assertTrue(biscuit in startingAtPrice)
        assertTrue(conditioner in startingAtPrice)
        assertTrue(iceCream !in startingAtPrice)

        val inPriceRange = model.getProducts(Criteria(fromPrice = 10.0, toPrice = 15.0)).handleWithThrow()
        assertEquals(1, inPriceRange.size)
        assertEquals(conditioner, inPriceRange[0])

        val inGroups = model.getProducts(Criteria(inGroups = setOf(cosmetics.id, diary.id))).handleWithThrow()
        assertTrue(biscuit !in inGroups)
        assertTrue(conditioner in inGroups)
        assertTrue(iceCream in inGroups)

        val ordered = model.getProducts(
            orderings = Ordering.by(ProductProperty.NAME, Order.DESCENDING).andThen(ProductProperty.PRICE)
        ).handleWithThrow()
        assertEquals(listOf(iceCream, conditioner, biscuit), ordered)
    }

    @Test
    fun getProductCountCheck() {
        val productsSet = setOf(iceCream.id, conditioner.id, biscuit.id)
        val getProductCount = model.getProductCount()
        assertRight(productsSet.size, getProductCount)
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
        model.deleteQuantityOfProduct(biscuit.id, 20).handleWithThrow()
        assertRight(80, model.getProduct(biscuit.id).map { it.count })
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
        model.addQuantityOfProduct(biscuit.id, 50).handleWithThrow()
        assertRight(150, model.getProduct(biscuit.id).map { it.count })
    }

    @Test
    fun addQuantityOfProductInvalidate() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(model.addQuantityOfProduct(biscuit.id, -50))
    }

    @Test
    fun updateProduct() {
        model.updateProduct(biscuit.id, name = "Not biscuit", price = 1.2, count = 5).handleWithThrow()
        val updatedProduct = model.getProduct(biscuit.id)

        assertRight("Not biscuit", updatedProduct.map { it.name })
        assertRight(1.2, updatedProduct.map { it.price })
        assertRight(5, updatedProduct.map { it.count })

        val groupSet = setOf(sweets.id, diary.id)
        model.updateProduct(biscuit.id, name = "Biscuit again", groups = groupSet).handleWithThrow()
        val withGroups = model.getProduct(biscuit.id)

        assertRight(groupSet, withGroups.map { it.groups })
        assertRight("Biscuit again", withGroups.map { it.name })

        val emptyGroup = emptySet<GroupID>()
        model.updateProduct(biscuit.id, groups = emptyGroup).handleWithThrow()
        val reset = model.getProduct(biscuit.id)

        assertRight(emptyGroup, reset.map { it.groups })
        assertRight("Biscuit again", reset.map { it.name })
        assertRight(1.2, reset.map { it.price })
        assertRight(5, reset.map { it.count })
    }

}
