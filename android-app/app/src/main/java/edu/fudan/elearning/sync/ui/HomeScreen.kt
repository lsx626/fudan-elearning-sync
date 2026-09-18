package edu.fudan.elearning.sync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.fudan.elearning.sync.R
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回课程列表"
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.sync() }, enabled = !syncing) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (syncing) "同步中" else "立即同步")
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
                        icon = { navIcon(R.drawable.ic_school, selectedTab == Tab.COURSES) },
                        label = { Text("课程") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.STORAGE,
                        onClick = { selectedTab = Tab.STORAGE },
                        icon = { navIcon(R.drawable.ic_folder, selectedTab == Tab.STORAGE) },
                        label = { Text("存储") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.SETTINGS,
                        onClick = { selectedTab = Tab.SETTINGS },
                        icon = {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = if (selectedTab == Tab.SETTINGS)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (syncing) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        syncProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

/** 底部导航自绘图标（按选中态着色）。 */
@Composable
private fun navIcon(drawableRes: Int, selected: Boolean) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
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
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stats.course.name.firstOrNull()?.toString() ?: "#",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
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
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "文件 ${stats.filesTotal} · 已下载 ${stats.filesDone} · ${FileUtils.formatBytes(stats.bytesDownloaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        if (courses.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_school),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无课程",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击右上角「立即同步」拉取课程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 文件类型徽标颜色。 */
private fun typeBadgeColor(ext: String): Color = when (ext) {
    "pdf" -> Color(0xFFD64545)
    "ppt", "pptx" -> Color(0xFFE07B39)
    "doc", "docx" -> Color(0xFF3B82F6)
    "xls", "xlsx" -> Color(0xFF22A06B)
    else -> Color(0xFF8A8F98)
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
                    val ext = file.filename.substringAfterLast('.', "").lowercase()
                    if (ext.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(typeBadgeColor(ext).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ext.take(3).uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = typeBadgeColor(ext)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${FileUtils.formatBytes(file.size)} · ${FileUtils.statusText(file.status)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { FileUtils.openFile(viewModel.getApplication(), file) }) {
                        Text("预览")
                    }
                    TextButton(onClick = { FileUtils.shareFile(viewModel.getApplication(), file) }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "分享",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 存储管理：学期筛选 + 批量删除（删除前需二次确认）。 */
@Composable
private fun StorageView(viewModel: AppViewModel, courses: List<CourseStats>) {
    val terms = remember { courses.map { it.course.term }.filter { it.isNotEmpty() }.distinct() }
    var selectedTerm by remember { mutableStateOf<String?>(null) }
    var expandedCourse by remember { mutableStateOf<CourseStats?>(null) }
    var pendingDeleteCourse by remember { mutableStateOf<CourseStats?>(null) }
    var pendingDeleteFile by remember { mutableStateOf<FileItem?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("按学期筛选", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selectedTerm == null, onClick = { selectedTerm = null }, label = { Text("全部") }) }
            items(terms) { term ->
                FilterChip(selected = selectedTerm == term, onClick = { selectedTerm = term }, label = { Text(term) })
            }
        }
        Spacer(Modifier.height(16.dp))

        val filtered = courses.filter { selectedTerm == null || it.course.term == selectedTerm }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                            TextButton(onClick = { pendingDeleteCourse = stats }) {
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
                                    TextButton(onClick = { pendingDeleteFile = file }) {
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

    pendingDeleteCourse?.let { stats ->
        ConfirmDialog(
            title = "删除课程文件",
            message = "将删除「${stats.course.name}」下的全部已下载文件，此操作不可撤销。",
            onConfirm = {
                viewModel.deleteCourseFiles(stats.course.id)
                pendingDeleteCourse = null
            },
            onDismiss = { pendingDeleteCourse = null }
        )
    }
    pendingDeleteFile?.let { file ->
        ConfirmDialog(
            title = "删除文件",
            message = "将删除「${file.name}」，此操作不可撤销。",
            onConfirm = {
                viewModel.deleteFile(file)
                pendingDeleteFile = null
            },
            onDismiss = { pendingDeleteFile = null }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 设置：账号、同步频率、本地存储。 */
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(15, 30, 60, 120)) { m ->
                FilterChip(selected = interval == m, onClick = {
                    interval = m
                    viewModel.setSyncInterval(m)
                }, label = { Text("$m") })
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