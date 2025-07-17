# Android USB Host API Summary for Communication

This document summarizes the key Android USB Host APIs within the `android.hardware.usb` package, which are essential for interacting with USB devices when an Android device acts as a host.

## Key Classes and Their Purposes

1.  **[`UsbManager`](/reference/android/hardware/usb/UsbManager)**
    *   **Purpose**: The central class for enumerating and communicating with connected USB devices. It's your entry point to the USB host system.
    *   **Key Methods**:
        *   [`getDeviceList()`](/reference/android/hardware/usb/UsbManager#getDeviceList()): Returns a `HashMap` of all currently connected USB devices, keyed by their device names.
        *   [`requestPermission(UsbDevice device, PendingIntent pi)`](/reference/android/hardware/usb/UsbManager#requestPermission(android.hardware.usb.UsbAccessory, android.app.PendingIntent)): Prompts the user for permission to access a specific `UsbDevice`. This is crucial for security and user consent.
        *   [`hasPermission(UsbDevice device)`](/reference/android/hardware/usb/UsbManager#hasPermission(android.hardware.usb.UsbDevice)): Checks if your application already has permission to access a given `UsbDevice`.
        *   [`openDevice(UsbDevice device)`](/reference/android/hardware/usb/UsbManager#openDevice(android.hardware.usb.UsbDevice)): Opens a [`UsbDeviceConnection`](/reference/android/hardware/usb/UsbDeviceConnection) to the specified `UsbDevice`, allowing for data transfer.

2.  **[`UsbDevice`](/reference/android/hardware/usb/UsbDevice)**
    *   **Purpose**: Represents a single connected USB device. It provides methods to retrieve identifying information about the device and to discover its interfaces and endpoints.
    *   **Key Methods**:
        *   `getInterfaceCount()`: Returns the total number of USB interfaces supported by the device.
        *   `getInterface(int index)`: Retrieves a specific [`UsbInterface`](/reference/android/hardware/usb/UsbInterface) by its index.
        *   `getVendorId()`, `getProductId()`, `getDeviceClass()`, `getDeviceSubclass()`, `getDeviceProtocol()`, `getDeviceName()`: Methods to access various identifying attributes of the device.

3.  **[`UsbInterface`](/reference/android/hardware/usb/UsbInterface)**
    *   **Purpose**: Represents a specific functional interface of a USB device. A single USB device can expose multiple interfaces, each defining a different set of functionalities (e.g., a composite device might have a keyboard interface and a mouse interface).
    *   **Key Methods**:
        *   `getEndpointCount()`: Returns the number of communication endpoints associated with this interface.
        *   `getEndpoint(int index)`: Retrieves a specific [`UsbEndpoint`](/reference/android/hardware/usb/UsbEndpoint) by its index.
        *   `getInterfaceClass()`, `getInterfaceSubclass()`, `getInterfaceProtocol()`: Access descriptors that define the type of functionality the interface provides (e.g., HID, Mass Storage, Vendor Specific).

4.  **[`UsbEndpoint`](/reference/android/hardware/usb/UsbEndpoint)**
    *   **Purpose**: Represents a communication channel (pipe) within a `UsbInterface`. Endpoints are used for actual data transfer. An interface typically has at least one IN (input) and one OUT (output) endpoint for two-way communication.
    *   **Key Attributes (accessed via getters)**:
        *   `getDirection()`: Indicates the direction of data flow (`UsbConstants.USB_DIR_IN` for device-to-host, `UsbConstants.USB_DIR_OUT` for host-to-device).
        *   `getType()`: Specifies the transfer type (e.g., `UsbConstants.USB_ENDPOINT_XFER_BULK` for bulk transfers, `USB_ENDPOINT_XFER_INT` for interrupt, `USB_ENDPOINT_XFER_CONTROL` for control).
        *   `getMaxPacketSize()`: The maximum number of bytes that can be transferred in a single transaction.

5.  **[`UsbDeviceConnection`](/reference/android/hardware/usb/UsbDeviceConnection)**
    *   **Purpose**: Represents an active, open connection to a USB device. This is the class through which all data transfers occur.
    *   **Key Methods**:
        *   [`claimInterface(UsbInterface intf, boolean force)`](/reference/android/hardware/usb/UsbDeviceConnection#claimInterface(android.hardware.usb.UsbInterface, boolean)): Claims exclusive access to a `UsbInterface`. You must claim an interface before you can perform I/O operations on its endpoints. `force` indicates whether to force-claim if another application already holds it.
        *   [`releaseInterface(UsbInterface intf)`](/reference/android/hardware/usb/UsbDeviceConnection#releaseInterface(android.hardware.usb.UsbInterface)): Releases a previously claimed `UsbInterface`.
        *   [`bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int length, int timeout)`](/reference/android/hardware/usb/UsbDeviceConnection#bulkTransfer(android.hardware.usb.UsbEndpoint, byte[], int, int)): Performs a synchronous bulk data transfer. **Important**: This is a blocking call and should always be executed on a background thread to prevent blocking the UI.
        *   [`controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout)`](/reference/android/hardware/usb/UsbDeviceConnection#controlTransfer(int, int, int, int, byte[], int, int)): Performs a control transfer, typically used for device configuration or status requests.
        *   [`close()`](/reference/android/hardware/usb/UsbDeviceConnection#close()): Closes the connection to the device and releases all associated resources.

6.  **[`UsbRequest`](/reference/android/hardware/usb/UsbRequest)**
    *   **Purpose**: Used for performing asynchronous data transfers. This is more advanced and allows for non-blocking I/O operations.
    *   **Key Methods**:
        *   [`initialize(UsbDeviceConnection connection, UsbEndpoint endpoint)`](/reference/android/hardware/usb/UsbRequest#initialize(android.hardware.usb.UsbDeviceConnection, android.hardware.usb.UsbEndpoint)): Initializes the request with a connection and endpoint.
        *   [`queue(ByteBuffer buffer)`](/reference/android/hardware/usb/UsbRequest#queue(java.nio.ByteBuffer)): Queues the request for asynchronous transfer.
        *   [`requestWait()`](/reference/android/hardware/usb/UsbDeviceConnection#requestWait()) (on `UsbDeviceConnection`): Waits for the result of a previously queued asynchronous request.

## Typical USB Host Communication Flow

1.  **Discover Devices**: Obtain a `UsbManager` instance and use `getDeviceList()` to get a `HashMap` of connected `UsbDevice` objects.
2.  **Request Permission**: If your app doesn't have automatic permission (e.g., via manifest filtering), call `usbManager.requestPermission()` for the desired `UsbDevice`. Handle the permission result in a `BroadcastReceiver`.
3.  **Identify Interface and Endpoint**: Iterate through the `UsbDevice`'s interfaces (`getInterfaceCount()`, `getInterface()`) to find the one relevant to your device's functionality (e.g., by `interfaceClass`, `vendorId`, `productId`). Then, iterate through the endpoints of that interface (`getEndpointCount()`, `getEndpoint()`) to find the appropriate IN and OUT endpoints for data transfer.
4.  **Open Connection and Claim Interface**:
    *   Call `usbManager.openDevice(device)` to get a `UsbDeviceConnection`.
    *   Call `connection.claimInterface(foundInterface, true)` to gain exclusive access to the interface.
5.  **Transfer Data**:
    *   For synchronous transfers, use `connection.bulkTransfer()` or `connection.controlTransfer()`. **Always perform these on a background thread.**
    *   For asynchronous transfers, use `UsbRequest` to `initialize()` and `queue()` requests, then use `connection.requestWait()` to retrieve completed requests.
6.  **Terminate Communication**: When done, or if the device is detached, call `connection.releaseInterface(usbInterface)` and `connection.close()` to release resources. You can also register a `BroadcastReceiver` for `UsbManager.ACTION_USB_DEVICE_DETACHED` to handle unexpected disconnections.