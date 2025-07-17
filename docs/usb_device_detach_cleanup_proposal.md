# Proposal: USB Device Detach Event Handling and Resource Cleanup

This proposal outlines the necessary modifications to `WebUsbConnectionManager.kt` to properly handle USB device detach events, ensuring that resources are cleaned up and the application's state is updated accordingly.

## Current State

Currently, `WebUsbConnectionManager` handles USB permission requests and connection/disconnection initiated by the application. However, it does not explicitly listen for or react to a physical detachment of the USB device from the Android host.

## Proposed Changes to `WebUsbConnectionManager.kt`

To address device detachment, we will introduce a new `BroadcastReceiver` specifically for the `UsbManager.ACTION_USB_DEVICE_DETACHED` intent.

### 1. Add a `BroadcastReceiver` for Detach Events

A new `BroadcastReceiver` will be added to `WebUsbConnectionManager` to listen for `ACTION_USB_DEVICE_DETACHED`.

```kotlin
// In WebUsbConnectionManager.kt

// ... existing code ...

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
            if (detachedDevice != null && usbDeviceConnection?.device?.deviceName == detachedDevice.deviceName) {
                Log.d("WebUsbConnectionManager", "Detected detachment of connected device: ${detachedDevice.deviceName}")
                disconnectDevice() // Call the existing disconnect logic
                connectionStatus.value = "Disconnected: Device detached"
            } else {
                Log.d("WebUsbConnectionManager", "A device detached: ${detachedDevice?.deviceName ?: "Unknown"}, but not the connected one.")
            }
        }
    }
}
```

### 2. Register and Unregister the Detach Receiver

The `usbDetachReceiver` needs to be registered when the `WebUsbConnectionManager` is initialized and unregistered when it is released to prevent memory leaks.

```kotlin
// In WebUsbConnectionManager.kt

// ... existing code ...

init {
    // Register the permission receiver
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
 * Releases resources held by the manager, specifically unregistering BroadcastReceivers.
 * This should be called when the manager is no longer needed (e.g., in Activity's onDestroy).
 */
fun release() {
    context.unregisterReceiver(usbPermissionReceiver)
    context.unregisterReceiver(usbDetachReceiver) // Unregister the detach receiver
    disconnectDevice() // Ensure device is disconnected on release
}
```

### 3. Update `disconnectDevice()` (No Change Needed, but Confirm Purpose)

The existing `disconnectDevice()` function already handles releasing the interface and closing the connection. This function will be reused by the `usbDetachReceiver`.

```kotlin
// In WebUsbConnectionManager.kt

/**
 * Disconnects from the currently connected USB device, if any.
 * Releases the interface and closes the connection.
 */
fun disconnectDevice() {
    usbDeviceConnection?.releaseInterface(usbInterface)
    usbDeviceConnection?.close()
    usbDeviceConnection = null
    usbInterface = null
    connectionStatus.value = "Disconnected" // Ensure status is updated
}
```

## Benefits

*   **Robustness**: The application will now gracefully handle unexpected device disconnections, preventing potential crashes or stale connection states.
*   **User Experience**: The UI can immediately reflect the disconnected state, providing clear feedback to the user.
*   **Resource Management**: Ensures that USB resources (interfaces, connections) are properly released even if the device is physically removed.

## Next Steps

Upon your approval, I will proceed to implement these changes in `app/src/main/java/com/innosage/cmp/example/webusbdemo/WebUsbConnectionManager.kt`.