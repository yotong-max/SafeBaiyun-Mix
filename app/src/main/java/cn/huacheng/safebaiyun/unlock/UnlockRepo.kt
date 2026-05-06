package cn.huacheng.safebaiyun.unlock

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import cn.huacheng.safebaiyun.util.ContextHolder
import cn.huacheng.safebaiyun.util.LockBiz
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


@SuppressLint("MissingPermission")
object UnlockRepo {

    private const val MAGIC_SERVICE = "14839ac4-7d7e-415c-9a42-167340cf2339"

    private lateinit var gatt: BluetoothGatt

    private lateinit var readableCharacteristic: BluetoothGattCharacteristic

    private lateinit var writeableCharacteristic: BluetoothGattCharacteristic

    private var autoDisconnectJob: Job? = null

    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow

    /**
     * 解锁指定门禁
     * @param mac 门禁蓝牙 MAC 地址
     * @param key 门禁加密密钥
     */
    fun unlock(mac: String, key: String) {
        val bluetoothAdapter =
            (ContextHolder.get()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            showToast("Mac地址格式错误")
            return
        }
        connect(bluetoothAdapter, mac, key)

        autoDisconnectJob = GlobalScope.launch {
            delay(10000)
            if (isActive) {
                log("10s超时，自动断开链接")
                gatt.disconnect()
                gatt.close()
            }
        }
    }

    /**
     * 兼容旧接口：解锁第一个门禁
     */
    fun unlock() {
        val (mac, key) = DataRepo.readData()
        unlock(mac, key)
    }

    private fun connect(bluetoothAdapter: BluetoothAdapter, mac: String, key: String) {
        val remoteDevice = bluetoothAdapter.getRemoteDevice(mac)
        // 将 config 存为成员供 callback 使用
        _pendingConfig = mac to key
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(
                ContextHolder.get(),
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            remoteDevice.connectGatt(
                ContextHolder.get(),
                false,
                callback
            )
        }

        log("尝试连接蓝牙 ${this::gatt.isInitialized}")
    }

    /** 待连接门禁的 mac-key 对（callback 内部读取） */
    private var _pendingConfig: Pair<String, String> = "" to ""

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            log("连接状态改变 status$status,newState$newState")
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                autoDisconnectJob?.cancel()
                log("开始搜索服务")
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            log("搜索服务成功 $status")
            log("搜索到以下服务：${gatt?.services?.map { it.uuid }?.joinToString(",")}")
            handleService(gatt?.services?.find { it.uuid.toString() == MAGIC_SERVICE })
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            //android13以上走这里
            log("特征码读取回调 $status,${value.size}")
            handleCharacteristicWrite(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            //android12及以下走这里
            val value = characteristic?.value ?: return
            log("特征码读取回调 $status,${value.size}")
            handleCharacteristicWrite(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            log("特征码写入回调")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                showToast("开门成功")
            } else {
                showToast("密钥写入失败")
            }
            gatt?.close()
        }
    }

    /**
     * 找到对应的characteristic
     */
    private fun handleService(service: BluetoothGattService?) {

        if (service == null) {
            return
        }

        log("开始处理服务，共${service.characteristics.size}个特征")
        val propCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

        service.characteristics?.forEach {
            log("特征${it.uuid},prop:${it.properties}")
            val properties = it.properties
            if ((properties and 2) != 0) {
                readableCharacteristic = it
            }
            if ((properties and 8) != 0) {
                writeableCharacteristic = it
            }
            if ((properties and 16) != 0) {
                propCharacteristics.add(it)
            }
            if ((properties and 32) != 0) {
                propCharacteristics.add(it)
            }
        }

        handleCharacteristics(propCharacteristics)
    }

    private fun handleCharacteristics(propCharacteristics: MutableList<BluetoothGattCharacteristic>) {
        log("开始处理特征,写入对应数据")
        propCharacteristics.forEach { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.descriptors.forEach {
                if ((characteristic.properties and 16) != 0) {
                    characteristic.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else if ((characteristic.properties and 32) != 0) {
                    characteristic.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                }

                gatt.writeDescriptor(it)
            }
        }

        val result = gatt.readCharacteristic(readableCharacteristic)
        log("特征写入结果 $result")
    }

    private fun handleCharacteristicWrite(value: ByteArray) {
        val (mac, key) = _pendingConfig
        log("开始写入密钥")
        val encrypted = LockBiz.encryptData(value, LockBiz.hexToByteArray(mac), key)
        log(encrypted.joinToString())
        writeableCharacteristic.setValue(encrypted)
        writeableCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = gatt.writeCharacteristic(writeableCharacteristic)
        log("密钥写入结果 $result")

    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun log(msg: String) {
        println(msg)
        _logFlow.value = _logFlow.value + msg
    }
}
