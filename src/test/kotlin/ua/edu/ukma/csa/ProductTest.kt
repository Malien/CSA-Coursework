package ua.edu.ukma.csa

import arrow.core.Either
import arrow.core.extensions.fx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.*
import kotlin.concurrent.thread

class ProductTest {

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    private lateinit var sweets: Group
    private lateinit var cosmetics: Group
    private lateinit var diary: Group

    @BeforeEach
    fun populate() {
        model.clear()
        groups.clear()

        biscuit = addProduct(name = "Biscuit", count = 100, price = 20.5).handleWithThrow()
        conditioner = addProduct(name = "Hair conditioner", count = 20, price = 13.75).handleWithThrow()
        iceCream = addProduct(name = "Vanilla Ice Cream", count = 50, price = 7.59).handleWithThrow()

        sweets = addGroup("Sweets").handleWithThrow()
        cosmetics = addGroup("Cosmetics").handleWithThrow()
        diary = addGroup("Diary").handleWithThrow()

        assignGroup(biscuit.id, sweets.id)
        assignGroup(conditioner.id, cosmetics.id)
        assignGroup(iceCream.id, diary.id)
    }

    @Test
    fun getQuantityCheck() {
        val count = getQuantity(biscuit.id)
        assertRight(100, count)
    }

    @Test
    fun setPriceValidate() {
        val newPrice = setPrice(biscuit.id, 15.6)
        assertRight(15.6, newPrice)
    }

    @Test
    fun setPriceInvalidate() {
        val result = setPrice(biscuit.id, -1.5)
        assertLeftType<ModelException.ProductCanNotHaveThisPrice>(result)
    }

    @Test
    fun deleteQuantityOfProductValidate() {
        val newCount = deleteQuantityOfProduct(biscuit.id, 20)
        assertRight(80, newCount)
    }

    @Test
    fun deleteQuantityOfProductNegativeMeaning() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(deleteQuantityOfProduct(biscuit.id, 120))
    }

    @Test
    fun deleteQuantityOfProductLowerThanNull() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(deleteQuantityOfProduct(biscuit.id, -5))
    }

    @Test
    fun addQuantityOfProductValidate() {
        val newCount = addQuantityOfProduct(biscuit.id, 50)
        assertRight(150, newCount)
    }

    @Test
    fun addQuantityOfProductInvalidate() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(addQuantityOfProduct(biscuit.id, -50))
    }

    @Test
    fun addGroupCheck() {
        val lego = addGroup("Lego").handleWithThrow()
        addGroup("Fruits").handleWithThrow()
        addGroup("Vegetables").handleWithThrow()
        assertLeftType<ModelException.GroupAlreadyExists>(addGroup("Lego"))
        assignGroup(biscuit.id, lego.id).handleWithThrow()
        assertLeftType<ModelException.GroupDoesNotExist>(assignGroup(biscuit.id, GroupID.UNSET))
    }

    @RepeatedTest(20)
    fun parallelChanges() {
        var special: Group? = null
        var discounted: Group? = null
        val instructionSets = listOf(
            {
                Either.fx<ModelException, Unit> {
                    special = addGroup("Special").bind()
                    assignGroup(biscuit.id, special!!.id).bind()
                    assignGroup(iceCream.id, special!!.id).bind()
                }
            }, {
                Either.fx {
                    discounted = addGroup("Discounted").bind()

                    setPrice(biscuit.id, 17.5).bind()
                    assignGroup(biscuit.id, discounted!!.id)

                    assignGroup(conditioner.id, discounted!!.id).bind()
                    setPrice(conditioner.id, 9.70).bind()
                    deleteQuantityOfProduct(conditioner.id, 10).bind()
                }
            }, {
                deleteQuantityOfProduct(biscuit.id, 20).map { Unit }
            }, {
                Either.fx {
                    deleteQuantityOfProduct(biscuit.id, 10).bind()
                    deleteQuantityOfProduct(iceCream.id, 10).bind()
                }
            }, {
                Either.fx {
                    deleteQuantityOfProduct(biscuit.id, 10).bind()
                    deleteQuantityOfProduct(iceCream.id, 10).bind()
                }
            }, {
                Either.fx {
                    addQuantityOfProduct(biscuit.id, 60).bind()
                    addQuantityOfProduct(conditioner.id, 20).bind()
                    addQuantityOfProduct(iceCream.id, 5).bind()
                }
            }
        )

        instructionSets
            .map { thread { it().handleWithThrow() } }
            .forEach { it.join() }

        assertTrue(groups.containsKey(special!!.id))
        assertTrue(groups.containsKey(discounted!!.id))
        assertTrue(biscuit in groups[special!!.id]!!)
        assertTrue(iceCream in groups[special!!.id]!!)
        assertTrue(biscuit in groups[discounted!!.id]!!)
        assertTrue(conditioner in groups[discounted!!.id]!!)
        assertEquals(17.5, biscuit.price)
        assertEquals(9.70, conditioner.price)
        assertRight(120, getQuantity(biscuit.id))
        assertRight(30, getQuantity(conditioner.id))
        assertRight(35, getQuantity(iceCream.id))
    }

}

