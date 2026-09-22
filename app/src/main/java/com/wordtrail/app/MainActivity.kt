package com.wordtrail.app

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.Ad

class MainActivity : AppCompatActivity() {

    private val BANNER_AD_UNIT_ID = "ca-app-pub-1493112125477027/5623281128"
    private val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1493112125477027/5240137746"
    private val REWARDED_AD_UNIT_ID = "ca-app-pub-1493112125477027/7100014325"
    private val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1493112125477027/9917749358"
    private val NATIVE_AD_UNIT_ID = "ca-app-pub-1493112125477027/8667237701"
    private val APP_OPEN_AD_UNIT_ID = "ca-app-pub-1493112125477027/5849502679"

    private val START_IO_APP_ID = "208872776"

    private lateinit var webView: WebView
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardEarned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}
        StartAppSDK.init(this, START_IO_APP_ID, true)

        setupBanner()
        loadInterstitial()
        loadRewarded()
        setupWebView()
    }

    private fun setupWebView() {
        webView = findViewById(R.id.gameWebView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(NativeAdsBridge(), "NativeAds")
        webView.loadUrl("file:///android_asset/wordtrail.html")
    }

    inner class NativeAdsBridge {

        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread {
                val ad = rewardedAd
                if (ad == null) {
                    showStartIoRewardedOrFail()
                    loadRewarded()
                    return@runOnUiThread
                }
                rewardEarned = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewarded()
                        if (rewardEarned) notifyJs("onRewardedAdComplete")
                        else notifyJs("onRewardedAdFailed")
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        rewardedAd = null
                        loadRewarded()
                        notifyJs("onRewardedAdFailed")
                    }
                }
                ad.show(this@MainActivity, OnUserEarnedRewardListener { _ ->
                    rewardEarned = true
                })
            }
        }

        @JavascriptInterface
        fun showInterstitial() {
            runOnUiThread {
                val ad = interstitialAd
                if (ad != null) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial()
                            notifyJs("onInterstitialClosed")
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            loadInterstitial()
                            notifyJs("onInterstitialClosed")
                        }
                    }
                    ad.show(this@MainActivity)
                    return@runOnUiThread
                }
                showStartIoInterstitialOrContinue()
            }
        }
    }

    private fun notifyJs(jsFunctionName: String) {
        runOnUiThread { webView.evaluateJavascript("$jsFunctionName();", null) }
    }

    private fun setupBanner() {
        val container = findViewById<FrameLayout>(R.id.bannerContainer)
        val adView = AdView(this)
        adView.adUnitId = BANNER_AD_UNIT_ID
        adView.setAdSize(AdSize.BANNER)
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                container.removeAllViews()
                showStartIoBanner(container)
            }
        }
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun showStartIoBanner(container: FrameLayout) {
        val startBanner = com.startapp.sdk.ads.banner.Banner(this)
        container.addView(startBanner)
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            this, INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun loadRewarded() {
        RewardedAd.load(
            this, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun showStartIoRewardedOrFail() {
        val startAd = StartAppAd(this)
        startAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                startAd.showAd()
                notifyJs("onRewardedAdComplete")
            }
            override fun onFailedToReceiveAd(ad: Ad?) {
                notifyJs("onRewardedAdFailed")
            }
        })
    }

    private fun showStartIoInterstitialOrContinue() {
        val startAd = StartAppAd(this)
        startAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                startAd.showAd()
                notifyJs("onInterstitialClosed")
            }
            override fun onFailedToReceiveAd(ad: Ad?) {
                notifyJs("onInterstitialClosed")
            }
        })
    }
}
