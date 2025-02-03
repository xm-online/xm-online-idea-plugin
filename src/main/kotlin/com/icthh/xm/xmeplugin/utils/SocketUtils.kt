package com.icthh.xm.xmeplugin.utils

import javax.net.ServerSocketFactory
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Random
import java.util.SortedSet
import java.util.TreeSet


class SocketUtils {

    private enum class SocketType {

        TCP {
            override fun isPortAvailable(port: Int): Boolean {
                try {
                    val serverSocket = ServerSocketFactory.getDefault().createServerSocket(
                        port, 1, InetAddress.getByName("localhost")
                    )
                    serverSocket.close()
                    return true
                } catch (ex: Exception) {
                    return false
                }

            }
        },

        UDP {
            override fun isPortAvailable(port: Int): Boolean {
                try {
                    val socket = DatagramSocket(port, InetAddress.getByName("localhost"))
                    socket.close()
                    return true
                } catch (ex: Exception) {
                    return false
                }

            }
        };

        protected abstract fun isPortAvailable(port: Int): Boolean

        private fun findRandomPort(minPort: Int, maxPort: Int): Int {
            val portRange = maxPort - minPort
            return minPort + random.nextInt(portRange + 1)
        }

        internal fun findAvailablePort(minPort: Int, maxPort: Int): Int {
            assertTrue(minPort > 0, "'minPort' must be greater than 0")
            assertTrue(maxPort >= minPort, "'maxPort' must be greater than or equal to 'minPort'")
            assertTrue(maxPort <= PORT_RANGE_MAX, "'maxPort' must be less than or equal to $PORT_RANGE_MAX")

            val portRange = maxPort - minPort
            var candidatePort: Int
            var searchCounter = 0
            do {
                if (searchCounter > portRange) {
                    throw IllegalStateException(
                        String.format(
                            "Could not find an available %s port in the range [%d, %d] after %d attempts",
                            name, minPort, maxPort, searchCounter
                        )
                    )
                }
                candidatePort = findRandomPort(minPort, maxPort)
                searchCounter++
            } while (!isPortAvailable(candidatePort))

            return candidatePort
        }

        internal fun findAvailablePorts(numRequested: Int, minPort: Int, maxPort: Int): SortedSet<Int> {
            assertTrue(minPort > 0, "'minPort' must be greater than 0")
            assertTrue(maxPort > minPort, "'maxPort' must be greater than 'minPort'")
            assertTrue(maxPort <= PORT_RANGE_MAX, "'maxPort' must be less than or equal to $PORT_RANGE_MAX")
            assertTrue(numRequested > 0, "'numRequested' must be greater than 0")
            assertTrue(
                maxPort - minPort >= numRequested,
                "'numRequested' must not be greater than 'maxPort' - 'minPort'"
            )

            val availablePorts = TreeSet<Int>()
            var attemptCount = 0
            while (++attemptCount <= numRequested + 100 && availablePorts.size < numRequested) {
                availablePorts.add(findAvailablePort(minPort, maxPort))
            }

            if (availablePorts.size != numRequested) {
                throw IllegalStateException(
                    String.format(
                        "Could not find %d available %s ports in the range [%d, %d]",
                        numRequested, name, minPort, maxPort
                    )
                )
            }

            return availablePorts
        }
    }

    companion object {

        /**
         * The default minimum value for port ranges used when finding an available
         * socket port.
         */
        val PORT_RANGE_MIN = 1024

        /**
         * The default maximum value for port ranges used when finding an available
         * socket port.
         */
        val PORT_RANGE_MAX = 65535


        private val random = Random(System.currentTimeMillis())

        @JvmOverloads
        fun findAvailableTcpPort(minPort: Int = PORT_RANGE_MIN, maxPort: Int = PORT_RANGE_MAX): Int {
            return SocketType.TCP.findAvailablePort(minPort, maxPort)
        }

        @JvmOverloads
        fun findAvailableTcpPorts(
            numRequested: Int,
            minPort: Int = PORT_RANGE_MIN,
            maxPort: Int = PORT_RANGE_MAX
        ): SortedSet<Int> {
            return SocketType.TCP.findAvailablePorts(numRequested, minPort, maxPort)
        }

        @JvmOverloads
        fun findAvailableUdpPort(minPort: Int = PORT_RANGE_MIN, maxPort: Int = PORT_RANGE_MAX): Int {
            return SocketType.UDP.findAvailablePort(minPort, maxPort)
        }

        @JvmOverloads
        fun findAvailableUdpPorts(
            numRequested: Int,
            minPort: Int = PORT_RANGE_MIN,
            maxPort: Int = PORT_RANGE_MAX
        ): SortedSet<Int> {
            return SocketType.UDP.findAvailablePorts(numRequested, minPort, maxPort)
        }

        private fun assertTrue(condition: Boolean, message: String) {
            if (!condition) {
                throw IllegalStateException(message)
            }
        }
    }

}
