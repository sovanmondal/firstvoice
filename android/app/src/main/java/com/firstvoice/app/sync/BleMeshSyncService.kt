package com.firstvoice.app.sync

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.firstvoice.app.data.local.dao.RadioMessageDao
import com.firstvoice.app.data.local.dao.TriageCardDao
import com.firstvoice.app.data.local.entity.RadioMessageEntity
import com.firstvoice.app.data.local.entity.TriageCardEntity
import com.firstvoice.app.data.model.RadioMessage
import com.firstvoice.app.data.model.TriageCard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * BLE-only mesh sync. No WiFi-Direct, no pairing, no invitation dialogs.
 *
 * Every device:
 * - Advertises a BLE service (so others can find it)
 * - Scans for that service (to find others)
 * - Runs a GATT server (to accept incoming sync requests)
 * - Periodically connects as GATT client to discovered peers to push data
 *
 * Sync protocol over BLE GATT:
 * 1. Client writes its manifest (card IDs + timestamps) to SYNC_REQUEST characteristic
 * 2. Server reads manifest, computes diff, writes missing cards to SYNC_RESPONSE
 * 3. Client reads SYNC_RESPONSE and stores new cards
 * 4. Reverse: server also gets client's new cards from the request
 *
 * Data is JSON, chunked into 512-byte BLE writes.
 */
@SuppressLint("MissingPermission")
class BleMeshSyncService(
    private val context: Context,
    private val triageCardDao: TriageCardDao,
    private val radioMessageDao: RadioMessageDao,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "BLESync"
        val SERVICE_UUID: UUID = UUID.fromString("0000FE01-0000-1000-8000-00805F9B34FB")
        val SYNC_WRITE_UUID: UUID = UUID.fromString("0000FE02-0000-1000-8000-00805F9B34FB")
        val SYNC_READ_UUID: UUID = UUID.fromString("0000FE03-0000-1000-8000-00805F9B34FB")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var initialized = false

    @Volatile private var isSyncingNow = false
    private var lastSyncHash = 0L

    // Buffer for incoming GATT writes (chunked data)
    private var incomingBuffer = StringBuilder()
    private var responseData: ByteArray = ByteArray(0)

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    data class PeerDevice(val deviceName: String, val deviceAddress: String, val isGroupOwner: Boolean = false)
    data class SyncState(
        val connectedPeerCount: Int = 0,
        val lastSyncTimestamp: Long? = null,
        val cardsSent: Int = 0,
        val cardsReceived: Int = 0,
        val totalSynced: Int = 0,
        val isSyncing: Boolean = false,
        val error: String? = null
    )

    @kotlinx.serialization.Serializable
    data class SyncPayload(
        val cards: List<TriageCard> = emptyList(),
        val messages: List<RadioMessage> = emptyList(),
        val cardManifest: List<ManifestEntry> = emptyList(),
        val msgIds: List<String> = emptyList()
    )

    @kotlinx.serialization.Serializable
    data class ManifestEntry(val id: String, val updatedAt: Long)

    fun initialize() {
        if (initialized) return
        initialized = true

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        bluetoothAdapter = bm.adapter
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not enabled")
            return
        }

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        scanner = bluetoothAdapter?.bluetoothLeScanner

        startGattServer(bm)
        startAdvertising()
        startScanning()
        startPeriodicSync()
        Log.d(TAG, "Initialized — BLE advertise + scan + GATT server")
    }

    // --- GATT Server (accepts incoming sync) ---

    private fun startGattServer(bm: BluetoothManager) {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeChar = BluetoothGattCharacteristic(
            SYNC_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val readChar = BluetoothGattCharacteristic(
            SYNC_READ_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(writeChar)
        service.addCharacteristic(readChar)

        gattServer = bm.openGattServer(context, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                Log.d(TAG, "GATT server: ${device.address} state=$newState")
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                val chunk = String(value)
                if (chunk == "END") {
                    // Full payload received — process it
                    scope.launch {
                        val response = processIncomingSync(incomingBuffer.toString())
                        responseData = response.toByteArray()
                        incomingBuffer.clear()
                    }
                    if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                } else {
                    incomingBuffer.append(chunk)
                    if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
            ) {
                val chunk = if (offset < responseData.size) {
                    responseData.copyOfRange(offset, minOf(offset + 512, responseData.size))
                } else {
                    "END".toByteArray()
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            }
        })

        gattServer?.addService(service)
    }

    private suspend fun processIncomingSync(payloadJson: String): String {
        return try {
            val incoming = json.decodeFromString<SyncPayload>(payloadJson)

            // Apply incoming cards
            var received = 0
            for (card in incoming.cards) {
                val existing = triageCardDao.getById(card.id)
                if (existing == null || card.updatedAt > existing.updatedAt) {
                    triageCardDao.insert(TriageCardEntity.fromTriageCard(
                        card.copy(syncStatus = card.syncStatus.copy(meshSynced = true, meshSyncedAt = System.currentTimeMillis()))
                    ))
                    received++
                }
            }

            // Apply incoming messages
            val localMsgIds = radioMessageDao.getAll().map { it.id }.toSet()
            val newMsgs = incoming.messages.filter { it.id !in localMsgIds }
            if (newMsgs.isNotEmpty()) {
                radioMessageDao.insertAll(newMsgs.map { RadioMessageEntity.from(it) })
            }

            // Build response with our data that peer needs
            val localCards = triageCardDao.getAll()
            val peerCardMap = incoming.cardManifest.associateBy { it.id }
            val cardsToSend = localCards.filter { local ->
                val p = peerCardMap[local.id]
                p == null || local.updatedAt > p.updatedAt
            }.map { it.toTriageCard() }

            val localMsgs = radioMessageDao.getAll()
            val msgsToSend = localMsgs.filter { it.id !in incoming.msgIds }.map { it.toRadioMessage() }

            _syncState.value = SyncState(
                connectedPeerCount = _peers.value.size,
                lastSyncTimestamp = System.currentTimeMillis(),
                cardsSent = cardsToSend.size,
                cardsReceived = received,
                totalSynced = _syncState.value.totalSynced + cardsToSend.size + received,
                isSyncing = false
            )

            Log.d(TAG, "Server sync: received=${incoming.cards.size} cards, ${incoming.messages.size} msgs. Sending ${cardsToSend.size} cards, ${msgsToSend.size} msgs")

            json.encodeToString(SyncPayload(cards = cardsToSend, messages = msgsToSend))
        } catch (e: Exception) {
            Log.e(TAG, "Process sync error: ${e.message}")
            json.encodeToString(SyncPayload())
        }
    }

    // --- BLE Advertising ---

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { Log.d(TAG, "Advertising started") }
        override fun onStartFailure(errorCode: Int) { Log.e(TAG, "Advertise failed: $errorCode") }
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true) // Must be connectable for GATT
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false) // Save space
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    // --- BLE Scanning ---

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.device.name ?: "Device-${addr.takeLast(5)}"
            knownPeers[addr] = System.currentTimeMillis()
            val peerList = knownPeers.map { (a, _) -> PeerDevice(name, a) }
            if (peerList.size != _peers.value.size) {
                _peers.value = peerList
                _syncState.value = _syncState.value.copy(connectedPeerCount = peerList.size)
                Log.d(TAG, "Peers: ${peerList.size}")
            }
        }
    }

    private val knownPeers = mutableMapOf<String, Long>()

    private fun startScanning() {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
        _isDiscovering.value = true
        Log.d(TAG, "Scanning started")
    }

    // --- Periodic GATT Client Sync ---

    private fun startPeriodicSync() {
        scope.launch {
            delay(5_000)
            while (isActive) {
                delay(3_000)
                if (isSyncingNow || knownPeers.isEmpty()) continue

                val cards = triageCardDao.getAll()
                val msgs = radioMessageDao.getAll()
                val hash = cards.sumOf { it.updatedAt } + msgs.size.toLong()
                if (hash == lastSyncHash) continue

                for ((addr, _) in knownPeers.toMap()) {
                    if (isSyncingNow) break
                    syncWithPeer(addr, cards, msgs)
                }
            }
        }
    }

    private suspend fun syncWithPeer(
        address: String,
        localCards: List<TriageCardEntity>,
        localMsgs: List<RadioMessageEntity>
    ) {
        isSyncingNow = true
        _syncState.value = _syncState.value.copy(isSyncing = true)

        try {
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
            val payload = SyncPayload(
                cards = localCards.map { it.toTriageCard() },
                messages = localMsgs.map { it.toRadioMessage() },
                cardManifest = localCards.map { ManifestEntry(it.id, it.updatedAt) },
                msgIds = localMsgs.map { it.id }
            )
            val payloadBytes = json.encodeToString(payload).toByteArray()

            val result = CompletableDeferred<String>()

            val gattCallback = object : BluetoothGattCallback() {
                private var responseBuffer = StringBuilder()

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "GATT connected to $address, discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (!result.isCompleted) result.completeExceptionally(Exception("Disconnected"))
                        gatt.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service == null) {
                        result.completeExceptionally(Exception("Service not found"))
                        gatt.disconnect()
                        return
                    }

                    // Write payload in chunks
                    val writeChar = service.getCharacteristic(SYNC_WRITE_UUID)
                    scope.launch {
                        var offset = 0
                        while (offset < payloadBytes.size) {
                            val end = minOf(offset + 512, payloadBytes.size)
                            writeChar.value = payloadBytes.copyOfRange(offset, end)
                            gatt.writeCharacteristic(writeChar)
                            delay(50) // Wait for BLE write
                            offset = end
                        }
                        // Signal end
                        writeChar.value = "END".toByteArray()
                        gatt.writeCharacteristic(writeChar)
                        delay(500) // Wait for server to process

                        // Read response
                        val readChar = service.getCharacteristic(SYNC_READ_UUID)
                        gatt.readCharacteristic(readChar)
                    }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    val chunk = String(characteristic.value ?: ByteArray(0))
                    if (chunk == "END" || chunk.isEmpty()) {
                        result.complete(responseBuffer.toString())
                        gatt.disconnect()
                    } else {
                        responseBuffer.append(chunk)
                        // Read next chunk
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }

            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

            val responseJson = withTimeout(15_000) { result.await() }

            // Process response
            val response = json.decodeFromString<SyncPayload>(responseJson)
            var received = 0
            for (card in response.cards) {
                val existing = triageCardDao.getById(card.id)
                if (existing == null || card.updatedAt > existing.updatedAt) {
                    triageCardDao.insert(TriageCardEntity.fromTriageCard(
                        card.copy(syncStatus = card.syncStatus.copy(meshSynced = true, meshSyncedAt = System.currentTimeMillis()))
                    ))
                    received++
                }
            }
            val localMsgIds = localMsgs.map { it.id }.toSet()
            val newMsgs = response.messages.filter { it.id !in localMsgIds }
            if (newMsgs.isNotEmpty()) {
                radioMessageDao.insertAll(newMsgs.map { RadioMessageEntity.from(it) })
            }

            val updatedCards = triageCardDao.getAll()
            val updatedMsgs = radioMessageDao.getAll()
            lastSyncHash = updatedCards.sumOf { it.updatedAt } + updatedMsgs.size.toLong()

            _syncState.value = SyncState(
                connectedPeerCount = knownPeers.size,
                lastSyncTimestamp = System.currentTimeMillis(),
                cardsSent = localCards.size,
                cardsReceived = received,
                totalSynced = _syncState.value.totalSynced + received,
                isSyncing = false
            )
            Log.d(TAG, "Client sync with $address: received $received cards, ${newMsgs.size} msgs")

        } catch (e: Exception) {
            Log.w(TAG, "Sync with $address failed: ${e.message}")
            _syncState.value = _syncState.value.copy(isSyncing = false)
        } finally {
            isSyncingNow = false
        }
    }

    fun broadcastSync() {
        lastSyncHash = 0 // Force re-sync
        _syncState.value = _syncState.value.copy(isSyncing = true, error = null)
    }

    fun destroy() {
        advertiser?.stopAdvertising(advertiseCallback)
        scanner?.stopScan(scanCallback)
        gattServer?.close()
        scope.cancel()
        initialized = false
    }
}
