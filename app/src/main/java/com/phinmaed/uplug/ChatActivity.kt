package com.phinmaed.uplug

import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageEdit: EditText
    private lateinit var sendBtn: ImageView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: ChatAdapter
    private var listener: ListenerRegistration? = null

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var threadId: String
    private lateinit var otherUid: String
    private lateinit var myUid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        myUid = FirebaseAuth.getInstance().uid ?: run {
            finish()
            return
        }

        adapter = ChatAdapter(myUid) { message ->
            toggleExactTime(message)
        }

        threadId = intent.getStringExtra("threadId") ?: ""
        otherUid = intent.getStringExtra("otherUid") ?: ""
        val chatUserName = intent.getStringExtra("otherUserName") ?: "Unknown"
        val chatUserProfilePic = intent.getStringExtra("otherUserPhotoUrl") ?: ""

        if (threadId.isBlank() || otherUid.isBlank()) {
            finish()
            return
        }

        val backBtn = findViewById<ImageView>(R.id.backButton)
        val headerName = findViewById<TextView>(R.id.chatUserName)
        val headerPic = findViewById<ImageView>(R.id.chatUserProfilePic)
        val headerStatus = findViewById<TextView>(R.id.chatUserStatus)

        recyclerView = findViewById(R.id.chatRecyclerView)
        messageEdit = findViewById(R.id.messageEdit)
        sendBtn = findViewById(R.id.sendBtn)

        headerStatus.text = "PHINMA User"
        headerName.text = chatUserName

        Glide.with(this)
            .load(chatUserProfilePic)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()
            .into(headerPic)

        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter.setOtherUserPhoto(chatUserProfilePic)

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        messageEdit.addTextChangedListener {
            val text = it?.toString()?.trim() ?: ""
            val enabled = text.isNotEmpty()
            sendBtn.isEnabled = enabled
            sendBtn.alpha = if (enabled) 1f else 0.4f
        }

        sendBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }

        sendBtn.setOnClickListener {
            val text = messageEdit.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            addPendingMessageToUi(text)
            sendMessage(text)
            messageEdit.text.clear()
        }

        sendBtn.isEnabled = false
        sendBtn.alpha = 0.4f

        ensureThreadExists {
            listenForMessages()
        }
    }

    private fun addPendingMessageToUi(text: String) {
        val pending = Message(
            fromUid = myUid,
            toUid = otherUid,
            text = text,
            createdAt = com.google.firebase.Timestamp.now()
        )

        val updated = messages.toMutableList()
        updated.add(pending)

        val grouped = applyGrouping(updated)

        messages.clear()
        messages.addAll(grouped)

        adapter.submitMessages(messages)

        recyclerView.post {
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun sendMessage(text: String) {
        val threadRef = db.collection("threads").document(threadId)
        val msgRef = threadRef.collection("messages").document()
        val now = com.google.firebase.Timestamp.now()

        val msgData = hashMapOf(
            "fromUid" to myUid,
            "toUid" to otherUid,
            "text" to text,
            "createdAt" to now
        )

        val threadData = hashMapOf(
            "participants" to threadParticipants(),
            "createdAt" to now,
            "updatedAt" to now,
            "lastMessage" to text,
            "lastFromUid" to myUid
        )

        threadRef.set(threadData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                msgRef.set(msgData)
                    .addOnFailureListener { e ->
                        android.util.Log.e("ChatActivity", "Message write failed", e)
                        Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChatActivity", "Thread upsert failed", e)
                Toast.makeText(this, "Failed to start chat: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun listenForMessages() {
        listener = db.collection("threads")
            .document(threadId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener

                val rawMessages = mutableListOf<Message>()
                for (doc in snapshot.documents) {
                    doc.toObject(Message::class.java)?.let { rawMessages.add(it) }
                }

                val groupedMessages = applyGrouping(rawMessages)

                messages.clear()
                messages.addAll(groupedMessages)

                adapter.submitMessages(messages)

                if (messages.isNotEmpty()) {
                    recyclerView.post {
                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
    }

    private var selectedMessageKey: String? = null

    private fun messageKey(message: Message): String {
        return "${message.fromUid}|${message.createdAt?.seconds}|${message.text}"
    }

    private fun toggleExactTime(message: Message) {
        val tappedKey = messageKey(message)
        val oldKey = selectedMessageKey

        selectedMessageKey = if (oldKey == tappedKey) null else tappedKey

        val updated = messages.map {
            val key = messageKey(it)
            it.copy(
                showExactTime = key == selectedMessageKey,
                isSelected = key == selectedMessageKey
            )
        }

        messages.clear()
        messages.addAll(updated)

        adapter.submitMessages(messages)
    }

    private fun applyGrouping(list: List<Message>): List<Message> {
        val result = mutableListOf<Message>()

        for (i in list.indices) {
            val current = list[i]
            val prev = list.getOrNull(i - 1)
            val next = list.getOrNull(i + 1)

            val groupedPrev = canGroup(prev, current)
            val groupedNext = canGroup(current, next)

            result.add(
                current.copy(
                    isGroupedWithPrevious = groupedPrev,
                    isGroupedWithNext = groupedNext,
                    showExactTime = false
                )
            )
        }

        return result
    }

    private fun canGroup(a: Message?, b: Message?): Boolean {
        if (a == null || b == null) return false
        if (a.fromUid != b.fromUid) return false

        val aDate = a.createdAt?.toDate() ?: return false
        val bDate = b.createdAt?.toDate() ?: return false

        val sameDay = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(aDate) ==
                java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(bDate)

        if (!sameDay) return false

        val diffMs = kotlin.math.abs(bDate.time - aDate.time)
        val within5Minutes = diffMs <= 5 * 60 * 1000

        return within5Minutes
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    private fun ensureThreadExists(onReady: () -> Unit) {
        val threadRef = db.collection("threads").document(threadId)

        threadRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                onReady()
            } else {
                val now = com.google.firebase.Timestamp.now()

                val data = mapOf(
                    "participants" to threadParticipants(),
                    "createdAt" to now,
                    "updatedAt" to now,
                    "lastMessage" to "",
                    "lastFromUid" to ""
                )

                threadRef.set(data)
                    .addOnSuccessListener {
                        onReady()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ChatActivity", "Thread create failed", e)
                        Toast.makeText(this, "Failed to create chat: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("ChatActivity", "Thread read failed", e)
            Toast.makeText(this, "Failed to open chat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun threadParticipants(): List<String> {
        return if (myUid < otherUid) listOf(myUid, otherUid) else listOf(otherUid, myUid)
    }
}