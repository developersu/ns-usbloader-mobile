package com.blogspot.developersu.ns_usbloader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;


public class AboutActivity extends AppCompatActivity {
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if (item.getItemId() == android.R.id.home)
            finish();
        return true;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final TextView t1 = findViewById(R.id.textView1);
        t1.append(" v"+BuildConfig.VERSION_NAME);
        final TextView t2 = findViewById(R.id.textView2);
        t2.setMovementMethod(LinkMovementMethod.getInstance());
        final TextView t4 = findViewById(R.id.textView4);
        t4.setMovementMethod(LinkMovementMethod.getInstance());
        final TextView t6 = findViewById(R.id.textView6);
        t6.setMovementMethod(LinkMovementMethod.getInstance());
        final TextView tTranslators = findViewById(R.id.textViewTranslators);
        tTranslators.setMovementMethod(LinkMovementMethod.getInstance());

        ImageView donateLibera = findViewById(R.id.donateLiberaImageView);
        donateLibera.setOnClickListener(this::donateLiberaOnClickAction);

        ImageView donatePaypal = findViewById(R.id.donatePaypalImageView);
        donatePaypal.setOnClickListener(this::donatePaypalOnClickAction);

        ImageView donateYandex = findViewById(R.id.donateYandexImageView);
        donateYandex.setOnClickListener(this::donateYandexOnClickAction);
    }

    private void donateLiberaOnClickAction(View view){
        String url = "https://liberapay.com/developersu/donate";
        createOpenBrowserIntent(url);
    }
    private void donatePaypalOnClickAction(View view){
        String url = "https://www.paypal.me/developersu";
        createOpenBrowserIntent(url);
    }
    private void donateYandexOnClickAction(View view){
        String url = "https://money.yandex.ru/to/410014301951665";
        createOpenBrowserIntent(url);
    }
    private void createOpenBrowserIntent(String url){
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        intent.setData(Uri.parse(url));

        startActivity(intent);
    }
}
