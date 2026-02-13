package com.sjtu.canvas.helper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import com.sjtu.canvas.helper.R
import com.sjtu.canvas.helper.data.model.Course
import com.sjtu.canvas.helper.ui.viewmodel.CoursesUiState
import com.sjtu.canvas.helper.ui.viewmodel.CoursesViewModel
import java.time.OffsetDateTime

@Composable
fun CoursesScreen(
    onAssignmentsClick: (Long) -> Unit,
    onVideosClick: (Long) -> Unit,
    onFilesClick: (Long) -> Unit,
    viewModel: CoursesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.courses_title)) },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is CoursesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is CoursesUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            is CoursesUiState.Success -> {
                if (state.courses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.courses_empty))
                    }
                } else {
                    val groupedCourses = remember(state.courses) { groupCoursesByTimeline(state.courses) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedCourses.forEach { section ->
                            item {
                                TimelineSectionHeader(title = section.title)
                            }
                            items(section.courses) { course ->
                                TimelineCourseItem {
                                    CourseCard(
                                        course = course,
                                        onAssignmentsClick = { onAssignmentsClick(course.id) },
                                        onVideosClick = { onVideosClick(course.id) },
                                        onFilesClick = { onFilesClick(course.id) }
                                    )
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
fun CourseCard(
    course: Course,
    onAssignmentsClick: () -> Unit,
    onVideosClick: () -> Unit,
    onFilesClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.primary
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = course.name?.takeIf { it.isNotBlank() } ?: "未命名课程",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAssignmentsClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.nav_assignments), maxLines = 1)
                }
                Button(
                    onClick = onVideosClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.nav_videos), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onFilesClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.nav_files), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TimelineSectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.primary
        )
        Divider(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TimelineCourseItem(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .padding(end = 10.dp, top = 8.dp)
                .width(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colors.primary)
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(2.dp)
                    .height(80.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private data class CourseTimelineSection(
    val title: String,
    val courses: List<Course>
)

private fun groupCoursesByTimeline(courses: List<Course>): List<CourseTimelineSection> {
    val sorted = courses.sortedByDescending { courseTimestamp(it) }
    val groups = linkedMapOf<String, MutableList<Course>>()
    for (course in sorted) {
        val key = timelineKey(course)
        groups.getOrPut(key) { mutableListOf() }.add(course)
    }
    return groups.map { CourseTimelineSection(it.key, it.value) }
}

private fun timelineKey(course: Course): String {
    val termName = course.term?.name?.trim().orEmpty()
    if (termName.isNotBlank()) return termName
    val date = course.term?.startAt ?: course.startAt ?: course.term?.endAt ?: course.endAt
    val year = date?.take(4) ?: "未知年份"
    return "$year 学年"
}

private fun courseTimestamp(course: Course): Long {
    val date = course.term?.startAt ?: course.startAt ?: course.term?.endAt ?: course.endAt
    return parseTimestamp(date)
}

private fun parseTimestamp(value: String?): Long {
    if (value.isNullOrBlank()) return Long.MIN_VALUE
    return runCatching {
        OffsetDateTime.parse(value).toEpochSecond()
    }.getOrDefault(Long.MIN_VALUE)
}
