package com.phinmaed.uplug

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatAdapter(
    private val myId: String,
    private val onMessageClick: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE = 3
        private const val PAYLOAD_SELECTION = "payload_selection"
    }

    private val items = mutableListOf<ChatItem>()
    private var otherUserPhotoUrl: String = ""

    init {
        setHasStableIds(true)
    }

    fun setOtherUserPhoto(url: String) {
        otherUserPhotoUrl = url
    }

    fun submitMessages(messages: List<Message>) {
        val oldItems = items.toList()

        items.clear()

        var lastDate: String? = null
        for (msg in messages) {
            val date = formatDate(msg)

            if (date != lastDate) {
                items.add(ChatItem(type = TYPE_DATE, dateText = date))
                lastDate = date
            }

            items.add(
                ChatItem(
                    type = if (msg.fromUid == myId) TYPE_SENT else TYPE_RECEIVED,
                    message = msg
                )
            )
        }

        if (oldItems.isEmpty() || oldItems.size != items.size) {
            notifyDataSetChanged()
            return
        }

        for (i in items.indices) {
            val oldItem = oldItems[i]
            val newItem = items[i]

            if (oldItem.type != newItem.type) {
                notifyDataSetChanged()
                return
            }

            if (newItem.type == TYPE_DATE) continue

            val oldMsg = oldItem.message ?: continue
            val newMsg = newItem.message ?: continue

            val selectionChanged =
                oldMsg.showExactTime != newMsg.showExactTime ||
                        oldMsg.isSelected != newMsg.isSelected

            val contentChanged =
                oldMsg.text != newMsg.text ||
                        oldMsg.fromUid != newMsg.fromUid ||
                        oldMsg.toUid != newMsg.toUid ||
                        oldMsg.createdAt != newMsg.createdAt ||
                        oldMsg.isGroupedWithPrevious != newMsg.isGroupedWithPrevious ||
                        oldMsg.isGroupedWithNext != newMsg.isGroupedWithNext

            when {
                contentChanged -> notifyItemChanged(i)
                selectionChanged -> notifyItemChanged(i, PAYLOAD_SELECTION)
            }
        }
    }

    override fun getItemViewType(position: Int): Int = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_SENT -> SentViewHolder(
                inflater.inflate(R.layout.message_item_sent, parent, false)
            )
            TYPE_RECEIVED -> ReceivedViewHolder(
                inflater.inflate(R.layout.message_item_received, parent, false)
            )
            TYPE_DATE -> DateViewHolder(
                inflater.inflate(R.layout.message_date_separator, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is SentViewHolder -> item.message?.let { holder.bind(it) }
            is ReceivedViewHolder -> item.message?.let { holder.bind(it) }
            is DateViewHolder -> item.dateText?.let { holder.bind(it) }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val item = items[position]
            when (holder) {
                is SentViewHolder -> item.message?.let { holder.bindSelectionOnly(it) }
                is ReceivedViewHolder -> item.message?.let { holder.bindSelectionOnly(it) }
            }
            return
        }

        super.onBindViewHolder(holder, position, payloads)
    }


    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        val item = items[position]
        return when (item.type) {
            TYPE_DATE -> ("date|" + (item.dateText ?: "")).hashCode().toLong()
            else -> {
                val m = item.message
                ("msg|${m?.fromUid}|${m?.createdAt?.seconds}|${m?.text}").hashCode().toLong()
            }
        }
    }

    private fun formatDate(message: Message): String {
        val ts = message.createdAt?.toDate() ?: Date()
        return SimpleDateFormat("MMM dd, yyyy", Locale("en", "PH")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }.format(ts)
    }

    private fun formatExactTime(message: Message): String {
        val ts = message.createdAt?.toDate() ?: Date()
        return SimpleDateFormat("hh:mm a", Locale("en", "PH")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }.format(ts)
    }
    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)

        fun bind(date: String) {
            dateText.text = date
        }
    }

    inner class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.sentMessageText)
        private val time: TextView = itemView.findViewById(R.id.sentMessageTime)

        fun bind(message: Message) {
            text.text = message.text
            time.text = formatExactTime(message)

            val bg = when {
                !message.isGroupedWithPrevious && !message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_sent_selected
                    else R.drawable.message_bubble_sent

                message.isGroupedWithPrevious && message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_sent_middle_selected
                    else R.drawable.message_bubble_sent_middle

                message.isGroupedWithPrevious ->
                    if (message.isSelected) R.drawable.message_bubble_sent_bottom_selected
                    else R.drawable.message_bubble_sent_bottom

                else ->
                    if (message.isSelected) R.drawable.message_bubble_sent_top_selected
                    else R.drawable.message_bubble_sent_top
            }
            text.setBackgroundResource(bg)

            val showTime = message.showExactTime || !message.isGroupedWithNext
            time.animate().cancel()
            time.visibility = if (showTime) View.VISIBLE else View.GONE
            time.alpha = if (showTime) 1f else 0f
            time.translationY = 0f

            text.scaleX = if (message.isSelected) 0.98f else 1f
            text.scaleY = if (message.isSelected) 0.98f else 1f

            val params = itemView.layoutParams as RecyclerView.LayoutParams
            params.topMargin = if (message.isGroupedWithPrevious) 2 else 10
            params.bottomMargin = if (message.isGroupedWithNext) 2 else 10
            itemView.layoutParams = params

            text.setOnClickListener { onMessageClick(message) }
        }

        fun bindSelectionOnly(message: Message) {
            val bg = when {
                !message.isGroupedWithPrevious && !message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_sent_selected
                    else R.drawable.message_bubble_sent

                message.isGroupedWithPrevious && message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_sent_middle_selected
                    else R.drawable.message_bubble_sent_middle

                message.isGroupedWithPrevious ->
                    if (message.isSelected) R.drawable.message_bubble_sent_bottom_selected
                    else R.drawable.message_bubble_sent_bottom

                else ->
                    if (message.isSelected) R.drawable.message_bubble_sent_top_selected
                    else R.drawable.message_bubble_sent_top
            }
            text.setBackgroundResource(bg)

            text.animate()
                .scaleX(if (message.isSelected) 0.98f else 1f)
                .scaleY(if (message.isSelected) 0.98f else 1f)
                .setDuration(120)
                .start()

            val shouldShow = message.showExactTime || !message.isGroupedWithNext
            animateTimeOnly(time, shouldShow)
        }
    }

    inner class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.receivedMessageText)
        private val time: TextView = itemView.findViewById(R.id.receivedMessageTime)
        private val profilePic: ImageView = itemView.findViewById(R.id.receivedMessageProfilePic)

        fun bind(message: Message) {
            text.text = message.text
            time.text = formatExactTime(message)

            val bg = when {
                !message.isGroupedWithPrevious && !message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_received_selected
                    else R.drawable.message_bubble_received

                message.isGroupedWithPrevious && message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_received_middle_selected
                    else R.drawable.message_bubble_received_middle

                message.isGroupedWithPrevious ->
                    if (message.isSelected) R.drawable.message_bubble_received_bottom_selected
                    else R.drawable.message_bubble_received_bottom

                else ->
                    if (message.isSelected) R.drawable.message_bubble_received_top_selected
                    else R.drawable.message_bubble_received_top
            }
            text.setBackgroundResource(bg)

            val showTime = message.showExactTime || !message.isGroupedWithNext
            time.animate().cancel()
            time.visibility = if (showTime) View.VISIBLE else View.GONE
            time.alpha = if (showTime) 1f else 0f
            time.translationY = 0f

            text.scaleX = if (message.isSelected) 0.98f else 1f
            text.scaleY = if (message.isSelected) 0.98f else 1f

            val params = itemView.layoutParams as RecyclerView.LayoutParams
            params.topMargin = if (message.isGroupedWithPrevious) 2 else 10
            params.bottomMargin = if (message.isGroupedWithNext) 2 else 10
            itemView.layoutParams = params

            profilePic.visibility = if (message.isGroupedWithNext) View.INVISIBLE else View.VISIBLE

            Glide.with(profilePic.context)
                .load(otherUserPhotoUrl)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .circleCrop()
                .into(profilePic)

            text.setOnClickListener { onMessageClick(message) }
        }

        fun bindSelectionOnly(message: Message) {
            val bg = when {
                !message.isGroupedWithPrevious && !message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_received_selected
                    else R.drawable.message_bubble_received

                message.isGroupedWithPrevious && message.isGroupedWithNext ->
                    if (message.isSelected) R.drawable.message_bubble_received_middle_selected
                    else R.drawable.message_bubble_received_middle

                message.isGroupedWithPrevious ->
                    if (message.isSelected) R.drawable.message_bubble_received_bottom_selected
                    else R.drawable.message_bubble_received_bottom

                else ->
                    if (message.isSelected) R.drawable.message_bubble_received_top_selected
                    else R.drawable.message_bubble_received_top
            }
            text.setBackgroundResource(bg)

            text.animate()
                .scaleX(if (message.isSelected) 0.98f else 1f)
                .scaleY(if (message.isSelected) 0.98f else 1f)
                .setDuration(120)
                .start()

            val shouldShow = message.showExactTime || !message.isGroupedWithNext
            animateTimeOnly(time, shouldShow)
        }
    }
    private fun animateTimeOnly(time: TextView, show: Boolean) {
        time.animate().cancel()
        (time.getTag(R.id.time_animator_tag) as? ValueAnimator)?.cancel()

        if (show) {
            time.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val targetHeight = time.measuredHeight.coerceAtLeast(1)

            if (time.visibility != View.VISIBLE) {
                time.visibility = View.VISIBLE
                time.alpha = 0f
                time.translationY = -4f
                time.layoutParams.height = 0
                time.requestLayout()
            }

            val animator = ValueAnimator.ofInt(time.height, targetHeight).apply {
                duration = 140
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener {
                    val value = it.animatedValue as Int
                    time.layoutParams.height = value
                    time.requestLayout()
                }
            }

            time.setTag(R.id.time_animator_tag, animator)

            time.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(140)
                .start()

            animator.start()
        } else {
            val startHeight = time.height
            if (time.visibility != View.VISIBLE || startHeight == 0) {
                time.visibility = View.GONE
                return
            }

            val animator = ValueAnimator.ofInt(startHeight, 0).apply {
                duration = 100
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener {
                    val value = it.animatedValue as Int
                    time.layoutParams.height = value
                    time.requestLayout()
                }
            }

            time.setTag(R.id.time_animator_tag, animator)

            time.animate()
                .alpha(0f)
                .translationY(-4f)
                .setDuration(100)
                .withEndAction {
                    time.visibility = View.GONE
                    time.alpha = 1f
                    time.translationY = 0f
                    time.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    time.requestLayout()
                }
                .start()

            animator.start()
        }
    }
}