package com.innosage.cmp.example.webusbdemo

import android.os.Bundle
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
import com.innosage.cmp.example.webusbdemo.ui.theme.WebUsbDemoTheme

class MainActivity : ComponentActivity() {
    // Instance of the new connection manager
    private lateinit var webUsbConnectionManager: WebUsbConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the connection manager
        webUsbConnectionManager = WebUsbConnectionManager(applicationContext)

        setContent {
            WebUsbDemoTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release resources held by the connection manager
        webUsbConnectionManager.release()
    }

    @Composable
    fun MainScreen() {
        // Observe the connection status from the manager
        val connectionStatus by webUsbConnectionManager.connectionStatus
        val discoveredDevices = remember { mutableStateOf<List<android.hardware.usb.UsbDevice>>(emptyList()) }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { discoveredDevices.value = webUsbConnectionManager.discoverUsbDevices() }) {
                    Text("Discover WebUSB Devices")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Status: $connectionStatus")
                Spacer(modifier = Modifier.height(16.dp))

                // Only show device list if not connected
                if (connectionStatus == "Disconnected" || connectionStatus.startsWith("Error")) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(discoveredDevices.value) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { webUsbConnectionManager.connectToDevice(device) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = device.deviceName, modifier = Modifier.weight(1f))
                                Button(onClick = { webUsbConnectionManager.connectToDevice(device) }) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                } else {
                    Button(onClick = { webUsbConnectionManager.disconnectDevice() }) {
                        Text("Disconnect")
                    }
                }
            }
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
