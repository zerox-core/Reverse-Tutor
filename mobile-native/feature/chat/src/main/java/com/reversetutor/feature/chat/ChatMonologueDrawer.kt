package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Expression-loop slice 4 (SPEC section 4.7 route C): the thinking-chain drawer.
 *
 * Collapsed by default, rendered under the spoken bubble. The [Surface] click
 * is consumed locally so tapping the drawer never toggles bubble selection.
 *
 * 2026-09-21 思考链体验优化：展开状态改为受控（调用方会话级共享）——用户
 * 展开一次，后续消息与流式中的抽屉都保持展开，生成结束落抽屉不再闪断；
 * [streaming] = true 时副标题提示「正在思考…」。
 */
@Composable
internal fun MonologueDrawer(
    monologue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    streaming: Boolean = false
) {
    Surface(
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF4F6FA),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFFD8DEE9))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF4D66A6),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "内心独白",
                    color = Color(0xFF3D4657),
                    fontSize = 11.sp
                )
                Text(
                    when {
                        expanded -> "收起"
                        streaming -> "正在思考…"
                        else -> "我此刻在想什么"
                    },
                    color = Color(0xFF6D778C),
                    fontSize = 10.sp
                )
            }
            if (expanded) {
                Text(
                    monologue,
                    modifier = Modifier.padding(top = 6.dp),
                    color = Color(0xFF3D4657),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontStyle = FontStyle.Normal
                )
            }
        }
    }
}
