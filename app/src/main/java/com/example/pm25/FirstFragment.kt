package com.example.pm25

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {


    private val bluetoothManager
            by lazy { requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter
            by lazy { bluetoothManager.adapter!! }
    private val locationManager
            by lazy { requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val scanner by lazy { BleScanner(requireContext(), bluetoothAdapter) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_first, container, false)
        root.findViewById<Button>(R.id.scanbtn).setOnClickListener {
            if (!(ensureBluetoothEnabled() && ensureLocationEnabled() && checkPermission()))
                return@setOnClickListener
            scanner.doScan()
            AlertDialog.Builder(requireContext()).setAdapter(scanner.viewAdapter) { _, which: Int ->
                val item = scanner.viewAdapter.getItem(which)
                Toast.makeText(
                    requireContext(),
                    "Selected device: ${item.name} (${item.address})",
                    Toast.LENGTH_SHORT
                ).show()
                scanner.stopScan()
            }.setTitle("Choose your device")
                .setCancelable(true)
                .setOnCancelListener { scanner.stopScan() }
                .show()
        }
        return root
    }

    fun ensureBluetoothEnabled(): Boolean {
        if (bluetoothAdapter.isDisabled) {
            activity?.let(AlertDialog::Builder)
                ?.setNegativeButton("Cancel") { _, _ -> }
                ?.setCancelable(true)
                ?.setMessage("Bluetooth is required.")
                ?.setPositiveButton("Open Bluetooth") { _, _ -> activity?.startBluetoothActivity() }
                ?.show()
            return false
        }
        return true
    }

    fun checkPermission(): Boolean {
        val ps =
            arrayOf("android.permission.BLUETOOTH", "android.permission.ACCESS_FINE_LOCATION")
        println(checkPermissions(requireContext(), *ps))
        if (!checkPermissions(requireContext(), *ps)) {
            activity?.let(AlertDialog::Builder)
                ?.setCancelable(true)
                ?.setMessage("Bluetooth and Location permissions is required.")
                ?.setPositiveButton("Ok") { _, _ ->
                    if (!checkPermissions(requireContext(), *ps)) {
                        requestPermissions(ps, 1)
                    }
                }?.show()
            return false
        }
        return true
    }

    fun ensureLocationEnabled(): Boolean {
        if (locationManager.isDisable) {
            activity?.let(AlertDialog::Builder)
                ?.setMessage("Location Service is required.")
                ?.setNegativeButton("Cancel") { _, _ -> }
                ?.setPositiveButton("Open Location") { _, _ -> activity?.startLocationActivity() }
                ?.setCancelable(true)
                ?.show()
            return false
        }
        return true
    }

    override fun onDestroy() {
        scanner.stopScan()
        super.onDestroy()
    }
}

class BLEAdapter(context: Context) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)!!
    private val list = ArrayList<Pair<String, BluetoothDevice>>()
    private val addressSet = mutableSetOf<String>()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val name = list[position].first
        val view = (convertView ?: inflater.inflate(R.layout.scan_item, parent, false)) as TextView
        view.text = name
        return view
    }

    override fun getItem(position: Int) = list[position].second

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = list.size

    fun clear() {
        list.clear()
    }

    fun putDevice(device: BluetoothDevice) {
        val address = device.address
        if (address in addressSet) return
        val name = (device.name ?: "<no name>") + " ($address)"
        addressSet.add(address)
        list.add(name to device)
        notifyDataSetChanged()
    }
}

class BleScanner(val context: Context, val bluetoothAdapter: BluetoothAdapter) {
    val viewAdapter = BLEAdapter(context)
    var scanning = false
        private set
    private val handler = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            viewAdapter.putDevice(result.device)
        }
    }

    fun doScan() {
        runCatching { bluetoothAdapter.bluetoothLeScanner.startScan(handler) }
    }

    fun stopScan() {
        runCatching { bluetoothAdapter.bluetoothLeScanner.stopScan(handler) }
    }

}
