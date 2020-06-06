package ua.edu.ukma.csa

import arrow.core.Either
import arrow.core.extensions.fx
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.*
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductTest {

    private val model = SQLiteModel(":memory:")

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    private lateinit var sweets: Group
    private lateinit var cosmetics: Group
    private lateinit var diary: Group

    @BeforeEach
    fun populate() {
        model.clear()

        biscuit = model.addProduct(name = "Biscuit", count = 100, price = 20.5).handleWithThrow()
        conditioner = model.addProduct(name = "Hair conditioner", count = 20, price = 13.75).handleWithThrow()
        iceCream = model.addProduct(name = "Vanilla Ice Cream", count = 50, price = 7.59).handleWithThrow()

        sweets = model.addGroup("Sweets").handleWithThrow()
        cosmetics = model.addGroup("Cosmetics").handleWithThrow()
        diary = model.addGroup("Diary").handleWithThrow()

        model.assignGroup(biscuit.id, sweets.id)
        model.assignGroup(conditioner.id, cosmetics.id)
        model.assignGroup(iceCream.id, diary.id)
    }

    @AfterAll
    fun close() {
        model.close()
    }

    @Test
    fun getQuantityCheck() {
        val count = model.getProduct(biscuit.id).map { it.count }
        assertRight(100, count)
    }

    @Test
    fun setPriceValidate() {
        val newPrice = model.setPrice(biscuit.id, 15.6)
        assertRight(15.6, newPrice)
    }

    @Test
    fun setPriceInvalidate() {
        val result = model.setPrice(biscuit.id, -1.5)
        assertLeftType<ModelException.ProductCanNotHaveThisPrice>(result)
    }

    @Test
    fun deleteQuantityOfProductValidate() {
        val newCount = model.deleteQuantityOfProduct(biscuit.id, 20)
        assertRight(80, newCount)
    }

    @Test
    fun deleteQuantityOfProductNegativeMeaning() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(model.deleteQuantityOfProduct(biscuit.id, 120))
    }

    @Test
    fun deleteQuantityOfProductLowerThanNull() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(model.deleteQuantityOfProduct(biscuit.id, -5))
    }

    @Test
    fun addQuantityOfProductValidate() {
        val newCount = model.addQuantityOfProduct(biscuit.id, 50)
        assertRight(150, newCount)
    }

    @Test
    fun addQuantityOfProductInvalidate() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(model.addQuantityOfProduct(biscuit.id, -50))
    }

    @Test
    fun addGroupCheck() {
        val lego = model.addGroup("Lego").handleWithThrow()
        model.addGroup("Fruits").handleWithThrow()
        model.addGroup("Vegetables").handleWithThrow()
        assertLeftType<ModelException.GroupAlreadyExists>(model.addGroup("Lego"))
        model.assignGroup(biscuit.id, lego.id).handleWithThrow()
        assertLeftType<ModelException.GroupDoesNotExist>(model.assignGroup(biscuit.id, GroupID.UNSET))
    }

    @RepeatedTest(20)
    fun parallelChanges() {
        var special: Group? = null
        var discounted: Group? = null
        val instructionSets = listOf(
            {
                Either.fx<ModelException, Unit> {
                    special = model.addGroup("Special").bind()
                    model.assignGroup(biscuit.id, special!!.id).bind()
                    model.assignGroup(iceCream.id, special!!.id).bind()
                }
            }, {
                Either.fx {
                    discounted = model.addGroup("Discounted").bind()

                    model.setPrice(biscuit.id, 17.5).bind()
                    model.assignGroup(biscuit.id, discounted!!.id)

                    model.assignGroup(conditioner.id, discounted!!.id).bind()
                    model.setPrice(conditioner.id, 9.70).bind()
                    model.deleteQuantityOfProduct(conditioner.id, 10).bind()
                }
            }, {
                model.deleteQuantityOfProduct(biscuit.id, 20).map { Unit }
            }, {
                Either.fx {
                    model.deleteQuantityOfProduct(biscuit.id, 10).bind()
                    model.deleteQuantityOfProduct(iceCream.id, 10).bind()
                }
            }, {
                Either.fx {
                    model.deleteQuantityOfProduct(biscuit.id, 10).bind()
                    model.deleteQuantityOfProduct(iceCream.id, 10).bind()
                }
            }, {
                Either.fx {
                    model.addQuantityOfProduct(biscuit.id, 60).bind()
                    model.addQuantityOfProduct(conditioner.id, 20).bind()
                    model.addQuantityOfProduct(iceCream.id, 5).bind()
                }
            }
        )

        instructionSets
            .map { thread { it().handleWithThrow() } }
            .forEach { it.join() }

        assertTrue(special!!.id in model.getProduct(biscuit.id).handleWithThrow().groups)
        assertTrue(special!!.id in model.getProduct(iceCream.id).handleWithThrow().groups)
        assertTrue(discounted!!.id in model.getProduct(biscuit.id).handleWithThrow().groups)
        assertTrue(discounted!!.id in model.getProduct(conditioner.id).handleWithThrow().groups)
        assertEquals(17.5, biscuit.price)
        assertEquals(9.70, conditioner.price)
        assertRight(120, model.getProduct(biscuit.id).map { it.count })
        assertRight(30, model.getProduct(conditioner.id).map { it.count })
        assertRight(35, model.getProduct(iceCream.id).map { it.count })
    }

}

