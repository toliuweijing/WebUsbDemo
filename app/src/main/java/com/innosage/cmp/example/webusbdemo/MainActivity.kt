package com.innosage.cmp.example.webusbdemo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.innosage.cmp.example.webusbdemo.ui.theme.WebUsbDemoTheme
import java.io.IOException

private const val ACTION_USB_PERMISSION = "com.innosage.cmp.example.webusbdemo.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? by mutableStateOf(null)

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
                        Log.d("MainActivity", "Permission denied for device $device")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        setContent {
            WebUsbDemoTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(usbPermissionReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
    }

    @Composable
    fun MainScreen() {
        val discoveredDevices = remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
        val connectionStatus = remember { mutableStateOf("Disconnected") }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { discoveredDevices.value = discoverUsbDevices() }) {
                    Text("Discover WebUSB Devices")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Status: ${connectionStatus.value}")
                Spacer(modifier = Modifier.height(16.dp))

                if (usbSerialPort == null) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(discoveredDevices.value) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { connectToDevice(device) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = device.deviceName, modifier = Modifier.weight(1f))
                                Button(onClick = { connectToDevice(device) }) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                } else {
                    Button(onClick = { disconnectDevice() }) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }

    private fun discoverUsbDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.toList()
    }

    private fun connectToDevice(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        )
        usbManager.requestPermission(device, permissionIntent)

        if (usbManager.hasPermission(device)) {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val driver = drivers.firstOrNull { it.device == device }
            if (driver != null) {
                val connection = usbManager.openDevice(driver.device)
                if (connection != null) {
                    val port = driver.ports[0]
                    try {
                        port.open(connection)
                        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                        usbSerialPort = port
                    } catch (e: IOException) {
                        Log.e("MainActivity", "Error opening port", e)
                        disconnectDevice()
                    }
                }
            }
        }
    }

    private fun disconnectDevice() {
        try {
            usbSerialPort?.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error closing port", e)
        } finally {
            usbSerialPort = null
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WebUsbDemoTheme {
        // This is a simplified preview and won't reflect the full state logic
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { /* Preview */ }) {
                Text("Discover WebUSB Devices")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Status: Disconnected")
        }
    }
}
