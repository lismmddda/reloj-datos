package com.example.a2025s

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    // === UI ===
    private lateinit var botonConectar: Button
    private lateinit var textInfo: TextView
    private lateinit var textDatos: TextView
    private lateinit var textDetalles: TextView

    // === Variables ===
    private var activityContext: Context? = null
    private var deviceConnected = false
    private lateinit var nodeID: String
    private lateinit var nodeName: String
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        activityContext = this
        botonConectar = findViewById(R.id.boton)
        textInfo = findViewById(R.id.textinfo)
        textDatos = findViewById(R.id.textDatos)
        textDetalles = findViewById(R.id.textDetalles)

        botonConectar.setOnClickListener {
            if (!deviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                getNodes(tempAct)
            } else {
                textInfo.text = " Ya estás conectado al reloj"
            }
        }
    }

    // === Conexión con el reloj físico ===
    private fun getNodes(context: Context) {
        launch(Dispatchers.IO) {
            val nodeList = Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                if (nodes.isNotEmpty()) {
                    val nodo = nodes.first()
                    nodeID = nodo.id
                    nodeName = nodo.displayName
                    deviceConnected = true

                    Log.d("Nodo", "Conectado al reloj físico: $nodeName ($nodeID)")

                    withContext(Dispatchers.Main) {
                        textInfo.text = "Conectado al reloj "
                        textDetalles.text = "Reloj: $nodeName\n ID: $nodeID"
                    }
                } else {
                    deviceConnected = false
                    withContext(Dispatchers.Main) {
                        textInfo.text = "No se encontró ningún reloj ⛔"
                        textDetalles.text = ""
                    }
                }
            } catch (exception: Exception) {
                Log.e("ErrorNodo", "❌ Error: ${exception.message}")
                withContext(Dispatchers.Main) {
                    textInfo.text = "Error al conectar con el reloj"
                    textDetalles.text = ""
                }
            }
        }
    }

    // === Escuchar mensajes del reloj (sensores) ===
    override fun onMessageReceived(event: MessageEvent) {
        Log.d("Mobile", "Mensaje recibido desde: ${event.sourceNodeId}, ruta: ${event.path}")

        if (event.path == "/SENSOR_DATA") {
            val message = String(event.data, StandardCharsets.UTF_8)
            Log.d("Mobile", "Datos del sensor: $message")

            runOnUiThread {
                textDatos.text = message
            }

            // Enviar datos al servidor local
            sendSensorDataToServer(message)
        } else {
            Log.w("Mobile", "Ruta no reconocida: ${event.path}")
        }
    }

    // === Enviar datos al servidor PHP/MySQL ===
    private fun sendSensorDataToServer(sensorData: String) {
        // 🔧 URL de tu servidor local (ajusta la IP según tu entorno)
        val url = "http://192.168.100.27/smartphone/guardar_datos.php?sensor_data=$sensorData"

        val request = Request.Builder()
            .url(url)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "Sin respuesta"

                Log.d("HTTP", "Respuesta del servidor: $body")

                withContext(Dispatchers.Main) {
                    textInfo.text = " Enviado al servidor:\n$body"
                }
            } catch (e: Exception) {
                Log.e("HTTP", "Error HTTP: ${e.message}")
                withContext(Dispatchers.Main) {
                    textInfo.text = "⚠ Error al enviar datos"
                }
            }
        }
    }

    // === Ciclo de vida ===
    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(this).addListener(this)
            Wearable.getMessageClient(this).addListener(this)
            Wearable.getCapabilityClient(this)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
            Log.d("Lifecycle", "Listeners registrados")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(this).removeListener(this)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getCapabilityClient(this).removeListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {}
    override fun onCapabilityChanged(p0: CapabilityInfo) {}
}
