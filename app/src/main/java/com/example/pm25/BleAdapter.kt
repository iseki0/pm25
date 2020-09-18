package com.example.pm25

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class BleAdapter(context: Context) : BaseAdapter() {

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