package ua.edu.ukma.csa.model

import java.sql.SQLException

sealed class ModelException(msg: String) : RuntimeException(msg) {
    class ProductDoesNotExist(id: ProductID) : ModelException("Product with id $id does not exist")
    class ProductCanNotHaveThisPrice(price: Double) :
        ModelException("Product can`t have price of $price")

    class ProductCanNotHaveThisCount(count: Int) :
        ModelException("Product can`t have count of $count")

    class ProductAlreadyInGroup(product: Product, group: GroupID) :
        ModelException("Product $product is already in group $group")

    class GroupAlreadyExists(group: GroupID) :
        ModelException("Product with group $group already exists")

    class InvalidRequest(message: String) : ModelException(message)

    data class GroupsNotPresent(val missingGroups: Set<GroupID>) :
        ModelException("Not all of the groups fom the set are present")

    class GroupDoesNotExist(group: GroupID) : ModelException("Group with $group doesn't exist")

    class UserLoginAlreadyExists(login: String) : ModelException("User with login $login already exists")

    class UserDoesNotExist(id: UserID) : ModelException("User with $id does not exist")

    sealed class Password(reason: String) : ModelException(reason) {
        class Length(expected: Int, got: Int) :
            Password("Password is too short. Expected at least $expected, got $got")

        class NoDigits : Password("Password should contain at least one digit")
        class NoUppercase : Password("Password should contain at least one uppercase letter")
        class NoLowercase : Password("Password should contain at least one lowercase letter")
        class ForbiddenCharacters : Password("Password contains illegal characters")
    }

    data class SQL(val error: SQLException) : ModelException("SQL exception raised: $error") {
        override fun toString() = "ModelException.SQL($error)"
    }

    // TODO: Remove this one
    class NotImplemented() :
        ModelException("Not implemented")
}

