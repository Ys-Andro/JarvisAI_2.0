package com.example

import android.app.Application
import com.example.jarvisai.di.AppContainer

class JarvisApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContainer = AppContainer(this)
    }

    companion object {
        lateinit var instance: JarvisApplication
            private set
    }
}
