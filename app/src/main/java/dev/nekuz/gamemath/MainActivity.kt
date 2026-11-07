package dev.nekuz.gamemath

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = ToolAdapter()
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, Tool.entries[position].activity))
        }
        findViewById<View>(R.id.infoButton).setOnClickListener {
            dialog?.dismiss()
            dialog = AlertDialog.Builder(this)
                .setTitle(R.string.info)
                .setMessage(R.string.info_message)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private inner class ToolAdapter : BaseAdapter() {
        override fun getCount() = Tool.entries.size

        override fun getItem(position: Int) = Tool.entries[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.row_tool, parent, false)
            val tool = Tool.entries[position]
            view.findViewById<TextView>(R.id.title).setText(tool.title)
            view.findViewById<TextView>(R.id.summary).setText(tool.summary)
            return view
        }
    }
}
