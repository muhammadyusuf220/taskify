package com.example.taskify.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.example.taskify.R
import com.example.taskify.databinding.FragmentCalendarBinding
import com.example.taskify.ui.adapter.TaskAdapter
import com.example.taskify.ui.viewmodel.MainViewModel
import com.example.taskify.data.model.Task     // <--- Tambahkan ini
import com.example.taskify.data.model.Holiday  // <--- Tambahkan ini
import androidx.core.content.ContextCompat     // <--- Tambahkan ini (untuk perbaikan warna)
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter
    private var selectedDate: String = ""

    private var currentTasks: List<Task> = emptyList()
    private var currentHolidays: List<Holiday> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupCalendarListener()
        setupObservers()

        viewModel.loadTasks()
    }

    private fun setupCalendarListener() {
        // Set default date hari ini
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = dateFormat.format(Date())

        // PERBAIKAN: Implementasi interface OnCalendarDayClickListener dengan benar
        binding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                val clickedDate = calendarDay.calendar.time
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = dateFormat.format(clickedDate)

                filterTasksByDate()
            }
        })
    }



    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { },
            onTaskCheckChanged = { task, isChecked ->
                viewModel.toggleTaskCompletion(task, isChecked)
            },
            onDeleteClick = { task -> viewModel.deleteTask(task) }
        )

        binding.rvDailyTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }
    }

    private fun setupObservers() {
        // 1. Observer Tasks
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            currentTasks = tasks
            filterTasksByDate() // Update list bawah
            updateCalendarEvents() // Update ikon di kalender
        }

        // 2. Observer Holidays
        viewModel.holidays.observe(viewLifecycleOwner) { holidays ->
            currentHolidays = holidays
            updateCalendarEvents() // Update ikon di kalender
        }
    }

    private fun updateCalendarEvents() {
        val eventsMap = HashMap<String, CalendarDay>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // 1. MASUKKAN DATA LIBUR
        currentHolidays.forEach { holiday ->
            try {
                val date = sdf.parse(holiday.date)
                if (date != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    val calendarDay = CalendarDay(calendar)

                    // LOGIC LIBUR: Ubah warna ANGKA tanggal menjadi Merah
                    // Gunakan android.R.color.holo_red_dark (Resource ID)
                    calendarDay.labelColor = android.R.color.holo_red_dark

                    eventsMap[holiday.date] = calendarDay
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 2. MASUKKAN DATA TUGAS (Timpa/Update jika tanggal sama)
        currentTasks.forEach { task ->
            if (!task.isCompleted) {
                try {
                    val dateStr = task.due_date

                    // Cek apakah tanggal ini sudah ada di map (misal: hari libur)?
                    val existingDay = eventsMap[dateStr]

                    if (existingDay != null) {
                        // KASUS: TANGGAL MERAH + ADA TUGAS
                        // Warna angka tetap merah (dari step 1), kita hanya tambahkan icon
                        existingDay.imageResource = android.R.drawable.ic_menu_my_calendar

                    } else {
                        // KASUS: HARI BIASA + ADA TUGAS
                        val date = sdf.parse(dateStr)
                        if (date != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = date
                            val calendarDay = CalendarDay(calendar)

                            // Set Icon Kalender
                            calendarDay.imageResource = android.R.drawable.ic_menu_my_calendar

                            // Masukkan ke map
                            eventsMap[dateStr] = calendarDay
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        // 3. Render ke Kalender
        binding.calendarView.setCalendarDays(eventsMap.values.toList())
    }

    // Tambahkan logika di filterTasksByDate untuk menampilkan nama hari libur
    // Ganti keseluruhan fungsi filterTasksByDate dengan ini:
    private fun filterTasksByDate() {
        // 1. Cari data libur
        val holiday = currentHolidays.find { it.date == selectedDate }

        // 2. Setup Teks Header
        val displayText = StringBuilder()

        // UBAH: Format menjadi "Tanggal: yyyy-MM-dd"
        displayText.append("Tanggal: $selectedDate")

        // LOGIKA BARU: Jika ada libur, tambahkan sebagai subjudul (baris baru)
        // Ini menjamin info libur TIDAK HILANG meskipun ada tugas
        if (holiday != null) {
            displayText.append("\n${holiday.localName}")
        }

        // --- LOGIKA WARNA ADAPTIF ---
        if (holiday != null) {
            // Jika Libur: Merah
            binding.tvSelectedDate.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
            )
        } else {
            // Jika Hari Biasa: Cek Dark Mode
            val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

            if (isDarkMode) {
                binding.tvSelectedDate.setTextColor(Color.WHITE)
            } else {
                binding.tvSelectedDate.setTextColor(Color.BLACK)
            }
        }

        // Set teks ke TextView (Header)
        binding.tvSelectedDate.text = displayText.toString()

        // 3. Filter Tugas
        viewModel.tasks.value?.let { allTasks ->
            val filteredTasks = allTasks.filter { task ->
                task.due_date == selectedDate && !task.isCompleted
            }
            taskAdapter.submitList(filteredTasks)

            // Handle Empty State
            // Keterangan libur sudah dipindah ke atas, jadi di sini cukup info tugas kosong saja
            if (filteredTasks.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "Tidak ada tugas pada tanggal ini"
            } else {
                binding.tvEmptyState.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}