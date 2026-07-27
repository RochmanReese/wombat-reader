package com.techwombat.reader.appearance

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.techwombat.reader.storage.ReaderAppearance
import com.techwombat.reader.storage.ReaderTheme

class AppearanceDialog(private val context: Context, initial: ReaderAppearance, private val changed: (ReaderAppearance) -> Unit) {
    private var appearance = initial.normalized()
    private val density get() = context.resources.displayMetrics.density

    fun show() {
        val padding = (20 * density).toInt()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding / 2, padding, 0) }
        content.addView(Switch(context).apply {
            text = if (appearance.theme == ReaderTheme.DARK) "Dark mode" else "Light mode"
            isChecked = appearance.theme == ReaderTheme.DARK
            contentDescription = "Dark mode"
            setOnCheckedChangeListener { _, checked ->
                appearance = appearance.copy(theme = if (checked) ReaderTheme.DARK else ReaderTheme.LIGHT)
                text = if (checked) "Dark mode" else "Light mode"
                notifyChange()
            }
        })
        content.addView(fontRow())
        content.addView(stepper("Font size", { appearance.fontScale }, { appearance = appearance.copy(fontScale = it); notifyChange() }, ReaderAppearance.MIN_FONT_SCALE, ReaderAppearance.MAX_FONT_SCALE))
        content.addView(stepper("Line spacing", { appearance.lineSpacing }, { appearance = appearance.copy(lineSpacing = it); notifyChange() }, ReaderAppearance.MIN_LINE_SPACING, ReaderAppearance.MAX_LINE_SPACING))
        AlertDialog.Builder(context).setTitle("Reading appearance").setView(content).setPositiveButton("Done", null).show()
    }

    private fun fontRow() = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(label("Font"))
        addView(Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ReaderAppearanceMapper.fontChoices.map { it.second })
            setSelection(ReaderAppearanceMapper.fontChoices.indexOfFirst { it.first == appearance.fontFamily }.coerceAtLeast(0))
            contentDescription = "Font family"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { appearance = appearance.copy(fontFamily = ReaderAppearanceMapper.fontChoices[position].first); notifyChange() }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
    }

    private fun stepper(name: String, value: () -> Float, set: (Float) -> Unit, min: Float, max: Float) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(label(name))
        val valueView = TextView(context).apply { text = format(value()); gravity = Gravity.CENTER; minWidth = (52 * density).toInt() }
        addView(smallButton("−", "Decrease $name") { set((value() - 0.1f).coerceAtLeast(min)); valueView.text = format(value()) })
        addView(valueView)
        addView(smallButton("+", "Increase $name") { set((value() + 0.1f).coerceAtMost(max)); valueView.text = format(value()) })
    }

    private fun label(text: String) = TextView(context).apply { this.text = text; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
    private fun smallButton(text: String, description: String, click: () -> Unit) = Button(context).apply {
        this.text = text; contentDescription = description; minWidth = 0; minHeight = 0
        setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
        layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (36 * density).toInt())
        setOnClickListener { click() }
    }
    private fun notifyChange() = changed(appearance.normalized())
    private fun format(value: Float) = "%.1f×".format(value)
}
