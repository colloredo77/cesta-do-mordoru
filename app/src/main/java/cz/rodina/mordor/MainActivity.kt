package cz.rodina.mordor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MordorTheme { App(vm) } }
    }
}

@Composable
fun App(vm: MainViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    val family by vm.family.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    val me = members.firstOrNull { it.id == vm.memberId }
    val f = family
    val g = game

    Box(Modifier.fillMaxSize().parchment()) {
        when {
            !vm.ready -> Loading("Zapaluji pochodně…")
            vm.familyCode == null -> FamilySetup(vm)
            f == null -> Loading("Hledám výpravu ${vm.familyCode}…") { vm.leave() }
            me == null && vm.memberId != null && members.isEmpty() -> Loading("Svolávám společenstvo…") { vm.leave() }
            me == null -> ProfileSetup(vm, members)
            g == null -> Loading("Počítám cestu…")
            else -> MainTabs(vm, f, members, g, me)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

@Composable
fun Loading(text: String, onLeave: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Gold)
        Text(text, Modifier.padding(16.dp), color = Ink)
        if (onLeave != null) TextButton(onClick = onLeave) { Text("Zrušit a začít znovu") }
    }
}

@Composable
fun MainTabs(vm: MainViewModel, family: Family, members: List<Member>, game: GameState, me: Member) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val colors = members.mapIndexed { i, m -> m.id to MemberColors[i % MemberColors.size] }.toMap()
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Blood, selectedTextColor = Blood, indicatorColor = Parchment,
        unselectedIconColor = InkSoft, unselectedTextColor = InkSoft,
    )
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = ParchmentDark) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Place, null) }, label = { Text("Mapa") }, colors = itemColors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Person, null) }, label = { Text("Výprava") }, colors = itemColors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Kronika") }, colors = itemColors)
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Nastavení") }, colors = itemColors)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MapScreen(game, colors)
                1 -> PartyScreen(vm, family, game, me, colors)
                2 -> ChronicleScreen(game)
                else -> SettingsScreen(vm, family, members, me)
            }
        }
    }
}
