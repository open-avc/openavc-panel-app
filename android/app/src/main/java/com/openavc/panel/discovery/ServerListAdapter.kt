package com.openavc.panel.discovery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.openavc.panel.databinding.ItemServerBinding

class ServerListAdapter(
    private val onClick: (ServerInfo) -> Unit,
) : ListAdapter<ServerInfo, ServerListAdapter.VH>(Diff) {

    class VH(val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.name.text = item.name
        val subtitle = buildString {
            append(item.host)
            append(':')
            append(item.port)
            if (item.version.isNotEmpty()) {
                append("  \u2022  v")
                append(item.version)
            }
        }
        holder.binding.subtitle.text = subtitle
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    private object Diff : DiffUtil.ItemCallback<ServerInfo>() {
        override fun areItemsTheSame(oldItem: ServerInfo, newItem: ServerInfo): Boolean =
            oldItem.instanceId.takeIf { it.isNotEmpty() } == newItem.instanceId.takeIf { it.isNotEmpty() } &&
                oldItem.host == newItem.host && oldItem.port == newItem.port

        override fun areContentsTheSame(oldItem: ServerInfo, newItem: ServerInfo): Boolean =
            oldItem == newItem
    }
}
