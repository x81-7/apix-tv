package com.apix.app.vod.plugin

import android.content.Context
import android.util.Log
import com.apix.app.vod.extractors.ApixProvider
import dalvik.system.DexClassLoader
import java.io.File

class ProviderLoader(private val context: Context) {
    private val optimizedDir = context.getDir("dex", Context.MODE_PRIVATE)

    fun loadProviders(pluginFiles: List<Pair<File, String>>): List<ApixProvider> {
        val loadedProviders = mutableListOf<ApixProvider>()
        
        for ((file, className) in pluginFiles) {
            try {
                val classLoader = DexClassLoader(
                    file.absolutePath,
                    optimizedDir.absolutePath,
                    null,
                    context.classLoader
                )
                
                val loadedClass = classLoader.loadClass(className)
                val providerInstance = loadedClass.getDeclaredConstructor().newInstance()
                
                if (providerInstance is ApixProvider) {
                    loadedProviders.add(providerInstance)
                }
            } catch (e: Exception) {
                Log.e("ProviderLoader", "Failed to load class: $className from ${file.name}", e)
            }
        }
        
        return loadedProviders
    }
}
