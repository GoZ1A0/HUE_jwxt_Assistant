package com.hebeu.emptyclassroom.ui.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.R

@Composable
fun AppBrandIcon(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.app_icon), contentDescription = "HUE教务助手", modifier = modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
}
