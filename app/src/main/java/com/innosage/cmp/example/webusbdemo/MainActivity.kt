package com.innosage.cmp.example.webusbdemo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.innosage.cmp.example.webusbdemo.ui.theme.WebUsbDemoTheme
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

private const val ACTION_USB_PERMISSION = "com.innosage.cmp.example.webusbdemo.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbPort: UsbSerialPort? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        setContent {
            WebUsbDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var discoveredDevices by remember { mutableStateOf("No devices discovered.") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(onClick = {
                            discoveredDevices = discoverUsbDevices()
                        }) {
                            Text("Discover WebUSB Devices")
                        }
                        Text(text = discoveredDevices, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }

    private fun discoverUsbDevices(): String {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            return "No USB devices found."
        }

        val stringBuilder = StringBuilder("Discovered Devices:\n")
        for ((_, device) in deviceList) {
            stringBuilder.append("Device Name: ${device.deviceName}\n")
            stringBuilder.append("Vendor ID: ${device.vendorId}, Product ID: ${device.productId}\n")
            stringBuilder.append("Class: ${device.deviceClass}, Subclass: ${device.deviceSubclass}\n")
            stringBuilder.append("Protocol: ${device.deviceProtocol}\n")

            // Check if the device is a WebUSB device (vendor-specific class)
            if (device.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                stringBuilder.append("  (Potentially WebUSB compatible)\n")
            }

            // Attempt to find a serial driver for the device
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val usbSerialDriver: UsbSerialDriver? = drivers.firstOrNull { it.device == device }

            if (usbSerialDriver != null) {
                stringBuilder.append("  Serial Driver Found: ${usbSerialDriver.javaClass.simpleName}\n")
                usbPort = usbSerialDriver.ports.firstOrNull()
                if (usbPort != null) {
                    stringBuilder.append("  Port found: ${usbPort!!.portNumber}\n")
                    requestUsbPermission(device)
                }
            } else {
                stringBuilder.append("  No Serial Driver Found.\n")
            }
            stringBuilder.append("\n")
        }
        return stringBuilder.toString()
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    // You would typically handle the permission result in a BroadcastReceiver
    // For simplicity, this example omits the BroadcastReceiver and assumes permission is granted.
    // In a real application, you would register a BroadcastReceiver to listen for ACTION_USB_PERMISSION.
    // And then open the device/port if permission is granted.

    override fun onDestroy() {
        super.onDestroy()
        try {
            usbPort?.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error closing USB port: ${e.message}")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WebUsbDemoTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Hello Android!")
            Button(onClick = { /* Do nothing for preview */ }) {
                Text("Discover WebUSB Devices")
            }
        }
    }
}
