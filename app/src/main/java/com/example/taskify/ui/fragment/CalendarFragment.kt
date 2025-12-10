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
import com.example.taskify.data.model.Task     
import com.example.taskify.data.model.Holiday  
import androidx.core.content.ContextCompat     
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = dateFormat.format(Date())

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
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            currentTasks = tasks
            filterTasksByDate() 
            updateCalendarEvents() 
        }

        viewModel.holidays.observe(viewLifecycleOwner) { holidays ->
            currentHolidays = holidays
            updateCalendarEvents() 
        }
    }

    private fun updateCalendarEvents() {
        val eventsMap = HashMap<String, CalendarDay>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        currentHolidays.forEach { holiday ->
            try {
                val date = sdf.parse(holiday.date)
                if (date != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    val calendarDay = CalendarDay(calendar)

                    calendarDay.labelColor = android.R.color.holo_red_dark

                    eventsMap[holiday.date] = calendarDay
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        currentTasks.forEach { task ->
            if (!task.isCompleted) {
                try {
                    val dateStr = task.due_date

                    val existingDay = eventsMap[dateStr]

                    if (existingDay != null) {
                        existingDay.imageResource = android.R.drawable.ic_menu_my_calendar

                    } else {
                        val date = sdf.parse(dateStr)
                        if (date != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = date
                            val calendarDay = CalendarDay(calendar)

                            calendarDay.imageResource = android.R.drawable.ic_menu_my_calendar

                            eventsMap[dateStr] = calendarDay
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        binding.calendarView.setCalendarDays(eventsMap.values.toList())
    }

    private fun filterTasksByDate() {
        val holiday = currentHolidays.find { it.date == selectedDate }

        val displayText = StringBuilder()

        displayText.append("Tanggal: $selectedDate")

        if (holiday != null) {
            displayText.append("\n${holiday.localName}")
        }

        if (holiday != null) {
            binding.tvSelectedDate.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
            )
        } else {
            val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

            if (isDarkMode) {
                binding.tvSelectedDate.setTextColor(Color.WHITE)
            } else {
                binding.tvSelectedDate.setTextColor(Color.BLACK)
            }
        }

        binding.tvSelectedDate.text = displayText.toString()

        viewModel.tasks.value?.let { allTasks ->
            val filteredTasks = allTasks.filter { task ->
                task.due_date == selectedDate && !task.isCompleted
            }
            taskAdapter.submitList(filteredTasks)

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