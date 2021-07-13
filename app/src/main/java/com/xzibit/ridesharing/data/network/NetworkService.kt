package com.xzibit.ridesharing.data.network

import com.xzibit.ridesharing.simulator.WebSocket
import com.xzibit.ridesharing.simulator.WebSocketListener

class NetworkService {

    fun createWebSocket(webSocketListener: WebSocketListener): WebSocket {
        return WebSocket(webSocketListener)
    }
}