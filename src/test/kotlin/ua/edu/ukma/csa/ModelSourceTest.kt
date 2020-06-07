package ua.edu.ukma.csa

import org.junit.jupiter.api.BeforeEach
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.groups
import ua.edu.ukma.csa.model.model

class ModelSourceTest {

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


    }


}