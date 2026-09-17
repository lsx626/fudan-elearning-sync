package edu.fudan.elearning.sync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.fudan.elearning.sync.data.CourseStats
import edu.fudan.elearning.sync.data.FileItem
import edu.fudan.elearning.sync.util.FileUtils

private enum class Tab { COURSES, STORAGE, SETTINGS }

/** 主界面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val courses by viewModel.courses.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val loginState by viewModel.loginState.collectAsState()

    var selectedTab by remember { mutableStateOf(Tab.COURSES) }
    var selectedCourse by remember { mutableStateOf<CourseStats?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedCourse?.course?.name ?: "复小学",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (selectedCourse != null) {
                        IconButton(onClick = { selectedCourse = null }) {
                            Text("←", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.sync() }, enabled = !syncing) {
                        Text("立即同步")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (selectedCourse == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == Tab.COURSES,
                        onClick = { selectedTab = Tab.COURSES },
                        icon = { Text("课程") },
                        label = { Text("课程") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.STORAGE,
                        onClick = { selectedTab = Tab.STORAGE },
                        icon = { Text("存储") },
                        label = { Text("存储") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.SETTINGS,
                        onClick = { selectedTab = Tab.SETTINGS },
                        icon = { Text("设置") },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (syncing) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        syncProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                selectedCourse != null -> FileListView(viewModel, selectedCourse!!)
                selectedTab == Tab.COURSES -> CourseListView(viewModel, courses) { selectedCourse = it }
                selectedTab == Tab.STORAGE -> StorageView(viewModel, courses)
                else -> SettingsView(viewModel, loginState)
            }
        }
    }
}

/** 课程列表。 */
@Composable
private fun CourseListView(
    viewModel: AppViewModel,
    courses: List<CourseStats>,
    onOpen: (CourseStats) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(courses) { stats ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(stats) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stats.course.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (stats.course.term.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stats.course.term,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "文件 ${stats.filesTotal} · 已下载 ${stats.filesDone} · ${FileUtils.formatBytes(stats.bytesDownloaded)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (courses.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("暂无课程，点击右上角\"立即同步\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 文件列表（某课程）。 */
@Composable
private fun FileListView(viewModel: AppViewModel, stats: CourseStats) {
    val files = remember(stats.course.id) { viewModel.filesOf(stats.course.id) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (files.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("该课程暂无文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(files) { file ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${FileUtils.formatBytes(file.size)} · ${file.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { FileUtils.openFile(viewModel.getApplication(), file) }) {
                        Text("预览")
                    }
                    TextButton(onClick = { FileUtils.shareFile(viewModel.getApplication(), file) }) {
                        Text("分享")
                    }
                }
            }
        }
    }
}

/** 存储管理：学期筛选 + 批量删除。 */
@Composable
private fun StorageView(viewModel: AppViewModel, courses: List<CourseStats>) {
    val terms = remember { courses.map { it.course.term }.filter { it.isNotEmpty() }.distinct() }
    var selectedTerm by remember { mutableStateOf<String?>(null) }
    var expandedCourse by remember { mutableStateOf<CourseStats?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("按学期筛选", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("全部", selectedTerm == null) { selectedTerm = null }
            terms.forEach { term ->
                FilterChip(term, selectedTerm == term) { selectedTerm = term }
            }
        }
        Spacer(Modifier.height(16.dp))

        val filtered = courses.filter { selectedTerm == null || it.course.term == selectedTerm }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { stats ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stats.course.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${stats.course.term} · ${FileUtils.formatBytes(stats.bytesDownloaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            TextButton(onClick = { expandedCourse = stats }) { Text("查看文件") }
                            TextButton(onClick = { viewModel.deleteCourseFiles(stats.course.id) }) {
                                Text("删除全部", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (expandedCourse?.course?.id == stats.course.id) {
                            val files = viewModel.filesOf(stats.course.id)
                            files.forEach { file ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    TextButton(onClick = { viewModel.deleteFile(file) }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 设置：同步频率、退出登录。 */
@Composable
private fun SettingsView(viewModel: AppViewModel, loginState: LoginState) {
    var interval by remember { mutableStateOf(viewModel.currentInterval()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("账号", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("已登录", style = MaterialTheme.typography.titleSmall)
                    val username = (loginState as? LoginState.LoggedIn)?.username ?: ""
                    Text(username, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { viewModel.logout() }) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("同步频率（分钟）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(15, 30, 60, 120).forEach { m ->
                    FilterChip("$m", interval == m) {
                        interval = m
                        viewModel.setSyncInterval(m)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("本地存储", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "下载目录：${viewModel.downloadRoot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("已下载文件可直接在应用内预览，也可通过系统分享面板分享。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
