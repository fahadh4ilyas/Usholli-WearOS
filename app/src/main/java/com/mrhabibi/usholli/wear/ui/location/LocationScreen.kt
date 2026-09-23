package com.mrhabibi.usholli.wear.ui.location

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.mrhabibi.usholli.wear.R
import com.mrhabibi.usholli.wear.ui.UsholliViewModel
import com.mrhabibi.usholli.wear.ui.theme.OnBackground
import com.mrhabibi.usholli.wear.ui.theme.SurfaceVariant
import com.mrhabibi.usholli.wear.ui.theme.TextDim

@Composable
fun LocationScreen(viewModel: UsholliViewModel, navController: NavHostController) {
    val cities = viewModel.cities
    val loading = viewModel.citiesLoading

    LaunchedEffect(Unit) {
        viewModel.loadCities()
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { query -> viewModel.searchCities(query) }
    }

    val launchVoiceSearch: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
        runCatching { speechLauncher.launch(intent) }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.city),
                    color = OnBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = launchVoiceSearch,
                    colors = ButtonDefaults.buttonColors(backgroundColor = SurfaceVariant),
                ) {
                    Image(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search_city),
                        colorFilter = ColorFilter.tint(OnBackground),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.search_city), color = OnBackground, fontSize = 12.sp)
                }
            }
        }

        if (loading && cities.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.loading),
                    color = TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else if (cities.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.not_found),
                    color = TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            items(cities.size) { index ->
                val city = cities[index]
                CityRow(
                    cityName = city.lokasi,
                    onClick = {
                        viewModel.selectCity(city)
                        navController.popBackStack()
                    },
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun CityRow(cityName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(SurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cityName,
            color = OnBackground,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
