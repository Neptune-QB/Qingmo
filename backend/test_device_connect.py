import java.net.InetSocketAddress
import java.net.Socket

fun main() {
    try {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", 8000), 3000)
        println("CONNECTED OK")
        socket.getOutputStream().write("GET /api/v1/health HTTP/1.0\r\nHost: localhost\r\n\r\n".toByteArray())
        val response = socket.getInputStream().bufferedReader().readText()
        println(response)
        socket.close()
    } catch (e: Exception) {
        println("FAILED: ${e.message}")
    }
}
