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
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter
    private var selectedDate: String = ""

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

    private fun setupObservers() {
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            // 1. Update List di Bawah (Filter)
            filterTasksByDate()

            // 2. Update Tanda Titik di Kalender
            val events = ArrayList<CalendarDay>()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            tasks.forEach { task ->
                // Hanya tandai jika task BELUM selesai
                if (!task.isCompleted) {
                    try {
                        val date = sdf.parse(task.due_date)
                        if (date != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = date

                            val calendarDay = CalendarDay(calendar)
                            calendarDay.imageResource = R.drawable.ic_dot_red // Ikon titik merah
                            events.add(calendarDay)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            // Set semua event ke tampilan kalender
            binding.calendarView.setCalendarDays(events)
        }
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

    private fun filterTasksByDate() {
        viewModel.tasks.value?.let { allTasks ->
            val filteredTasks = allTasks.filter { task ->
                task.due_date == selectedDate && !task.isCompleted
            }
            taskAdapter.submitList(filteredTasks)

            binding.tvSelectedDate.text = "Deadline: $selectedDate"
            binding.tvEmptyState.visibility =
                if (filteredTasks.isEmpty()) View.VISIBLE else View.GONE

            if (filteredTasks.isEmpty()) {
                binding.tvEmptyState.text = "Tidak ada deadline pada tanggal ini"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}