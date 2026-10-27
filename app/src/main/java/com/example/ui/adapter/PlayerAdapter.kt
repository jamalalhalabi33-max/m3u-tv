package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.VideoPlayerApp

class PlayerAdapter(
    private val onPlayerSelected: (VideoPlayerApp) -> Unit
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    private val items = mutableListOf<VideoPlayerApp>()
    private var selectedPackage: String? = null

    fun submitData(newList: List<VideoPlayerApp>, currentPrefPackage: String?) {
        items.clear()
        items.addAll(newList)
        selectedPackage = currentPrefPackage
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_player_row, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_player_icon)
        private val tvName: TextView = itemView.findViewById(R.id.tv_player_name)
        private val tvPkg: TextView = itemView.findViewById(R.id.tv_player_pkg)
        private val tvSelected: TextView = itemView.findViewById(R.id.tv_player_selected)

        fun bind(player: VideoPlayerApp) {
            tvName.text = player.label
            tvPkg.text = if (player.isSystemDefault) "Android Intent Chooser" else player.packageName

            if (player.icon != null) {
                ivIcon.setImageDrawable(player.icon)
            } else {
                ivIcon.setImageResource(R.drawable.ic_play)
            }

            val isSelected = if (player.isSystemDefault) {
                selectedPackage.isNullOrEmpty()
            } else {
                player.packageName == selectedPackage
            }

            tvSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                selectedPackage = if (player.isSystemDefault) null else player.packageName
                notifyDataSetChanged()
                onPlayerSelected(player)
            }
        }
    }
}
