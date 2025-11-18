package com.mobcom.taskify.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobcom.taskify.databinding.FragmentCalendarBinding
import com.mobcom.taskify.ui.adapter.TaskAdapter
import com.mobcom.taskify.ui.viewmodel.MainViewModel
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

        setupCalendar()
        setupRecyclerView()
        setupObservers()

        viewModel.loadTasks()
    }

    private fun setupCalendar() {
        // Set initial selected date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = dateFormat.format(Date())

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            selectedDate = dateFormat.format(calendar.time)
            filterTasksByDate()
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { },
            onTaskCheckChanged = { task, isChecked ->
                viewModel.toggleTaskCompletion(task.task_id, isChecked)
            },
            onDeleteClick = { task ->
                viewModel.deleteTask(task)
            }
        )

        binding.rvDailyTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }
    }

    private fun setupObservers() {
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            filterTasksByDate()
        }
    }

    private fun filterTasksByDate() {
        viewModel.tasks.value?.let { allTasks ->
            val filteredTasks = allTasks.filter { task ->
                task.due_date == selectedDate
            }
            taskAdapter.submitList(filteredTasks)

            binding.tvSelectedDate.text = "Tugas pada $selectedDate"
            binding.tvEmptyState.visibility =
                if (filteredTasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}