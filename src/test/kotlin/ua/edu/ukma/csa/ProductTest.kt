package ua.edu.ukma.csa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.*
import java.util.*


class ProductTest {

    private val id = UUID.randomUUID()
    private var product = Product(id, "Biscuit", "Sweet", 100, 20.5)

    init {
        addProduct(product)
    }

    @Test
    fun getQuantityCheck() {
        val count = getQuantity(product.id)
        assertRight(100, count)
    }

    @Test
    fun setPriceValidate() {
        setPrice(product.id, 15.6).handleWithThrow()
        assertEquals(15.6, product.price)
    }

    @Test
    fun setPriceInvalidate() {
        val result = setPrice(product.id, -1.5)
        assertLeftType<ModelException.ProductCanNotHaveThisPrice>(result)
    }

    @Test
    fun deleteQuantityOfProductValidate() {
        deleteQuantityOfProduct(product.id, 20).handleWithThrow()
        assertEquals(80, product.count)
    }

    @Test
    fun deleteQuantityOfProductNegativeMeaning() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(deleteQuantityOfProduct(product.id, 120))
        assertTrue(product.count > 0)
    }

    @Test
    fun deleteQuantityOfProductLowerThanNull() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(deleteQuantityOfProduct(product.id, -5))
        assertNotEquals(95, product.count)
    }

    @Test
    fun addQuantityOfProductValidate() {
        addQuantityOfProduct(product.id, 50).handleWithThrow()
        assertEquals(150, product.count)
    }

    @Test
    fun addQuantityOfProductInvalidate() {
        assertLeftType<ModelException.ProductCanNotHaveThisCount>(addQuantityOfProduct(product.id, -50))
        assertEquals(100, product.count)
    }

    @Test
    fun addGroupCheck() {
        addGroup("Lego").handleWithThrow()
        addGroup("Fruits").handleWithThrow()
        addGroup("Vegetables").handleWithThrow()
        assertLeftType<ModelException.GroupAlreadyExists>(addGroup("Lego"))
    }

    @Test
    fun addGroupNameToProductCheck(){
        addGroupNameToProduct(product.id, "Lego").handleWithThrow()
        assertLeftType<ModelException.GroupDoesNotExist>(addGroupNameToProduct(product.id, "Non existent"))
    }

}

