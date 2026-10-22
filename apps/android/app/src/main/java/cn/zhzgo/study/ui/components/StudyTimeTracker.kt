package cn.zhzgo.study.ui.components
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cn.zhzgo.study.data.StudyPreferences
import cn.zhzgo.study.data.UserPreferences
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
/** Counts only time spent on a resumed, readable practice page. No background or loading time. */
@Composable
fun TrackStudyTime(enabled:Boolean) {
    val context=LocalContext.current.applicationContext
    val owner=LocalLifecycleOwner.current
    val study=remember(context){StudyPreferences(context)}
    LaunchedEffect(owner,enabled) {
        if(!enabled)return@LaunchedEffect
        val account=UserPreferences(context).userName.first() ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var checkpoint=SystemClock.elapsedRealtime()
            try {
                while(true) {
                    delay(10_000)
                    val now=SystemClock.elapsedRealtime();val seconds=(now-checkpoint)/1000
                    if(seconds>0){try{study.addStudySeconds(account,seconds)}catch(e:java.io.IOException){};checkpoint+=seconds*1000}
                }
            } finally {
                val seconds=(SystemClock.elapsedRealtime()-checkpoint)/1000
                if(seconds>0)withContext(NonCancellable){try{study.addStudySeconds(account,seconds)}catch(e:java.io.IOException){}}
            }
        }
    }
}
