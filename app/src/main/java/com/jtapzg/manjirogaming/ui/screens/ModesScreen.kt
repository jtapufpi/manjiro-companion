package com.jtapzg.manjirogaming.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.ui.MainViewModel
import com.jtapzg.manjirogaming.ui.components.ModeCard
import com.jtapzg.manjirogaming.ui.theme.MgColors

@Composable
fun ModesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val selected by vm.globalMode.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.tab_modes),
            color = MgColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(Mode.entries.toList(), key = { it.key }) { mode ->
                ModeCard(
                    mode = mode,
                    selected = mode == selected,
                    onClick = { vm.applyGlobalMode(mode) }
                )
            }
        }
    }
}
