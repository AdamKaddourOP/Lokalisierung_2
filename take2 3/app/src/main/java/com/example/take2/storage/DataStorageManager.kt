package com.example.take2.storage

import android.content.Context
import android.util.Log
import java.io.File

class DataStorageManager(private val context: Context) {

    private val TAG = "DataStorageManager"
    private val fileName = "sensor_data.txt"

    fun saveData(sensorType: String, data: String) {
        synchronized(this) {
            try {
                val file = File(context.filesDir, fileName)
                val existingData = if (file.exists()) file.readText() else ""
                val updatedData = "$existingData\n$sensorType: $data"
                file.writeText(updatedData)

                Log.d(TAG, "Data saved: $sensorType = $data")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save data: ${e.message}")
            }
        }
    }

    fun readData(): String {
        return try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read data: ${e.message}")
            ""
        }
    }

    fun clearData() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.delete()
            Log.d(TAG, "Data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear data: ${e.message}")
        }
    }
}
