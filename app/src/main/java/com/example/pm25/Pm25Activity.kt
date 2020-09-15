package com.example.pm25

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar


private val neededPermissions =
    arrayOf("android.permission.BLUETOOTH", "android.permission.ACCESS_FINE_LOCATION")

class DeviceListAdapter(val context: Context) :
    RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {
    private val devs = mutableListOf<Any>()

    private val inflater = LayoutInflater.from(context)!!

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.dev_name)
        val address = view.findViewById<TextView>(R.id.dev_address)
        val pm25 = view.findViewById<TextView>(R.id.dev_pm25)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = inflater.inflate(R.layout.device_item, parent)!!
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = devs.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

}

class Pm25Activity : AppCompatActivity() {
    private val bleManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bleAdapter by lazy { bleManager.adapter!! }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val scanner by lazy { BleScanner(this, bleAdapter) }
    private var devicesModified = false

    private val mainList by lazy { DeviceListAdapter(this) }

    override fun onStart() {
        super.onStart()
        setContentView(R.layout.pm25_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener(::clickAddButton)
        findViewById<RecyclerView>(R.id.dlist).adapter = mainList
        startService(Intent(SensorService::class.qualifiedName))

    }

    override fun onPause() {
        super.onPause()
        if (devicesModified) {
            TODO()
            devicesModified = false
        }
    }

    private fun clickAddButton(view: View) {
        if (!(ensureBTEnabled() && ensureLocationEnabled() && checkNeededPermission())) return
        scanner.doScan()
        AlertDialog.Builder(this)
            .setTitle("Select your device")
            .setAdapter(scanner.viewAdapter) { _, i ->
                scanner.stopScan()
                val device = scanner.viewAdapter.getItem(i)

                Snackbar.make(findViewById(R.id.root_view), "selected", Snackbar.LENGTH_SHORT)
                    .show()

            }
            .setOnCancelListener { scanner.stopScan() }
            .show()
    }


    private fun ensureBTEnabled(): Boolean {
        if (bleAdapter.isEnabled) return true
        AlertDialog.Builder(this)
            .setMessage("Bluetooth is required.")
            .setPositiveButton("Ok") { _, _ -> startBluetoothActivity() }
            .show()
        return false
    }

    private fun ensureLocationEnabled(): Boolean {
        if (locationManager.isEnable) return true
        AlertDialog.Builder(this)
            .setMessage("Location is required.")
            .setPositiveButton("Ok") { _, _ -> startLocationActivity() }
        return false
    }

    private fun checkNeededPermission(): Boolean {
        if (checkPermissions(this, *neededPermissions)) return true
        AlertDialog.Builder(this).setMessage("Location and Bluetooth permission is required.")
            .setPositiveButton("Ok") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(neededPermissions, 1)
                }
            }
        return false
    }

}

