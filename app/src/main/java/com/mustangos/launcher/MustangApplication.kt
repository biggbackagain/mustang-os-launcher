package com.mustangos.launcher

import android.app.Application
import com.mustangos.launcher.diagnostics.ErrorJournal

class MustangApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            ErrorJournal.record(this, "CRASH_${error.javaClass.simpleName}")
            previous?.uncaughtException(thread, error)
        }
    }
}
