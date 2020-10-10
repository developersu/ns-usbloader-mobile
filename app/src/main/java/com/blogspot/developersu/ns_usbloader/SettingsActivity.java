package com.blogspot.developersu.ns_usbloader;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {
    private EditText nsIp;
    private EditText servAddr;
    private EditText servPort;
    private SwitchCompat autoDetectIp;

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if (item.getItemId() == android.R.id.home)
            finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Set NS IP field
        nsIp = findViewById(R.id.nsIpEditText);
        servAddr = findViewById(R.id.servAddrTextEdit);
        servPort = findViewById(R.id.servPortTextEdit);
        autoDetectIp = findViewById(R.id.autoDetectIpSW);

        nsIp.setFilters(new InputFilter[]{inputFilterForIP});
        servAddr.setFilters(new InputFilter[]{inputFilterForIP});
        servPort.setFilters(new InputFilter[]{inputFilterForPort});
        autoDetectIp.setOnCheckedChangeListener((compoundButton, switchState) -> servAddr.setEnabled(! switchState));

        // TODO: Disable controls
        if (savedInstanceState == null){
            SharedPreferences sp = getSharedPreferences("NSUSBloader", MODE_PRIVATE); //.getInt("PROTOCOL", NsConstants.PROTO_TF_USB);
            nsIp.setText(sp.getString("SNsIP", "192.168.1.42"));
            autoDetectIp.setChecked(sp.getBoolean("SAutoIP", true));
            servAddr.setText(sp.getString("SServerIP", "192.168.1.142"));
            servPort.setText(String.valueOf(sp.getInt("SServerPort", 6042)));
        }
        // else { } // not needed

        // Shitcode practices begin
        nsIp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {
                if (! editable.toString().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))
                    nsIp.setTextColor(Color.RED);
                else
                    nsIp.setTextColor(getResources().getColor(R.color.defaultTextColor));
            }
        });
        servAddr.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {
                if (! editable.toString().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))
                    nsIp.setTextColor(Color.RED);
                else
                    nsIp.setTextColor(getResources().getColor(R.color.defaultTextColor));
            }
        });

        servPort.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {
                String contentString = editable.toString();
                //Log.i("LPR", contentString);
                if (contentString.matches("^\\d{1,5}")){
                    if (Integer.parseInt(contentString) < 1024)
                        servPort.setTextColor(Color.RED);
                    else
                        servPort.setTextColor(getResources().getColor(R.color.defaultTextColor));
                }
            }
        });
        // Shitcode practices end
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences.Editor spEditor = getSharedPreferences("NSUSBloader", MODE_PRIVATE).edit();

        if (nsIp.getText().toString().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))
            spEditor.putString("SNsIP", nsIp.getText().toString());

        spEditor.putBoolean("SAutoIP", autoDetectIp.isChecked());

        if (servAddr.getText().toString().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))
            spEditor.putString("SServerIP", servAddr.getText().toString());

        final String contentString = servPort.getText().toString();
        if (contentString.matches("^\\d{1,5}") && (Integer.parseInt(contentString) >= 1024)){
            spEditor.putInt("SServerPort", Integer.parseInt(servPort.getText().toString()));
        }

        spEditor.apply();
    }

    private static InputFilter inputFilterForIP = (charSequence, start, end, destination, dStart, dEnd) -> {
        if (end > start) {
            String destTxt = destination.toString();
            String resultingTxt = destTxt.substring(0, dStart) +
                    charSequence.subSequence(start, end) +
                    destTxt.substring(dEnd);
            if (! resultingTxt.matches ("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?"))
                return "";
            else {
                String[] splits = resultingTxt.split("\\.");
                for (String split : splits) {
                    if (Integer.parseInt(split) > 255)
                        return "";
                }
            }
        }
        return null;
    };

    private static InputFilter inputFilterForPort = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence charSequence, int start, int end, Spanned destination, int dStart, int dEnd) {
            if (end > start) {
                String destTxt = destination.toString();
                String resultingTxt = destTxt.substring(0, dStart) +
                        charSequence.subSequence(start, end) +
                        destTxt.substring(dEnd);
                if (!resultingTxt.matches ("^[0-9]+"))
                    return "";
                if (Integer.parseInt(resultingTxt) > 65535)
                    return "";
            }
            return null;
        }
    };

}
