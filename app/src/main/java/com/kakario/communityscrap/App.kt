package com.kakario.communityscrap

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Net.init(this)
        Store.init(this)
    }

    // 썸네일도 같은 접속 설정(브라우저 정보·출처·쿠키)으로 불러온다
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient { Net.client }.crossfade(true).build()
}
