package com.openavc.panel.discovery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.openavc.panel.R
import com.openavc.panel.databinding.ItemDiscoveryNotesBinding
import com.openavc.panel.databinding.ItemDiscoveryStatusBinding

/** What the discovery screen says above an empty system list. */
enum class DiscoveryStatus {
    /** Systems are listed; nothing to say. */
    HIDDEN,
    SEARCHING,
    NOTHING_FOUND;

    companion object {
        fun of(serverCount: Int, nothingFound: Boolean): DiscoveryStatus = when {
            serverCount > 0 -> HIDDEN
            nothingFound -> NOTHING_FOUND
            else -> SEARCHING
        }
    }
}

/**
 * Zero or one row at the top of the discovery list: the searching spinner,
 * or "No systems found" and what to try instead.
 */
class DiscoveryStatusAdapter : RecyclerView.Adapter<DiscoveryStatusAdapter.VH>() {

    class VH(val binding: ItemDiscoveryStatusBinding) : RecyclerView.ViewHolder(binding.root)

    var status: DiscoveryStatus = DiscoveryStatus.SEARCHING
        set(value) {
            if (field == value) return
            val wasShown = field != DiscoveryStatus.HIDDEN
            field = value
            val shown = value != DiscoveryStatus.HIDDEN
            when {
                wasShown && shown -> notifyItemChanged(0)
                wasShown -> notifyItemRemoved(0)
                shown -> notifyItemInserted(0)
            }
        }

    override fun getItemCount(): Int = if (status == DiscoveryStatus.HIDDEN) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDiscoveryStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = holder.binding
        val nothingFound = status == DiscoveryStatus.NOTHING_FOUND
        b.searchingProgress.visibility = if (nothingFound) View.GONE else View.VISIBLE
        b.noneIcon.visibility = if (nothingFound) View.VISIBLE else View.GONE
        b.statusTitle.setText(
            if (nothingFound) R.string.discovery_none_title else R.string.discovery_searching_title
        )
        b.statusDetail.setText(
            if (nothingFound) R.string.discovery_none_detail else R.string.discovery_searching_detail
        )
    }
}

/** The two notes under the list: the app needs a server, and how to reach the admin menu. */
class DiscoveryNotesAdapter : RecyclerView.Adapter<DiscoveryNotesAdapter.VH>() {

    class VH(binding: ItemDiscoveryNotesBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDiscoveryNotesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = Unit
}
