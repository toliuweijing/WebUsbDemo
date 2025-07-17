### **Plan V3: Direct USB Host Communication**

This plan outlines a complete refactor of the connection logic, adhering strictly to the official Android USB Host API documentation.

#### **1. High-Level Goal**

The primary goal is to establish a reliable connection to a selected USB device by directly managing its interfaces and endpoints. The application will find a suitable `UsbInterface` and `UsbEndpoint`, claim it, and establish a `UsbDeviceConnection`.

#### **2. Architectural Changes**

*   **Remove `usb-serial-for-android` Dependency:** This library is not suitable and will be removed from the project to eliminate the incorrect abstraction layer.
*   **Direct API Usage:** The implementation will be refactored to use only the `android.hardware.usb.*` classes (`UsbManager`, `UsbDevice`, `UsbInterface`, `UsbEndpoint`, `UsbDeviceConnection`).
*   **State Management:** The application state will be updated to hold the core USB objects (`UsbDeviceConnection`, `UsbInterface`, etc.) instead of the `UsbSerialPort`.

#### **3. Detailed Implementation Plan**

**Step 1: Clean Up Project Dependencies**

*   **File:** `app/build.gradle.kts`
*   **Action:** Remove the following dependency implementation line:
    ```kotlin
    // REMOVE THIS LINE
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
    ```

**Step 2: Refactor `MainActivity.kt` for Direct USB API Usage**

*   **File:** `app/src/main/java/com/innosage/cmp/example/webusbdemo/MainActivity.kt`
*   **Action:**
    1.  **Remove Imports:** Delete all `import` statements related to `com.hoho.android.usbserial.*`.
    2.  **Update State Variables:** The `usbSerialPort` state variable will be replaced with variables to hold the essential USB components:
        ```kotlin
        private var usbDeviceConnection: UsbDeviceConnection? by mutableStateOf(null)
        private var usbInterface: UsbInterface? by mutableStateOf(null)
        // Add states for endpoints if needed for data transfer later
        ```

**Step 3: Rewrite Connection and Disconnection Logic**

*   **File:** `app/src/main/java/com/innosage/cmp/example/webusbdemo/MainActivity.kt`
*   **Action:** The `connectToDevice` and `disconnectDevice` functions will be completely rewritten.

*   **New `connectToDevice(device: UsbDevice)` function:**
    1.  Check for permission using `usbManager.hasPermission(device)`. If permission is not granted, request it and `return`, allowing the `BroadcastReceiver` to handle the result.
    2.  **Find a Suitable Interface:** Iterate through the device's interfaces (`for (i in 0 until device.interfaceCount)`). The code will look for an interface with `UsbConstants.USB_CLASS_VENDOR_SPEC` or another appropriate class.
    3.  **Find Communication Endpoints:** Within the chosen interface, iterate through its endpoints (`for (j in 0 until usbInterface.endpointCount)`). The code will look for a pair of `UsbEndpoint`s: one for input (`UsbConstants.USB_DIR_IN`) and one for output (`UsbConstants.USB_DIR_OUT`), typically of type `UsbConstants.USB_ENDPOINT_XFER_BULK`.
    4.  **Open Connection:** Get the `UsbDeviceConnection` by calling `usbManager.openDevice(device)`.
    5.  **Claim Interface:** If a suitable interface and endpoints are found, the app **must** claim it before data transfer can occur: `connection.claimInterface(usbInterface, true)`.
    6.  **Update State:** Store the `usbDeviceConnection` and `usbInterface` in the state variables and update the UI `connectionStatus` to "Connected".

*   **New `disconnectDevice()` function:**
    1.  Release the claimed interface: `usbDeviceConnection?.releaseInterface(usbInterface)`.
    2.  Close the connection: `usbDeviceConnection?.close()`.
    3.  Reset all relevant state variables to `null`.

#### **4. Workflow Diagram**

```mermaid
graph TD
    A[Start] --> B{User clicks "Connect"};
    B --> C{Has Permission?};
    C -- No --> D[Request Permission & Wait];
    C -- Yes --> E[Find Suitable Interface & Endpoints];
    E -- Not Found --> F[Update UI: "Error: Interface not found"];
    E -- Found --> G[usbManager.openDevice()];
    G --> H[connection.claimInterface()];
    H --> I[Store Connection & Interface];
    I --> J[Update UI: "Connected"];
    J --> K{User clicks "Disconnect"};
    K --> L[connection.releaseInterface()];
    L --> M[connection.close()];
    M --> N[Update UI: "Disconnected"];