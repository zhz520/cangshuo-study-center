package cn.zhzgo.study.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.AppDatabase
import cn.zhzgo.study.data.ToolEntity
import cn.zhzgo.study.data.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val toolDao = AppDatabase.getDatabase(application).toolDao()
    private val userPreferences = UserPreferences(application)

    // Two-level sorted tools: first by category order, then by tool order within category
    val allTools: StateFlow<List<ToolEntity>> = combine(
        toolDao.getAllTools(),
        userPreferences.categoryOrder,
        userPreferences.toolsOrder
    ) { tools, catOrderStr, toolOrderStr ->
        val toolsByCategory = tools.groupBy { it.category }

        // Sort categories
        val sortedCategories = if (!catOrderStr.isNullOrBlank()) {
            val catOrder = catOrderStr.split(",")
            toolsByCategory.keys.sortedBy { cat ->
                val idx = catOrder.indexOf(cat)
                if (idx == -1) Int.MAX_VALUE else idx
            }
        } else {
            toolsByCategory.keys.toList()
        }

        // For each category, sort tools within it
        val toolOrder = if (!toolOrderStr.isNullOrBlank()) toolOrderStr.split(",") else emptyList()

        sortedCategories.flatMap { category ->
            val categoryTools = toolsByCategory[category] ?: emptyList()
            if (toolOrder.isNotEmpty()) {
                categoryTools.sortedBy { tool ->
                    val idx = toolOrder.indexOf(tool.id)
                    if (idx == -1) Int.MAX_VALUE else idx
                }
            } else {
                categoryTools
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTools: StateFlow<List<ToolEntity>> = toolDao.getTopTools(4)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getToolsByIds(toolIds: List<String>): StateFlow<List<ToolEntity>> {
        return toolDao.getToolsByIds(toolIds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun toggleFavorite(toolId: String, isFavorite: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            toolDao.setFavorite(toolId, !isFavorite)
        }
    }

    fun updateToolsOrder(newOrder: List<String>) {
        viewModelScope.launch {
            userPreferences.setToolsOrder(newOrder.joinToString(","))
        }
    }

    fun updateCategoryOrder(newOrder: List<String>) {
        viewModelScope.launch {
            userPreferences.setCategoryOrder(newOrder.joinToString(","))
        }
    }
}
