package org.example

/**
 * Хранилище подключённых клиентов на стороне сервера.
 */
class ServerClientsMap<SocketT> {
    private var nextId = 0
    private var clients = mutableMapOf<ClientId, SocketT>()

    fun addClient(socket: SocketT): ClientId {
        val id = "client_${nextId++}"
        clients[id] = socket
        return id
    }

    fun removeClient(id: ClientId): SocketT {
        val client = clients[id]!!
        clients = clients.filterNot { it.key == id }.toMutableMap()
        return client
    }

    fun allClients(): Map<ClientId, SocketT> = clients

    fun findIdByClient(client: SocketT): ClientId? {
        return allClients().entries.find { it.value == client }?.key
    }
}
