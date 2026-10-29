package com.muyex.randommusicreminder.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LexingApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            // 进程首次启动也要重排，覆盖用户修改权限后直接打开应用的情况。
            runCatching { container.scheduleReconciler.reconcileAll() }
        }
    }

    override fun onTerminate() {
        container.musicPreviewController.release()
        applicationScope.cancel()
        super.onTerminate()
    }
}
