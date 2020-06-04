package ua.edu.ukma.csa.model

sealed class ModelException(msg: String) : RuntimeException(msg) {
    class ProductAlreadyExists(id: ProductID) : ModelException("Product with id $id already exists")
    class ProductDoesNotExist(id: ProductID) : ModelException("Product with id $id does not exist")
    class ProductCanNotHaveThisPrice(id: ProductID, price: Double) :
        ModelException("Product with id $id can`t have this price $price")

    class ProductCanNotHaveThisCount(id: ProductID, count: Int) :
        ModelException("Product with id $id can`t have this count $count")

    class ProductAlreadyInGroup(product: Product, group: String) :
        ModelException("Product $product is already in group $group")

    class GroupAlreadyExists(group: String) :
        ModelException("Product with group $group already exists")

    class GroupDoesNotExist(group: String) :
        ModelException("Product with group $group doesn't exist")
}

