package com.blogspot.developersu.ns_usbloader;

import static java.util.Objects.requireNonNull;

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
        requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
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
        final TextView tContributors = findViewById(R.id.textViewContributorsNames);
        tContributors.setMovementMethod(LinkMovementMethod.getInstance());

        ImageView donateLibera = findViewById(R.id.donateBoostyImageView);
        donateLibera.setOnClickListener(this::donateBoostyLink);
    }

    private void donateBoostyLink(View view){
        String url = "https://boosty.to/developersu";
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
