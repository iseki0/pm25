package space.iseki.pm25

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pm25.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class DeviceListAdapter(val context: Activity) :
    RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {
    private var list: MutableList<DeviceStatusUpdate> = mutableListOf()

    var sensors: List<DeviceStatusUpdate>
        set(value) {
            context.runOnUiThread {
                list = value.toMutableList()
                notifyDataSetChanged()
            }
        }
        get() = list

    lateinit var userActionHandler: (UserAction, DeviceStatusUpdate) -> Unit

    private val inflater = LayoutInflater.from(context)!!

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.dev_name)
        val address = view.findViewById<TextView>(R.id.dev_address)
        val pm25 = view.findViewById<TextView>(R.id.dev_pm25)
        val dinfo = view.findViewById<TextView>(R.id.dev_status)
        val wrapper = view.findViewById<LinearLayout>(R.id.dev_item_wrapper)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = inflater.inflate(R.layout.device_item, parent, false)!!
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
        val dinfo = "${u.connectStatus.str} ${if (u.charge) "Charging" else ""}"
        holder.dinfo.text = dinfo
        holder.wrapper.setOnClickListener {
            AlertDialog.Builder(context).apply {
                setTitle("Choose action")
                setItems(R.array.item_user_action) { _, which ->
                    userActionHandler.invoke(UserAction.values()[which], u)
                }
                show()
            }
        }
    }

    fun processUpdate(update: DeviceStatusUpdate) {
        context.runOnUiThread {
            val address = update.info.address
            val pos = list.indexOfFirst { it.info.address == address }
            if (pos < 0) return@runOnUiThread
            list[pos] = update
            notifyItemChanged(pos)
            println("notify change: $pos")
        }
    }

    enum class UserAction {
        Connect,
        Disconnect,
        Remove,
        SetFreq,
        Rename,
        Shutdown,
    }
}

private val DeviceConnectStatus.str: String
    get() = when (this) {
        DeviceConnectStatus.CONNECTED -> "Connected"
        DeviceConnectStatus.CONNECTING -> "Connecting"
        DeviceConnectStatus.DISCONNECTED -> "Disconnected"
        DeviceConnectStatus.ERROR -> "Error"
    }
