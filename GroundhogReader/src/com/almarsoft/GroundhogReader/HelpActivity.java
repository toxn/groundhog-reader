package com.almarsoft.GroundhogReader;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import com.almarsoft.GroundhogReader.lib.UsenetConstants;


public class HelpActivity extends Activity {
    private static final int ID_DIALOG_LOADING = 0;
	/** Activity showing one message */
	
	private WebView mContent;
	private Button mButton_Close;
	private WebSettings mWebSettings;
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	setContentView(R.layout.quickhelp);
    	
        mContent = (WebView) this.findViewById(R.id.help_content);
        
        mWebSettings = mContent.getSettings();
        mWebSettings.setDefaultTextEncodingName("utf-8");
        mWebSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        mWebSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebSettings.setJavaScriptEnabled(false);
        mWebSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        mWebSettings.setSupportZoom(true);
        
        mButton_Close = (Button) this.findViewById(R.id.btn_close);        
		mButton_Close.setOnClickListener(new OnClickListener() {
		    	
			public void onClick(View arg0) {
				finish();
			}
        });
		    
        mButton_Close.setFocusable(false);
        mContent.requestFocus();
        
        this.showDialog(ID_DIALOG_LOADING);
        
        mContent.setWebViewClient(new WebViewClient() {  
		     @Override  
		     public void onPageFinished(WebView view, String url)  
		     {
		    	 HelpActivity.this.dismissDialog(ID_DIALOG_LOADING);
		     }  
       	});          
        mContent.loadUrl(UsenetConstants.QUICKHELPURL);
    }
    
    
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == ID_DIALOG_LOADING){
			
			ProgressDialog loadingDialog = new ProgressDialog(this);
			loadingDialog.setMessage("Loading help...");
			loadingDialog.setIndeterminate(true);
			loadingDialog.setCancelable(true);
			return loadingDialog;
		}

		return super.onCreateDialog(id);
	}
    
}