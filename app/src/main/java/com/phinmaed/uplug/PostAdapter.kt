package com.phinmaed.uplug

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter(
    private val posts: List<Post>,
    private val onClick: (Post) -> Unit = {},
    private val onAuthorClick: (Post) -> Unit = {}
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val authorClickableArea: RelativeLayout = itemView.findViewById(R.id.authorClickableArea)
        val userProfilePic: ImageView = itemView.findViewById(R.id.userProfilePic)
        val wallIcon: ImageView = itemView.findViewById(R.id.wallIcon)
        val userName: TextView = itemView.findViewById(R.id.userName)
        val postWall: TextView = itemView.findViewById(R.id.postWall)
        val postTime: TextView = itemView.findViewById(R.id.postTime)
        val postTitle: TextView = itemView.findViewById(R.id.postTitle)
        val postContent: TextView = itemView.findViewById(R.id.postContent)
        val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon) // add this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.home_post_item, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]

        holder.userName.text = post.authorName
        holder.postWall.text = post.wall.uppercase()
        holder.postTime.text = formatTime(post.createdAt)
        holder.postTitle.text = post.title
        holder.postContent.text = post.content
        holder.pinIcon.visibility = if (post.pinned) View.VISIBLE else View.GONE

        when (post.wall.lowercase()) {
            "official" -> holder.wallIcon.setImageResource(R.drawable.cite)
            "cite" -> holder.wallIcon.setImageResource(R.drawable.cite)
            else -> holder.wallIcon.setImageResource(R.drawable.profile)
        }

        loadAuthorPhoto(post.photoUrl, holder.userProfilePic)

        holder.authorClickableArea.setOnClickListener { onAuthorClick(post) }
        holder.itemView.setOnClickListener { onClick(post) }
    }

    override fun getItemCount(): Int = posts.size

    private fun loadAuthorPhoto(photoUrl: String?, imageView: ImageView) {
        if (photoUrl.isNullOrBlank()) {
            loadDefaultProfile(imageView)
            return
        }

        Glide.with(imageView.context)
            .load(photoUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(imageView)
    }

    private fun loadDefaultProfile(imageView: ImageView) {
        Glide.with(imageView.context)
            .load(R.drawable.profile)
            .circleCrop()
            .into(imageView)
    }

    private fun formatTime(timestamp: Timestamp?): String {
        if (timestamp == null) return ""

        val now = System.currentTimeMillis()
        val postTime = timestamp.toDate().time
        val diff = now - postTime

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "${seconds}s"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(timestamp.toDate())
        }
    }
}