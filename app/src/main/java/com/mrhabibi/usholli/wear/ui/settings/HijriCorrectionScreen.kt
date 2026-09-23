package com.mrhabibi.usholli.wear.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.ui.UsholliViewModel
import com.mrhabibi.usholli.wear.ui.theme.OnBackground
import com.mrhabibi.usholli.wear.ui.theme.Primary
import com.mrhabibi.usholli.wear.ui.theme.SurfaceVariant

private val hijriOptions = listOf(
    -2 to R.string.hijri_correction_m2,
    -1 to R.string.hijri_correction_m1,
    0 to R.string.hijri_correction_0,
    1 to R.string.hijri_correction_p1,
    2 to R.string.hijri_correction_p2,
)

@Composable
fun HijriCorrectionScreen(viewModel: UsholliViewModel, navController: NavHostController) {
    val current = viewModel.settings.hijriCorrection

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = stringResource(R.string.hijri_correction),
                color = OnBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        items(hijriOptions.size) { index ->
            val (value, labelRes) = hijriOptions[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.setHijriCorrection(value) }
                    .background(if (current == value) Primary else SurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(labelRes),
                    color = OnBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (current == value) {
                    Image(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(OnBackground),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
