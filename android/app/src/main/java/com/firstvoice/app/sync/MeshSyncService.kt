package com.firstvoice.app.sync

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.net.wifi.p2p.WifiP2pManager.*
import android.util.Log
import com.firstvoice.app.data.local.dao.RadioMessageDao
import com.firstvoice.app.data.local.dao.TriageCardDao
import com.firstvoice.app.data.local.dao.VoiceClipDao
import com.firstvoice.app.data.local.entity.RadioMessageEntity
import com.firstvoice.app.data.local.entity.TriageCardEntity
import com.firstvoice.app.data.local.entity.VoiceClipEntity
import com.firstvoice.app.data.model.RadioMessage
import com.firstvoice.app.data.model.TriageCard
import com.firstvoice.app.data.model.VoiceClip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * WiFi-Direct mesh sync with clean state machine:
 *
 * States: IDLE → DISCOVERING → CONNECTING → CONNECTED → SYNCING
 *
 * Flow:
 * 1. Start discovery every 5s
 * 2. When peers found, ONE device connects (lower MAC initiates)
 * 3. Connection established → store IP → do initial sync
 * 4. Stay connected. Periodic sync every 3s via TCP using stored IP
 * 5. If connection drops, go back to DISCOVERING
 */
@SuppressLint("MissingPermission")
class MeshSyncService(
    private val context: Context,
    private val triageCardDao: TriageCardDao,
    private val radioMessageDao: RadioMessageDao,
    private val voiceClipDao: VoiceClipDao,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "MeshSync"
        private const val SYNC_PORT = 8765
        private const val SOCKET_TIMEOUT = 10_000
    }

    enum class State { IDLE, DISCOVERING, CONNECTING, CONNECTED }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var manager: WifiP2pManager? = null
    private var channel: Channel? = null
    private var initialized = false

    // Clean state
    @Volatile private var state = State.IDLE
    @Volatile private var groupOwnerIp: String? = null
    @Volatile private var amGroupOwner = false
    @Volatile private var isSyncing = false
    private var myMacAddress: String? = null
    private var discoveryFailCount = 0

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    data class PeerDevice(val deviceName: String, val deviceAddress: String, val isGroupOwner: Boolean = false)
    data class SyncState(
        val connectedPeerCount: Int = 0, val lastSyncTimestamp: Long? = null,
        val cardsSent: Int = 0, val cardsReceived: Int = 0, val totalSynced: Int = 0,
        val isSyncing: Boolean = false, val error: String? = null
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val on = intent.getIntExtra(EXTRA_WIFI_STATE, -1) == WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "WiFi P2P: ${if (on) "ON" else "OFF"}")
                }

                WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(EXTRA_WIFI_P2P_DEVICE)
                    myMacAddress = device?.deviceAddress
                    Log.d(TAG, "My MAC: $myMacAddress")
                }

                WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        val devices = peerList.deviceList.map { PeerDevice(it.deviceName, it.deviceAddress) }
                        if (devices.isNotEmpty()) {
                            _peers.value = devices
                            _syncState.value = _syncState.value.copy(connectedPeerCount = devices.size)
                            Log.d(TAG, "Found ${devices.size} peers")

                            // If not connected, connect to first peer
                            if (state == State.DISCOVERING || state == State.IDLE) {
                                connectToPeer(devices.first())
                            }
                        }
                    }
                }

                WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            onConnected(info)
                        }
                    } else if (state == State.CONNECTED || state == State.CONNECTING) {
                        Log.d(TAG, "P2P disconnected — back to discovering")
                        state = State.IDLE
                        groupOwnerIp = null
                        amGroupOwner = false
                    }
                }
            }
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true

        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context.mainLooper, null)

        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        })

        Log.d(TAG, "Initialized")

        // Clean up any stale P2P state from previous session
        manager?.cancelConnect(channel, null)
        manager?.removeGroup(channel, null)
        manager?.stopPeerDiscovery(channel, null)

        startTcpServer()
        startDiscoveryLoop()
        startSyncLoop()
    }

    // --- Discovery: find peers every 5s ---

    private fun startDiscoveryLoop() {
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (state != State.CONNECTED) {
                    manager?.discoverPeers(channel, object : ActionListener {
                        override fun onSuccess() {
                            if (state == State.IDLE) state = State.DISCOVERING
                            _isDiscovering.value = true
                            discoveryFailCount = 0
                        }
                        override fun onFailure(reason: Int) {
                            if (reason != 2) {
                                discoveryFailCount++
                                Log.w(TAG, "Discovery failed: $reason (attempt $discoveryFailCount)")
                                // Auto-recovery: if stuck, cancel stale connections and retry
                                if (discoveryFailCount >= 3) {
                                    Log.w(TAG, "Discovery stuck — resetting P2P state")
                                    manager?.cancelConnect(channel, null)
                                    manager?.removeGroup(channel, null)
                                    manager?.stopPeerDiscovery(channel, null)
                                    state = State.IDLE
                                    discoveryFailCount = 0
                                }
                            }
                        }
                    })
                    // If we know peers but aren't connected, try connecting
                    if (_peers.value.isNotEmpty() && state != State.CONNECTING) {
                        connectToPeer(_peers.value.first())
                    }
                }
                delay(5_000)
            }
        }
    }

    // --- Connect to peer ---

    private fun connectToPeer(peer: PeerDevice) {
        if (state == State.CONNECTING || state == State.CONNECTED) return
        state = State.CONNECTING
        Log.d(TAG, "Connecting to ${peer.deviceName} (${peer.deviceAddress})...")

        val config = WifiP2pConfig().apply { deviceAddress = peer.deviceAddress }
        manager?.connect(channel, config, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect request sent to ${peer.deviceName}")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Connect failed: $reason")
                state = State.IDLE // Go back to discovering
            }
        })
    }

    // --- Connection established ---

    private fun onConnected(info: WifiP2pInfo) {
        amGroupOwner = info.isGroupOwner
        groupOwnerIp = info.groupOwnerAddress?.hostAddress
        state = State.CONNECTED
        Log.d(TAG, "CONNECTED! groupOwner=$amGroupOwner ip=$groupOwnerIp")

        // Do immediate first sync
        if (!amGroupOwner && groupOwnerIp != null) {
            scope.launch { doTcpSync(groupOwnerIp!!) }
        }
        // Group owner waits for incoming TCP (server is running)
    }

    // --- TCP Server (always running, accepts sync from client) ---

    private fun startTcpServer() {
        scope.launch {
            while (isActive) {
                try {
                    ServerSocket(SYNC_PORT).use { server ->
                        server.soTimeout = 0
                        server.reuseAddress = true
                        Log.d(TAG, "TCP server on :$SYNC_PORT")
                        while (isActive) {
                            val client = server.accept()
                            client.soTimeout = SOCKET_TIMEOUT
                            Log.d(TAG, "Incoming TCP from ${client.inetAddress}")
                            launch {
                                try { performSync(client) }
                                catch (e: Exception) { Log.w(TAG, "Incoming sync error: ${e.message}") }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) { Log.w(TAG, "Server error: ${e.message}"); delay(2000) }
                }
            }
        }
    }

    // --- Periodic sync: every 3s if connected, use TCP directly ---

    private fun startSyncLoop() {
        scope.launch {
            delay(5_000)
            while (isActive) {
                delay(3_000)
                if (state == State.CONNECTED && !isSyncing && !amGroupOwner) {
                    val ip = groupOwnerIp ?: continue
                    doTcpSync(ip)
                }
            }
        }
    }

    private suspend fun doTcpSync(ip: String) {
        try {
            isSyncing = true
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, SYNC_PORT), SOCKET_TIMEOUT)
            socket.soTimeout = SOCKET_TIMEOUT
            performSync(socket)
        } catch (e: Exception) {
            Log.w(TAG, "TCP sync to $ip failed: ${e.message}")
            // If TCP fails, connection might be dead
            if (state == State.CONNECTED) {
                Log.d(TAG, "Connection seems dead, resetting")
                state = State.IDLE
                groupOwnerIp = null
            }
        } finally {
            isSyncing = false
        }
    }

    // --- Manual sync button ---

    fun broadcastSync() {
        if (state == State.CONNECTED && !amGroupOwner && groupOwnerIp != null) {
            scope.launch { doTcpSync(groupOwnerIp!!) }
        } else {
            // Force rediscovery
            state = State.IDLE
            groupOwnerIp = null
            _syncState.value = _syncState.value.copy(isSyncing = true, error = null)
        }
    }

    // --- Sync protocol ---

    private suspend fun performSync(socket: Socket) {
        val input = BufferedReader(InputStreamReader(socket.inputStream))
        val output = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)

        try {
            // Triage cards
            val localCards = triageCardDao.getAll()
            output.println(json.encodeToString(localCards.map { CardManifestEntry(it.id, it.updatedAt) }))

            val peerManifest = json.decodeFromString<List<CardManifestEntry>>(input.readLine() ?: throw IOException("Disconnected"))
            val peerCardMap = peerManifest.associateBy { it.id }
            val cardsToSend = localCards.filter { l -> val p = peerCardMap[l.id]; p == null || l.updatedAt > p.updatedAt }

            output.println(json.encodeToString(cardsToSend.map { it.toTriageCard() }))
            val peerCards = json.decodeFromString<List<TriageCard>>(input.readLine() ?: throw IOException("Disconnected"))

            var received = 0
            for (card in peerCards) {
                val existing = triageCardDao.getById(card.id)
                if (existing == null || card.updatedAt > existing.updatedAt) {
                    triageCardDao.insert(TriageCardEntity.fromTriageCard(
                        card.copy(syncStatus = card.syncStatus.copy(meshSynced = true, meshSyncedAt = System.currentTimeMillis()))
                    ))
                    received++
                }
            }

            // Radio messages
            val localMsgs = radioMessageDao.getAll()
            output.println(json.encodeToString(localMsgs.map { it.id }))
            val peerMsgIds = json.decodeFromString<List<String>>(input.readLine() ?: throw IOException("Disconnected"))
            output.println(json.encodeToString(localMsgs.filter { it.id !in peerMsgIds }.map { it.toRadioMessage() }))
            val peerMsgs = json.decodeFromString<List<RadioMessage>>(input.readLine() ?: throw IOException("Disconnected"))
            val localMsgIds = localMsgs.map { it.id }.toSet()
            val newMsgs = peerMsgs.filter { it.id !in localMsgIds }
            if (newMsgs.isNotEmpty()) radioMessageDao.insertAll(newMsgs.map { RadioMessageEntity.from(it) })

            // Voice clips
            val localClips = voiceClipDao.getAll()
            output.println(json.encodeToString(localClips.map { it.id }))
            val peerClipIds = json.decodeFromString<List<String>>(input.readLine() ?: throw IOException("Disconnected"))
            output.println(json.encodeToString(localClips.filter { it.id !in peerClipIds }.map { it.toVoiceClip() }))
            val peerClips = json.decodeFromString<List<VoiceClip>>(input.readLine() ?: throw IOException("Disconnected"))
            val localClipIds = localClips.map { it.id }.toSet()
            val newClips = peerClips.filter { it.id !in localClipIds }
            if (newClips.isNotEmpty()) voiceClipDao.insertAll(newClips.map { VoiceClipEntity.from(it) })

            _syncState.value = SyncState(
                connectedPeerCount = _peers.value.size, lastSyncTimestamp = System.currentTimeMillis(),
                cardsSent = cardsToSend.size, cardsReceived = received,
                totalSynced = _syncState.value.totalSynced + cardsToSend.size + received + newMsgs.size + newClips.size,
                isSyncing = false
            )
            Log.d(TAG, "Sync OK: cards=${cardsToSend.size}/${received} msgs=${newMsgs.size} clips=${newClips.size}")
        } finally {
            socket.close()
        }
    }

    fun destroy() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        scope.cancel()
        manager?.removeGroup(channel, null)
        initialized = false
    }

    @kotlinx.serialization.Serializable
    private data class CardManifestEntry(val id: String, val updatedAt: Long)
}
