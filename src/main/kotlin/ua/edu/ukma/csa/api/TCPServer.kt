package ua.edu.ukma.csa.api

import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.handleStream
import ua.edu.ukma.csa.network.tcp.TCPServer
import java.security.Key
import javax.crypto.Cipher
import kotlin.concurrent.thread

fun TCPServer.serve(model: ModelSource) = serve { inputStream, outputStream ->
    thread { model.handleStream(inputStream, outputStream) }
}

fun TCPServer.serve(model: ModelSource, key: Key, cipherFactory: () -> Cipher) = serve { inputStream, outputStream ->
    thread { model.handleStream(inputStream, outputStream, key, cipherFactory()) }
}
