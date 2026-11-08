package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.Playlist
import com.example.ui.leanback.LeanbackUtils

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistDelete: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private val items = mutableListOf<Playlist>()

    fun submitList(newList: List<Playlist>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_card, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_playlist_name)
        private val tvSub: TextView = itemView.findViewById(R.id.tv_playlist_sub)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_playlist_count)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_playlist)

        init {
            LeanbackUtils.applyFocusHighlight(itemView, scale = 1.025f, elevationDp = 8f)
            LeanbackUtils.applyFocusHighlight(btnDelete, scale = 1.1f, elevationDp = 6f)
        }

        fun bind(playlist: Playlist) {
            tvName.text = playlist.name
            tvSub.text = "${playlist.sourceType}: ${playlist.sourcePath}"
            tvCount.text = "${playlist.channelCount} CH"

            itemView.setOnClickListener {
                onPlaylistClick(playlist)
            }

            btnDelete.setOnClickListener {
                onPlaylistDelete(playlist)
            }
        }
    }
}
