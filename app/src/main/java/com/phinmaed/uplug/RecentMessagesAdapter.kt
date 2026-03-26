package com.phinmaed.uplug

import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class RecentMessagesAdapter(
    private val items: List<MessagePreview>
) : RecyclerView.Adapter<RecentMessagesAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {

        val avatar: ImageView = view.findViewById(R.id.messageAvatar)
        val name: TextView = view.findViewById(R.id.messageName)
        val preview: TextView = view.findViewById(R.id.messagePreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_message, parent, false)

        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = items[position]

        holder.name.text = item.name
        holder.preview.text = item.lastMessage

        Glide.with(holder.avatar.context)
            .load(item.photoUrl)
            .placeholder(R.drawable.profile)
            .circleCrop()
            .into(holder.avatar)
    }

    override fun getItemCount() = items.size
}