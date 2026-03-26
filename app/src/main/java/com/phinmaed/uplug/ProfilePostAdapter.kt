package com.phinmaed.uplug

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ProfilePostAdapter : RecyclerView.Adapter<ProfilePostAdapter.PostViewHolder>() {

    private val items = mutableListOf<Post>()

    fun submitList(posts: List<Post>) {
        items.clear()
        items.addAll(posts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.postTitle)
        private val content: TextView = itemView.findViewById(R.id.postContent)
        private val meta: TextView = itemView.findViewById(R.id.postMeta)
        private val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon)

        fun bind(post: Post) {
            title.text = post.title.orEmpty()
            content.text = post.content.orEmpty()

            val dateText = post.createdAt?.toDate()?.let {
                SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale("en", "PH")).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Manila")
                }.format(it)
            }.orEmpty()

            meta.text = buildString {
                append(post.wall.orEmpty().uppercase())
                if (dateText.isNotBlank()) {
                    append(" • ")
                    append(dateText)
                }
            }

            pinIcon.visibility = if (post.pinned == true) View.VISIBLE else View.GONE
        }
    }
}