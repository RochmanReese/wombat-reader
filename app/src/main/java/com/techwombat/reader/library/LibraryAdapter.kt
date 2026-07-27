package com.techwombat.reader.library

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.techwombat.reader.databinding.ItemLibraryBookBinding
import com.techwombat.reader.storage.BookReadingState
import java.io.File

class LibraryAdapter(
    private val onOpen: (BookReadingState) -> Unit,
    private val onDeleteRequested: (BookReadingState) -> Unit,
) : RecyclerView.Adapter<LibraryAdapter.BookViewHolder>() {
    private var books: List<BookReadingState> = emptyList()
    fun submit(books: List<BookReadingState>) { this.books = books; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = BookViewHolder(ItemLibraryBookBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = books.size
    override fun onBindViewHolder(holder: BookViewHolder, position: Int) = holder.bind(books[position])

    inner class BookViewHolder(private val binding: ItemLibraryBookBinding) : RecyclerView.ViewHolder(binding.root) {
        private val handler = Handler(Looper.getMainLooper())
        fun bind(book: BookReadingState) {
            binding.bookTitle.text = book.title?.takeIf { it.isNotBlank() } ?: book.privateFilePath?.let { File(it).nameWithoutExtension } ?: "Untitled book"
            binding.bookAuthor.text = book.author?.takeIf { it.isNotBlank() } ?: "Unknown author"
            binding.root.contentDescription = "${binding.bookTitle.text}, ${binding.bookAuthor.text}. Tap to open. Hold for two seconds to delete."
            binding.root.setOnTouchListener(object : View.OnTouchListener {
                private var held = false
                private var startX = 0f
                private var startY = 0f
                private val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
                private val deleteRunnable = Runnable { held = true; binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onDeleteRequested(book) }
                override fun onTouch(view: View, event: MotionEvent): Boolean = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        held = false; startX = event.x; startY = event.y
                        handler.postDelayed(deleteRunnable, DELETE_HOLD_MILLIS)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.x - startX) > touchSlop || kotlin.math.abs(event.y - startY) > touchSlop) handler.removeCallbacks(deleteRunnable)
                        true
                    }
                    MotionEvent.ACTION_UP -> { handler.removeCallbacks(deleteRunnable); if (!held) onOpen(book); true }
                    MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(deleteRunnable); true }
                    else -> true
                }
            })
        }
    }
    private companion object { const val DELETE_HOLD_MILLIS = 2_000L }
}
