package ua.edu.ukma.csa.kotlinx.java.security

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

operator fun KeyPair.component1(): PrivateKey = private
operator fun KeyPair.component2(): PublicKey = public
