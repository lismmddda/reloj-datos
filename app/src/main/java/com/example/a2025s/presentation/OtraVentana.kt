
package com.example.a2025s.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.example.a2025s.R
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*

class OtraVentana : ComponentActivity(), SensorEventListener {

    private lateinit var textoHeart: TextView
    private lateinit var textoLight: TextView
    private lateinit var botonConectar: Button
    private lateinit var botonEnviar: Button

    private lateinit var sensorManager: SensorManager
    private var heartSensor: Sensor? = null
    private var lightSensor: Sensor? = null

    private var heartRate: Int? = null
    private var lightLevel: Int? = null

    private var nodeID: String = ""
    private var deviceConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.otra_ventana)

        textoHeart = findViewById(R.id.textoHeart)
        textoLight = findViewById(R.id.textoLight)
        botonConectar = findViewById(R.id.botonConectar)
        botonEnviar = findViewById(R.id.botonEnviar)

        textoHeart.text = " Ritmo cardiaco: -- bpm"
        textoLight.text = " Luz: -- lx"

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (heartSensor == null) textoHeart.text = " Sensor de ritmo cardiaco no disponible"
        if (lightSensor == null) textoLight.text = " Sensor de luz no disponible"

        // Pedir permiso de sensores si no se tiene
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), 1001)
        }

        // Botón para conectar con el móvil
        botonConectar.setOnClickListener {
            Log.d("WearOS", " Intentando conectar con el móvil...")
            obtenerConexionConTelefono()
        }

        // Botón para enviar los datos
        botonEnviar.setOnClickListener {
            if (!deviceConnected) {
                Toast.makeText(this, "️ Conéctate primero al móvil", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (heartRate == null || lightLevel == null) {
                Toast.makeText(this, " Esperando datos del sensor...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mensaje = " Ritmo: ${heartRate} bpm |  Luz: ${lightLevel} lx"
            Log.d("WearOS", " Enviando mensaje: $mensaje")
            sendMessageToPhone(mensaje)
        }
    }

    //  Obtener nodo (móvil conectado)
    private fun obtenerConexionConTelefono() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes)
                if (nodes.isNotEmpty()) {
                    nodeID = nodes.first().id
                    deviceConnected = true
                    Log.d("WearOS", " Conectado al móvil: ${nodes.first().displayName}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, " Conectado al móvil", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, " No hay móviles conectados", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("WearOS", " Error al conectar: ${e.message}")
            }
        }
    }

    // 📡 Enviar mensaje al móvil
    private fun sendMessageToPhone(message: String) {
        if (nodeID.isEmpty()) {
            Toast.makeText(this, " Sin conexión con el móvil", Toast.LENGTH_SHORT).show()
            return
        }

        Wearable.getMessageClient(this)
            .sendMessage(nodeID, "/SENSOR_DATA", message.toByteArray())
            .addOnSuccessListener {
                Toast.makeText(this, " Datos enviados al móvil", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, " Error al enviar", Toast.LENGTH_SHORT).show()
                Log.e("WearOS", "Error al enviar mensaje: ${it.message}")
            }
    }

    // Escuchar sensores
    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                val value = event.values[0].toInt()
                if (value > 0) {
                    heartRate = value
                    textoHeart.text = " Ritmo cardiaco: $value bpm"
                    Log.d("WearOS", " Ritmo cardiaco: $value bpm")
                }
            }

            Sensor.TYPE_LIGHT -> {
                val value = event.values[0].toInt()
                lightLevel = value
                textoLight.text = " Luz ambiental: $value lx"
                Log.d("WearOS", " Luz ambiental: $value lx")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        heartSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
