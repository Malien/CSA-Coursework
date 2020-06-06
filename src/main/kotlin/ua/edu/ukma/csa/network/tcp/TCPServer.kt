package ua.edu.ukma.csa.network.tcp

import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.handleStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.Key
import javax.crypto.Cipher
import kotlin.concurrent.thread

class TCPServer(
    port: Int, backlog: Int = 50, bindAddress: InetAddress = InetAddress.getByName("0.0.0.0")
) : Closeable {

    val serverSocket = ServerSocket(port, backlog, bindAddress)

    init {
        serverSocket.soTimeout = TIMEOUT
    }

    @Volatile
    var shouldStop = false

    fun serve(handler: (inputStream: InputStream, outputStream: OutputStream) -> Unit) {
        shouldStop = false
        while (true) {
            try {
                if (shouldStop) break
                val socket = serverSocket.accept()
                handler(socket.getInputStream(), socket.getOutputStream())
            } catch (ignore: SocketException) {
            } catch (ignore: SocketTimeoutException) {
            }
        }
    }

    override fun close() {
        shouldStop = true
        serverSocket.close()
    }

    companion object {
        private const val TIMEOUT = 1000
    }

}

fun TCPServer.serve(model: ModelSource) = serve { inputStream, outputStream ->
    thread { model.handleStream(inputStream, outputStream) }
}

fun TCPServer.serve(model: ModelSource, key: Key, cipher: Cipher) = serve { inputStream, outputStream ->
    thread { model.handleStream(inputStream, outputStream, key, cipher) }
}