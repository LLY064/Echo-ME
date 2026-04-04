package com.ml.shubham0204.docqa.di

import android.content.ContentResolver
import android.content.Context
import com.ml.shubham0204.docqa.data.DualMemoryDB
import com.ml.shubham0204.docqa.data.LLMManager
import com.ml.shubham0204.docqa.domain.cache.HierarchicalCacheManager
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.ml.shubham0204.docqa")
class AppModule {
    @Factory
    fun contentResolver(ctx: Context): ContentResolver = ctx.contentResolver

    @Single
    fun dualMemoryDB(ctx: Context): DualMemoryDB = DualMemoryDB(ctx)

    @Single
    fun cacheManager(ctx: Context): HierarchicalCacheManager = HierarchicalCacheManager(ctx)

    @Single
    fun llmManager(ctx: Context): LLMManager = LLMManager(ctx)
}