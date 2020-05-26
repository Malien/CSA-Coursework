package ua.edu.ukma.csa

import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.model.*
import java.util.UUID


class ProductTest {

    private val id = UUID.randomUUID()
    private var product = Product(id, "Biscuit", "Sweet", 100, 20.5)

    @Test
    fun addProductCheck() {
        addProduct(product)
        assert(model.containsKey(product.id))

    }

    @Test
    fun getQuantityCheck() {
        getQuantity(product.id)
        assert(product.count == 100)

    }

    @Test
    fun setPriceValidate() {
        addProduct(product)
        setPrice(product.id, 15.6)
        assert(product.price == 15.6)
    }


    @Test
    fun setPriceInvalidate() {
        addProduct(product)
        setPrice(product.id, -1.5)
        assert(product.price != -1.5)

    }

    @Test
    fun deleteQuantityOfProductValidate() {
        addProduct(product)
        deleteQuantityOfProduct(product.id, 20)
        assert(product.count == 80)
    }

    @Test
    fun deleteQuantityOfProductNegativeMeaning() {
        addProduct(product)
        deleteQuantityOfProduct(product.id, 120)
        assert(product.count > 0)
    }

    @Test
    fun deleteQuantityOfProductLowerThanNull() {
        addProduct(product)
        deleteQuantityOfProduct(product.id, -5)
        assert(product.count != 95)
    }

    @Test
    fun addQuantityOfProductValidate() {
        addProduct(product)
        addQuantityOfProduct(product.id, 50)
        assert(product.count == 150)
    }

    @Test
    fun addQuantityOfProductInvalidate() {
        addProduct(product)
        addQuantityOfProduct(product.id, -50)
        assert(product.count == 100)
    }

    @Test
    fun addGroupCheck() {
        addProduct(product)
        addGroup(product.id, "Lego")
        addGroup(product.id, "Fruits")
        addGroup(product.id, "Vegetables")
        addGroup(product.id, "Lego")
        //println(setGroups)
    }

    @Test
    fun addGroupNameToProductCheck(){
        addProduct(product)
        addGroupNameToProduct(product.id, "Lego")
        println(product)
    }

}

