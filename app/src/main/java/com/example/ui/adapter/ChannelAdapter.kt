package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.image.TvImageLoader
import com.example.data.model.Channel
import com.example.ui.leanback.LeanbackUtils

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit,
    private val onChannelToggleFavorite: (Channel, Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private val items = mutableListOf<Channel>()

    fun submitList(newList: List<Channel>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun updateFavorite(position: Int, isFav: Boolean) {
        if (position in 0 until items.size) {
            items[position].isFavorite = isFav
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_row, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.channel_card_root)
        private val tvNumber: TextView = itemView.findViewById(R.id.tv_channel_number)
        private val ivLogo: ImageView = itemView.findViewById(R.id.iv_channel_logo)
        private val tvName: TextView = itemView.findViewById(R.id.tv_channel_name)
        private val tvSub: TextView = itemView.findViewById(R.id.tv_channel_sub)
        private val ivFavStar: ImageView = itemView.findViewById(R.id.iv_fav_star)
        private val llInfoFooter: View = itemView.findViewById(R.id.ll_info_footer)

        init {
            LeanbackUtils.applyFocusHighlight(itemView, scale = 1.04f, elevationDp = 10f)
        }

        fun bind(channel: Channel, position: Int) {
            tvNumber.text = channel.channelNumber.toString()
            tvName.text = channel.name

            val subText = buildString {
                if (channel.groupTitle.isNotEmpty()) {
                    append(channel.groupTitle)
                    append(" • ")
                }
                append(channel.playlistName)
            }
            tvSub.text = subText

            // Visible yellow highlight for favorited channels
            if (channel.isFavorite) {
                cardRoot.setBackgroundResource(R.drawable.bg_card_channel_fav)
                ivFavStar.visibility = View.VISIBLE
                llInfoFooter.setBackgroundColor(0xFF282312.toInt())
            } else {
                cardRoot.setBackgroundResource(R.drawable.bg_card_channel)
                ivFavStar.visibility = View.GONE
                llInfoFooter.setBackgroundColor(0xFF1F1F1F.toInt())
            }

            // Asynchronously load logo
            TvImageLoader.get(itemView.context).load(ivLogo, channel.logoUrl)

            // Click to launch external player
            itemView.setOnClickListener {
                onChannelClick(channel)
            }

            // Long press to toggle favorite
            itemView.setOnLongClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onChannelToggleFavorite(channel, currentPos)
                }
                true
            }
        }
    }
}
