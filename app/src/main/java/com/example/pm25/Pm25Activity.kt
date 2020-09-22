package com.example.pm25

import android.bluetooth.BluetoothDevice
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


private val neededPermissions =
    arrayOf("android.permission.BLUETOOTH", "android.permission.ACCESS_FINE_LOCATION")

class DeviceListAdapter(context: Context) :
    RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {
    private var list: MutableList<DeviceStatusUpdate> = mutableListOf()

    var sensors: List<DeviceStatusUpdate>
        set(value) {
            list = sensors.toMutableList()
            this.notifyDataSetChanged()
        }
        get() = list

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

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val u = list[position]
        val battery = u.battery
        val name = "${u.info.name}($battery%)"
        holder.name.text = name
        val lastSeen = Instant.ofEpochSecond(u.lastUpdate + 0L)
            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val address = "${u.info.address}($lastSeen)"
        holder.address.text = address
        holder.pm25.text = u.pm25.toString()
    }

    fun processUpdate(update: DeviceStatusUpdate) {
        val address = update.info.address
        val pos = list.indexOfFirst { it.info.address == address }
        if (pos < 0) return
        list[pos] = update
        notifyItemChanged(pos)
    }


}


class Pm25Activity : AppCompatActivity() {
    private val bleManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bleAdapter by lazy { bleManager.adapter!! }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val scanner by lazy { BleScanner(this, bleAdapter) }
    private val bgService: SensorBackgroundService
        get() = serviceConnection.service

    private val serviceConnection =
        localServiceConnection<SensorBackgroundService, SensorBackgroundService.LocalBinder>()

    private val mainList by lazy { DeviceListAdapter(this) }

    private val updateWatcher = { update: DeviceStatusUpdate ->
        mainList.processUpdate(update)
    }

    // TODO: refactor WTF???
    private var canceler = {}

    override fun onStart() {
        super.onStart()
        setContentView(R.layout.pm25_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener(::clickAddButton)
        findViewById<RecyclerView>(R.id.dlist).adapter = mainList
        startService(Intent(this, SensorBackgroundService::class.java))
        if (!serviceConnection.isBind)
            bindBackgroundService()
        serviceConnection.onceBind {
            refreshDeviceList()
            canceler = bgService.watch(updateWatcher)
        }
    }

    override fun onStop() {
        super.onStop()
        canceler.invoke()
        if (serviceConnection.isBind)
            unbindService(serviceConnection)
    }

    private fun addDevice(device: BluetoothDevice) {
        bgService.addDevice(
            SensorDeviceInfo(
                address = device.address,
                name = device.name ?: "<no name>",
            )
        )
        refreshDeviceList()
        bgService.connectDevice(device.address)
    }

    private fun refreshDeviceList() {
        val l = bgService.deviceInfos
        println(l)
        mainList.sensors = l
    }


    private fun clickAddButton(view: View) {
        if (!(ensureBTEnabled() && ensureLocationEnabled() && checkNeededPermission())) return
        scanner.doScan()
        AlertDialog.Builder(this)
            .setTitle("Select your device")
            .setAdapter(scanner.viewAdapter) { _, i ->
                // TODO: ugly code
                scanner.stopScan()
                val device = scanner.viewAdapter.getItem(i)
                val address = device.address
                val name = device.name ?: "<no name>"
                Snackbar.make(
                    findViewById(R.id.root_view),
                    "selected: $name ($address)",
                    Snackbar.LENGTH_SHORT
                )
                    .show()
                addDevice(device)
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
            .show()
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

    private fun bindBackgroundService() {
        bindService(
            Intent(this, SensorBackgroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
}

