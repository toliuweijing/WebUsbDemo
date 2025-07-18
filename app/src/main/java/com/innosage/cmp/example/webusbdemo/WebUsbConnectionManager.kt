package com.innosage.cmp.example.webusbdemo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Action string for USB permission requests
private const val ACTION_USB_PERMISSION = "com.innosage.cmp.example.webusbdemo.USB_PERMISSION"

/**
 * Manages USB device discovery, connection, and communication.
 * This class encapsulates all logic related to the Android USB Host API,
 * adhering to the Single Responsibility Principle.
 *
 * @param context The application context, typically from the Activity.
 */
class WebUsbConnectionManager(private val context: Context) {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Observable state for the current connection status, allowing UI to react.
    var connectionStatus: MutableState<String> = mutableStateOf("Disconnected")
        private set // Only this class can modify the status directly

    // Internal state for the active USB connection and interface
    private var connectedUsbDevice: UsbDevice? by mutableStateOf(null)
    private var usbDeviceConnection: UsbDeviceConnection? by mutableStateOf(null)
    private var usbInterface: UsbInterface? by mutableStateOf(null)

    // Observable state for current connection status (true if connected, false otherwise)
    var isConnected: MutableState<Boolean> = mutableStateOf(false)
        private set

    // BroadcastReceiver to handle USB permission results
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { connectToDevice(it) }
                    } else {
                        connectionStatus.value = "Permission denied for device ${device?.deviceName}"
                        Log.d("WebUsbConnectionManager", "Permission denied for device $device")
                    }
                }
            }
        }
    }

    // BroadcastReceiver to handle USB device detachment
    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val detachedDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                // Check if the detached device is the one we are currently connected to
                if (detachedDevice != null && connectedUsbDevice?.deviceName == detachedDevice.deviceName) {
                    Log.d("WebUsbConnectionManager", "Detected detachment of connected device: ${detachedDevice.deviceName}")
                    disconnectDevice() // Call the existing disconnect logic
                    connectionStatus.value = "Disconnected: Device detached"
                } else {
                    Log.d("WebUsbConnectionManager", "A device detached: ${detachedDevice?.deviceName ?: "Unknown"}, but not the connected one.")
                }
            }
        }
    }

    init {
        // Register the permission receiver when the manager is initialized
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbPermissionReceiver, permissionFilter)
        }

        // Register the detach receiver
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbDetachReceiver, detachFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbDetachReceiver, detachFilter)
        }
    }

    /**
     * Discovers all currently connected USB devices.
     * @return A list of UsbDevice objects.
     */
    fun discoverUsbDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.toList()
    }

    /**
     * Attempts to connect to a given UsbDevice.
     * Handles permission requests and interface claiming.
     *
     * @param device The UsbDevice to connect to.
     */
    fun connectToDevice(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
            )
            usbManager.requestPermission(device, permissionIntent)
            return
        }

        var foundInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            // For WebUSB, the class is often vendor-specific.
            // You might need to adjust this check based on your device's descriptors.
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                foundInterface = iface
                break
            }
        }

        if (foundInterface == null) {
            connectionStatus.value = "Error: No suitable interface found."
            Log.e("WebUsbConnectionManager", "Could not find a suitable interface for the device.")
            return
        }

        this.usbInterface = foundInterface
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            connectionStatus.value = "Error: Could not open device."
            Log.e("WebUsbConnectionManager", "usbManager.openDevice() returned null.")
            return
        }

        if (connection.claimInterface(foundInterface, true)) {
            this.usbDeviceConnection = connection
            this.connectedUsbDevice = device
            connectionStatus.value = "Connected to ${device.deviceName}"
            isConnected.value = true
        } else {
            connectionStatus.value = "Error: Could not claim interface."
            Log.e("WebUsbConnectionManager", "claimInterface() returned false.")
            connection.close()
        }
    }

    /**
     * Disconnects from the currently connected USB device, if any.
     * Releases the interface and closes the connection.
     */
    fun disconnectDevice() {
        usbDeviceConnection?.releaseInterface(usbInterface)
        usbDeviceConnection?.close()
        usbDeviceConnection = null
        usbInterface = null
        connectedUsbDevice = null
        connectionStatus.value = "Disconnected"
        isConnected.value = false
    }

    /**
     * Releases resources held by the manager, specifically unregistering the BroadcastReceiver.
     * This should be called when the manager is no longer needed (e.g., in Activity's onDestroy).
     */
    fun release() {
        context.unregisterReceiver(usbPermissionReceiver)
        context.unregisterReceiver(usbDetachReceiver) // Unregister the detach receiver
        disconnectDevice() // Ensure device is disconnected on release
    }
}