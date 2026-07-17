package com.reversetutor.preview.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class RtIconKey(
    val imageVector: ImageVector,
    val defaultDescription: String
) {
    ArrowBack(Icons.AutoMirrored.Filled.ArrowBack, "返回"),
    ArrowForward(Icons.AutoMirrored.Filled.ArrowForward, "前进"),
    Add(Icons.Default.Add, "添加"),
    Close(Icons.Default.Close, "关闭"),
    Check(Icons.Default.Check, "确认"),
    CheckCircle(Icons.Default.CheckCircle, "已完成"),
    More(Icons.Default.MoreVert, "更多选项"),
    Menu(Icons.Default.Menu, "菜单"),
    Search(Icons.Default.Search, "搜索"),
    Settings(Icons.Default.Settings, "设置"),
    Home(Icons.Default.Home, "首页"),
    Person(Icons.Default.Person, "个人"),
    People(Icons.Default.People, "社区"),
    School(Icons.Default.School, "学习"),
    Trophy(Icons.Default.EmojiEvents, "挑战"),
    Star(Icons.Default.Star, "收藏"),
    Folder(Icons.Default.Folder, "资料"),
    FileUpload(Icons.Default.FileUpload, "上传"),
    UploadFile(Icons.Default.UploadFile, "上传文件"),
    Image(Icons.Default.Image, "图片"),
    Link(Icons.Default.Link, "链接"),
    Label(Icons.AutoMirrored.Filled.Label, "标签"),
    Psychology(Icons.Default.Psychology, "人格"),
    SmartToy(Icons.Default.SmartToy, "AI"),
    GraphicEq(Icons.Default.GraphicEq, "图谱"),
    Speed(Icons.Default.Speed, "进度"),
    Help(Icons.AutoMirrored.Filled.HelpOutline, "帮助"),
    Info(Icons.Default.Info, "信息"),
    Warning(Icons.Default.Warning, "警告"),
    Delete(Icons.Default.Delete, "删除"),
    Edit(Icons.Default.Create, "编辑"),
    Copy(Icons.Default.ContentCopy, "复制"),
    Share(Icons.Default.Share, "分享"),
    OpenExternal(Icons.AutoMirrored.Filled.OpenInNew, "外部打开"),
    Save(Icons.Default.Save, "保存"),
    Filter(Icons.Default.FilterList, "筛选"),
    LightMode(Icons.Default.LightMode, "浅色模式"),
    DarkMode(Icons.Default.DarkMode, "暗色模式")
}

@Composable
fun RtIcon(
    key: RtIconKey,
    modifier: Modifier = Modifier,
    contentDescription: String? = key.defaultDescription,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = key.imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

@Composable
fun RtIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

@Composable
fun RtIconButton(
    key: RtIconKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = key.defaultDescription,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 24.dp
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        RtIcon(
            key = key,
            contentDescription = contentDescription,
            tint = tint,
            size = size
        )
    }
}
