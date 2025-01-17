package com.example.take2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.take2.sensors.SensorDataCollector
import com.example.take2.storage.DataStorageManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dataStorageManager: DataStorageManager
    private lateinit var sensorDataCollector: SensorDataCollector
    private lateinit var startStopButton: Button
    private lateinit var startTrackingButton: Button
    private lateinit var saveButton: Button
    private lateinit var accelerometerTextView: TextView
    private lateinit var gyroscopeTextView: TextView
    private lateinit var magnetometerTextView: TextView

    private lateinit var accelerometerSpinner: Spinner
    private lateinit var gyroscopeSpinner: Spinner
    private lateinit var magnetometerSpinner: Spinner

    private lateinit var mapView: MapView
    private var currentLocationMarker: Marker? = null

    private var isCollecting: Boolean = false
    private var isTracking: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val locationLog = mutableListOf<GeoPoint>()
    private val CREATE_FILE_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "onCreate called")

        try {
            // Initialize UI components
            startStopButton = findViewById(R.id.startStopButton)
            startTrackingButton = findViewById(R.id.startTrackingButton)
            saveButton = findViewById(R.id.newButton)
            accelerometerTextView = findViewById(R.id.accelerometerTextView)
            gyroscopeTextView = findViewById(R.id.gyroscopeTextView)
            magnetometerTextView = findViewById(R.id.magnetometerTextView)

            accelerometerSpinner = findViewById(R.id.accelerometerFrequencySpinner)
            gyroscopeSpinner = findViewById(R.id.gyroscopeFrequencySpinner)
            magnetometerSpinner = findViewById(R.id.magnetometerFrequencySpinner)

            // Setup frequency spinners
            val frequencies = arrayOf("Normal", "UI", "Game", "Fastest")
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frequencies)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            accelerometerSpinner.adapter = adapter
            gyroscopeSpinner.adapter = adapter
            magnetometerSpinner.adapter = adapter

            // Initialize storage and sensors
            dataStorageManager = DataStorageManager(this)
            sensorDataCollector = SensorDataCollector(this)

            // Initialize map
            Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            mapView = findViewById(R.id.mapContainer)
            mapView.setMultiTouchControls(true)
            setDefaultMapLocation()

            // Button listeners
            startStopButton.setOnClickListener { toggleDataCollection() }
            startTrackingButton.setOnClickListener { toggleLocationTracking() }
            saveButton.setOnClickListener { openFilePicker() }

            sensorDataCollector.onSensorDataCollected = { sensorType, data ->
                Log.d("MainActivity", "Sensor data collected: $sensorType - $data")
                runOnUiThread {
                    when (sensorType) {
                        "Accelerometer" -> accelerometerTextView.text = "Accelerometer: $data"
                        "Gyroscope" -> gyroscopeTextView.text = "Gyroscope: $data"
                        "Magnetometer" -> magnetometerTextView.text = "Magnetometer: $data"
                    }
                }
            }

            checkAndRequestPermissions()

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
        }
    }

    private fun toggleDataCollection() {
        Log.d("MainActivity", "toggleDataCollection called")
        if (isCollecting) {
            sensorDataCollector.stopCollecting()
            startStopButton.text = "Start Collection"
        } else {
            sensorDataCollector.startCollecting()
            startStopButton.text = "Stop Collection"
        }
        isCollecting = !isCollecting
    }

    private fun toggleLocationTracking() {
        Log.d("MainActivity", "toggleLocationTracking called")
        if (isTracking) {
            stopLocationTracking()
        } else {
            startLocationTracking()
        }
        isTracking = !isTracking
    }

    private fun startLocationTracking() {
        Log.d("MainActivity", "startLocationTracking called")
        startTrackingButton.text = "Stop Tracking"

        sensorDataCollector.startLocationUpdates() // Startet die Standortaktualisierungen

        sensorDataCollector.onLocationUpdated = { latitude, longitude ->
            Log.d("MainActivity", "Updated coordinates: $latitude, $longitude")
            val geoPoint = GeoPoint(latitude, longitude)
            locationLog.add(geoPoint)
            runOnUiThread {
                updateMapLocation(latitude, longitude)
                mapView.controller.animateTo(geoPoint)
            }
        }
    }

    private fun openFilePicker() {
        Log.d("MainActivity", "openFilePicker called")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "location_data.json")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    private fun stopLocationTracking() {

        Log.d("MainActivity", "stopLocationTracking called")
        startTrackingButton.text = "Start Tracking"
        sensorDataCollector.stopLocationUpdates() // Standort-Updates stoppen
        saveLocationLogToFile();
    }

    private fun saveLocationLogToFile() {
        Log.d("MainActivity", "saveLocationLogToFile called")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "location_data.json")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    private fun saveDataToUri(uri: Uri) {
        Log.d("MainActivity", "saveDataToUri called with URI: $uri")
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val data = locationLog.joinToString(separator = "\n") {
                    "{\"latitude\": ${it.latitude}, \"longitude\": ${it.longitude}}"
                }
                outputStream.write(data.toByteArray())
                Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save data: ${e.message}", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("MainActivity", "onActivityResult called with requestCode: $requestCode, resultCode: $resultCode")
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                saveDataToUri(uri)
            } ?: run {
                Log.e("MainActivity", "URI is null")
            }
        }
    }

    private fun setDefaultMapLocation() {
        Log.d("MainActivity", "setDefaultMapLocation called")
        val startPoint = GeoPoint(52.5200, 13.4050) // Berlin
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(startPoint)
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) {
        Log.d("MainActivity", "updateMapLocation called with latitude: $latitude, longitude: $longitude")
        val geoPoint = GeoPoint(latitude, longitude)
        currentLocationMarker?.let { mapView.overlays.remove(it) }

        currentLocationMarker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Current Location"
        }
        mapView.overlays.add(currentLocationMarker)
        mapView.invalidate()
    }

    private fun checkAndRequestPermissions() {
        Log.d("MainActivity", "checkAndRequestPermissions called")
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val requiredPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (requiredPermissions.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions: $requiredPermissions")
            ActivityCompat.requestPermissions(this, requiredPermissions.toTypedArray(), 101)
        }
    }
}
