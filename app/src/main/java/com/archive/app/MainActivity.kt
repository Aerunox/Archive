package com.archive.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.archive.app.data.localizedForAppLanguage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 界面语言在这里落地：Activity 自身成为「已套用所选语言」的 Context，
     * 于是 `stringResource` 按该语言解析，而 `LocalContext` 仍然是 Activity —— Hilt 需要这一点
     * （早先换成 `createConfigurationContext` 生成的 ContextImpl 会让 `hiltViewModel()` 抛异常）。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.localizedForAppLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            com.archive.app.ui.ArchiveRoot()
        }
    }
}
