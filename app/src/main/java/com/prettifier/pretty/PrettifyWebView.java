package com.prettifier.pretty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.fastaccess.helper.AppHelper;
import com.fastaccess.helper.InputHelper;
import com.fastaccess.helper.Logger;
import com.fastaccess.provider.markdown.MarkDownProvider;
import com.fastaccess.provider.scheme.SchemeParser;
import com.fastaccess.ui.modules.code.CodeViewerView;
import com.prettifier.pretty.callback.MarkDownInterceptorInterface;
import com.prettifier.pretty.helper.GithubHelper;
import com.prettifier.pretty.helper.PrettifyHelper;


public class PrettifyWebView extends NestedWebView {
    private OnContentChangedListener onContentChangedListener;
    private boolean interceptTouch;

    public interface OnContentChangedListener {
        void onContentChanged(int progress);
    }

    public PrettifyWebView(Context context) {
        super(context);
        if (isInEditMode()) return;
        initView();
    }

    public PrettifyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PrettifyWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent p_event) {
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(interceptTouch);
        }
        return super.onTouchEvent(event);
    }

    private void initView() {
        if (isInEditMode()) return;
        setWebChromeClient(new ChromeClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setWebViewClient(new WebClient());
        } else {
            setWebViewClient(new WebClientCompat());
        }
        WebSettings settings = getSettings();
        settings.setAppCachePath(getContext().getCacheDir().getPath());
        settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        setOnLongClickListener((view) -> {
            WebView.HitTestResult result = getHitTestResult();
            if (hitLinkResult(result) && !InputHelper.isEmpty(result.getExtra())) {
                AppHelper.copyToClipboard(getContext(), result.getExtra());
                return true;
            }
            return false;
        });
    }

    private boolean hitLinkResult(WebView.HitTestResult result) {
        return result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE || result.getType() == HitTestResult.IMAGE_TYPE ||
                result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
    }

    public void setOnContentChangedListener(@NonNull OnContentChangedListener onContentChangedListener) {
        this.onContentChangedListener = onContentChangedListener;
    }

    @SuppressLint("SetJavaScriptEnabled") public void setSource(@NonNull String source) {
        WebSettings settings = getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptEnabled(true);
        if (!InputHelper.isEmpty(source)) {
            String page = PrettifyHelper.generateContent(source);
            post(() -> loadDataWithBaseURL("file:///android_asset/highlight/", page, "text/html", "utf-8", null));
        } else Log.e(getClass().getSimpleName(), "Source can't be null or empty.");
    }

    public void setGithubContent(@NonNull String source, @Nullable String baseUrl) {
        setGithubContent(source, baseUrl, false);
    }

    @SuppressLint("SetJavaScriptEnabled") public void setGithubContent(@NonNull String source, @Nullable String baseUrl, boolean wrap) {
        if (wrap) {
            setScrollbarFadingEnabled(false);
            setVerticalScrollBarEnabled(false);
        } else {
            getSettings().setJavaScriptEnabled(true);
        }
        if (!InputHelper.isEmpty(source)) {
            if (!wrap) addJavascriptInterface(new MarkDownInterceptorInterface(this), "Android");
            String page = GithubHelper.generateContent(source, baseUrl, wrap);
            post(() -> loadDataWithBaseURL("file:///android_asset/md/", page, "text/html", "utf-8", null));
        }
    }

    public void loadImage(@NonNull String url) {
        WebSettings settings = getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        String html = "<html><head><style>img{display: inline; height: auto; max-width: 100%;}</style></head><body><img src=\"" + url +
                "\"/></body></html>";
        loadData(html, "text/html", null);
    }

    public void setInterceptTouch(boolean interceptTouch) {
        this.interceptTouch = interceptTouch;
    }

    private class ChromeClient extends WebChromeClient {
        @Override public void onProgressChanged(WebView view, int progress) {
            super.onProgressChanged(view, progress);
            if (onContentChangedListener != null) {
                onContentChangedListener.onContentChanged(progress);
            }
        }
    }

    private void startActivity(Uri url) {
        if (url == null) return;
        Logger.e(url);
        if (MarkDownProvider.isImage(url.toString())) {
            CodeViewerView.startActivity(getContext(), url.toString());
        } else {
            String lastSegment = url.getEncodedFragment();
            Logger.e(lastSegment);
            if (lastSegment != null) {
                loadUrl("javascript:scrollTo(\"" + lastSegment + "\")");
                return;
            }
            SchemeParser.launchUri(getContext(), url);
        }
    }

    private class WebClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            startActivity(request.getUrl());
            return true;
        }
    }

    private class WebClientCompat extends WebViewClient {
        @SuppressWarnings("deprecation") @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            startActivity(Uri.parse(url));
            return true;
        }
    }

}