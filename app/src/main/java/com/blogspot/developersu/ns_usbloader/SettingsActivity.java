package com.blogspot.developersu.ns_usbloader;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.blogspot.developersu.ns_usbloader.view.settings.IpContainingTextWatcher;
import com.blogspot.developersu.ns_usbloader.view.settings.IpInputFilter;
import com.blogspot.developersu.ns_usbloader.view.settings.OnThemeChangedListener;
import com.blogspot.developersu.ns_usbloader.view.settings.PortInputFilter;
import com.blogspot.developersu.ns_usbloader.view.settings.PortTextWatcher;

public class SettingsActivity extends AppCompatActivity {

    private static final String IP_REGEXP = "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}";

    private Spinner themeSpinner;
    private EditText nsIp;
    private EditText serverIp;
    private EditText servPort;
    private SwitchCompat autoDetectIp;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        themeSpinner = findViewById(R.id.applicationThemeSpinner);
        themeSpinner.setOnItemSelectedListener(new OnThemeChangedListener());
        ArrayAdapter<CharSequence> themeAdapter = ArrayAdapter.createFromResource(
                this, R.array.dayNightSelector, android.R.layout.simple_spinner_item);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(themeAdapter);

        nsIp = findViewById(R.id.nsIpEditText);
        serverIp = findViewById(R.id.servAddrTextEdit);
        servPort = findViewById(R.id.servPortTextEdit);
        autoDetectIp = findViewById(R.id.autoDetectIpSW);

        IpInputFilter ipInputFilter = new IpInputFilter();
        nsIp.setFilters(new InputFilter[]{ipInputFilter});
        serverIp.setFilters(new InputFilter[]{ipInputFilter});
        servPort.setFilters(new InputFilter[]{new PortInputFilter()});
        autoDetectIp.setOnCheckedChangeListener((compoundButton, switchState) -> serverIp.setEnabled(! switchState));

        // TODO: Disable controls (?)
        if (savedInstanceState == null) {
            SharedPreferences preferences = getSharedPreferences("NSUSBloader", MODE_PRIVATE);
            themeSpinner.setSelection(preferences.getInt("ApplicationTheme", 0));
            nsIp.setText(preferences.getString("SNsIP", "192.168.1.42"));
            autoDetectIp.setChecked(preferences.getBoolean("SAutoIP", true));
            serverIp.setText(preferences.getString("SServerIP", "192.168.1.142"));
            servPort.setText(String.valueOf(preferences.getInt("SServerPort", 6042)));
        }

        nsIp.addTextChangedListener(new IpContainingTextWatcher(this, nsIp));
        serverIp.addTextChangedListener(new IpContainingTextWatcher(this, serverIp));
        servPort.addTextChangedListener(new PortTextWatcher(this, servPort));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences.Editor editor = getSharedPreferences("NSUSBloader", MODE_PRIVATE).edit();

        editor.putInt("ApplicationTheme", themeSpinner.getSelectedItemPosition());
        editor.putBoolean("SAutoIP", autoDetectIp.isChecked());

        String nsIpNumber = nsIp.getText().toString();
        if (nsIpNumber.matches(IP_REGEXP))
            editor.putString("SNsIP", nsIpNumber);

        String serverIpNumber = serverIp.getText().toString();
        if (serverIpNumber.matches(IP_REGEXP))
            editor.putString("SServerIP", serverIpNumber);

        String portNumber = servPort.getText().toString();
        if (isPortValid(portNumber))
            editor.putInt("SServerPort", parseInt(portNumber));
        editor.apply();
    }
    private boolean isPortValid(String port) {
        try {
            return parseInt(port) >= 1024;
        }
        catch (Exception ignored){
            return false;
        }
    }
}