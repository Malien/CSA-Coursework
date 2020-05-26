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
import java.util.*
import kotlin.concurrent.thread


class ProductTest {

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    @BeforeEach
    fun populate() {
        model.clear()
        groups.clear()

        biscuit = Product(name = "Biscuit", count = 100, price = 20.5)
        conditioner = Product(name = "Hair conditioner", count = 20, price = 13.75)
        iceCream = Product(name = "Vanilla Ice Cream", count = 50, price = 7.59)

        addProduct(biscuit)
        addProduct(conditioner)
        addProduct(iceCream)

        addGroup("Sweets")
        addGroup("Cosmetics")
        addGroup("Diary")

        assignGroup(biscuit.id, "Sweets")
        assignGroup(conditioner.id, "Cosmetics")
        assignGroup(iceCream.id, "Diary")
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
        addGroup("Lego").handleWithThrow()
        addGroup("Fruits").handleWithThrow()
        addGroup("Vegetables").handleWithThrow()
        assertLeftType<ModelException.GroupAlreadyExists>(addGroup("Lego"))
        assignGroup(biscuit.id, "Lego").handleWithThrow()
        assertLeftType<ModelException.GroupDoesNotExist>(assignGroup(biscuit.id, "Non existent"))
    }

    sealed class Change {
        data class AddGroup(val name: String) : Change()
        data class AssignGroup(val id: UUID, val group: String) : Change()
        data class AddProduct(val product: Product) : Change()
        data class AddQuantity(val id: UUID, val quantity: Int) : Change()
        data class RemoveQuantity(val id: UUID, val quantity: Int) : Change()
        data class SetPrice(val id: UUID, val price: Double) : Change()

        fun handle() = when (this) {
            is AddGroup -> addGroup(name)
            is AssignGroup -> assignGroup(id, group)
            is AddProduct -> addProduct(product)
            is AddQuantity -> addQuantityOfProduct(id, quantity)
            is RemoveQuantity -> deleteQuantityOfProduct(id, quantity)
            is SetPrice -> setPrice(id, price)
        }
    }

    @RepeatedTest(20)
    fun parallelChanges() {
        val instructionSets = listOf(
            {
                Either.fx<ModelException, Unit> {
                    addGroup("Special").bind()
                    assignGroup(biscuit.id, "Special").bind()
                    assignGroup(iceCream.id, "Special").bind()
                }
            }, {
                Either.fx<ModelException, Unit> {
                    addGroup("Discounted").bind()

                    setPrice(biscuit.id, 17.5).bind()
                    assignGroup(biscuit.id, "Discounted")

                    assignGroup(conditioner.id, "Discounted").bind()
                    setPrice(conditioner.id, 9.70).bind()
                    deleteQuantityOfProduct(conditioner.id, 10).bind()
                }
            }, {
                deleteQuantityOfProduct(biscuit.id, 20).map { Unit }
            }, {
                Either.fx<ModelException, Unit> {
                    deleteQuantityOfProduct(biscuit.id, 10).bind()
                    deleteQuantityOfProduct(iceCream.id, 10).bind()
                }
            }, {
                Either.fx<ModelException, Unit> {
                    deleteQuantityOfProduct(biscuit.id, 10).bind()
                    deleteQuantityOfProduct(iceCream.id, 10).bind()
                }
            }, {
                Either.fx<ModelException, Unit> {
                    addQuantityOfProduct(biscuit.id, 60).bind()
                    addQuantityOfProduct(conditioner.id, 20).bind()
                    addQuantityOfProduct(iceCream.id, 5).bind()
                }
            }
        )

        instructionSets
            .map { thread { it().handleWithThrow() } }
            .forEach { it.join() }

        assertTrue(groups.containsKey("Special"))
        assertTrue(groups.containsKey("Discounted"))
        assertTrue(biscuit in groups["Special"]!!)
        assertTrue(iceCream in groups["Special"]!!)
        assertTrue(biscuit in groups["Discounted"]!!)
        assertTrue(conditioner in groups["Discounted"]!!)
        assertEquals(17.5, biscuit.price)
        assertEquals(9.70, conditioner.price)
        assertRight(120, getQuantity(biscuit.id))
        assertRight(30, getQuantity(conditioner.id))
        assertRight(35, getQuantity(iceCream.id))
    }

}

