package com.blogspot.developersu.ns_usbloader;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static com.blogspot.developersu.ns_usbloader.NsConstants.DEFAULT_NS_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.DEFAULT_PHONE_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.DEFAULT_PHONE_PORT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_CONTENT_LIST;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_FINAL_TOAST_DURATION;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_FINAL_TOAST_TEXT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_NS_DEVICE;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_NS_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_PHONE_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_PHONE_PORT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_PROTOCOL;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_PROGRESS;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.REQUEST_NS_ACCESS_INTENT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_PROGRESS_INTENT;
import static com.blogspot.developersu.ns_usbloader.NsUtils.nsSnack;
import static com.blogspot.developersu.ns_usbloader.service.TransferService.ACTION_START_TRANSFER;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blogspot.developersu.ns_usbloader.model.ProtocolSelector;
import com.blogspot.developersu.ns_usbloader.service.TransferService;
import com.blogspot.developersu.ns_usbloader.view.ApplicationTheme;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;
import com.blogspot.developersu.ns_usbloader.view.NsMainIntentFilter;
import com.blogspot.developersu.ns_usbloader.view.NspItemsAdapter;
import com.blogspot.developersu.ns_usbloader.view.NspViewHolder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

// TODO: add ImageAsset for notification icon in addition to of SVG-like
public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener  {

    public static final boolean IS_AFTER_KIT_KAT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    public static final boolean IS_AFTER_TIRAMISU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;

    private RecyclerView recyclerView;
    private NspItemsAdapter nspItemsAdapter;
    private ArrayList<NSPElement> nspElements = new ArrayList<>();
    private BroadcastReceiver innerBroadcastReceiver;
    private Button selectBtn,
            uploadToNsBtn;
    private ProgressBar progressBarMain;
    private NavigationView drawerNavView;
    private ProtocolSelector selector;
    private UsbHelper usbHelper;

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList("DATASET_LIST", nspElements);
        outState.putParcelable("USB_HELPER", usbHelper);
        MenuItem checkedItem = drawerNavView.getCheckedItem();
        outState.putInt("PROTOCOL", checkedItem != null ?
                checkedItem.getItemId():
                R.id.nav_tf_usb);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Configure intent to receive attached NS (moved from 'onReceive()')
        innerBroadcastReceiver = new InnerBroadcastReceiver();
        ContextCompat.registerReceiver(this, innerBroadcastReceiver, new NsMainIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED);
        blockUI(TransferService.isActive());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(innerBroadcastReceiver);
        SharedPreferences.Editor preferencesEditor =
                getSharedPreferences("NSUSBloader", MODE_PRIVATE).edit();
        preferencesEditor.putInt("PROTOCOL", selector.getSelected());
        preferencesEditor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_toolbar, menu);
        menu.findItem(R.id.select_all).setOnMenuItemClickListener(menuItem -> {
            if (nspElements.isEmpty())
                return true;

            for (NSPElement element: nspElements)
                element.setSelected(true);
            nspItemsAdapter.notifyDataSetChanged();
            return true;
        });
        return true;
    }

    // Drawer actions: Handle navigation view item clicks here
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if (R.id.nav_gl == item.getItemId()) {
            if (! nspElements.isEmpty()) {
                for (NSPElement element : nspElements) {
                    String filename = element.getFilename();
                    if (filename.endsWith(".nsz") || filename.endsWith(".xcz"))
                        element.setSelected(false);
                }
                nspItemsAdapter.notifyDataSetChanged();
            }
        }
        else if (R.id.nav_settings == item.getItemId()) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        else if (R.id.nav_about == item.getItemId()) {
            startActivity(new Intent(this, AboutActivity.class));
        }

        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawerNavView = findViewById(R.id.nav_view);
        selector = new ProtocolSelector(drawerNavView);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        drawerNavView.setNavigationItemSelectedListener(this);
        progressBarMain = findViewById(R.id.mainProgressBar);

        if (savedInstanceState != null) {
            nspElements = savedInstanceState.getParcelableArrayList("DATASET_LIST");
            usbHelper = savedInstanceState.getParcelable("USB_HELPER");
            usbHelper.restoreState(getApplicationContext());
            selector.select(savedInstanceState.getInt("PROTOCOL", R.id.nav_tf_usb));
        }
        else { //savedInstanceState == null
            UsbDevice ns = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);    // If it's started initially, then check if it's started from notification.
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                NsUtils.getAlertWindow(this,
                        getResources().getString(R.string.popup_error),
                        "Internal issue: getSystemService(Context.USB_SERVICE) returned null");
            }
            usbHelper = new UsbHelper(getApplicationContext(), ns);

            SharedPreferences preferences = getSharedPreferences("NSUSBloader", MODE_PRIVATE);
            ApplicationTheme.setApplicationTheme(preferences.getInt("ApplicationTheme", 0));

            selector.select(preferences.getInt("PROTOCOL", PROTO_TF_USB));

            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                        final DrawerLayout drawer = findViewById(R.id.drawer_layout); // Handle back button push when drawer opened
                        if (drawer.isDrawerOpen(GravityCompat.START))
                            drawer.closeDrawer(GravityCompat.START);
                        else //or hide activity
                            moveTaskToBack(true);
                }
            });
        }

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        nspItemsAdapter = new NspItemsAdapter(nspElements);
        recyclerView.setAdapter(nspItemsAdapter);
        setSwipeFunctionsToView();
        // Select files button
        selectBtn = findViewById(R.id.buttonSelect);

        ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                activityResult -> {
                    int requestCode = activityResult.getResultCode();
                    Intent data = activityResult.getData();
                    if (requestCode != Activity.RESULT_OK || data == null)
                        return;
                    readFile(data);
                });

        selectBtn.setOnClickListener(e -> {
            Intent fileChooser;
            if (IS_AFTER_KIT_KAT)
                fileChooser = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            else // older versions doesn't support ACTION_OPEN_DOCUMENT
                fileChooser = new Intent(Intent.ACTION_GET_CONTENT);
            fileChooser.setType("application/octet-stream"); //fileChooser.setType("*/*"); ???
            resultLauncher.launch(Intent.createChooser(fileChooser, getString(R.string.select_file_btn)));
        });
        uploadToNsBtn = findViewById(R.id.buttonUpload);

        //check if it's from file selected
        if (savedInstanceState == null)
            readFile(getIntent());


        requestNotificationsPermission();
    }

    private boolean requestNotificationsPermission() {
        if (! IS_AFTER_TIRAMISU)
            return false;

        if (ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            return false;

        if (shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)) {
            Snackbar.make(getWindow().getDecorView(), "FIXME_FIXME_FIXME", BaseTransientBottomBar.LENGTH_INDEFINITE) // TODO: FIX
                    .setAction(R.string.settings, view -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(
                                Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    })
                    .show();
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{POST_NOTIFICATIONS}, 1);
        }

        return true;
    }

    private void updateUploadBtnState(){    // TODO: this function is bad. It multiplies entropy and sorrow.
            uploadToNsBtn.setEnabled(nspItemsAdapter.getItemCount() > 0);
    }
    /**
     * @see MainActivity#onCreate
     * TODO: move to separate class
     * */
    private void setSwipeFunctionsToView() {
        ItemTouchHelper.Callback ithCallBack = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(
                        ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                nspItemsAdapter.move(viewHolder.getAbsoluteAdapterPosition(), target.getAbsoluteAdapterPosition()); // OR  getBindingAdapterPosition() ?
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                 int direction) {
                NspViewHolder nspViewHolder = (NspViewHolder) viewHolder;
                nspElements.remove(nspViewHolder.getData());
                nspItemsAdapter.notifyDataSetChanged();
                updateUploadBtnState();
            }
        };
        new ItemTouchHelper(ithCallBack).attachToRecyclerView(recyclerView);
    }

    private void readFile(Intent data) {
        Uri uri = data.getData();
        if (uri == null || uri.getScheme() == null || ! uri.getScheme().equals("content"))
            return;

        String fileName = NsUtils.getFileNameFromUri(uri, this);
        long fileSize = NsUtils.getFileSizeFromUri(uri, this);

        if (fileName == null || fileSize < 0) { //TODO: if (fileName == null || fileSize <= 0) {
            NsUtils.getAlertWindow(this,
                    getResources().getString(R.string.popup_error),
                    getResources().getString(R.string.popup_incorrect_file));
            return;
        }

        if (NsUtils.isNotSupportedFileExtension(fileName)) {
            NsUtils.getAlertWindow(this,
                    getResources().getString(R.string.popup_error),
                    getResources().getString(R.string.popup_non_supported_format));
            return;
        }

        for (NSPElement element: nspElements) {
            if (element.getFilename().equals(fileName)) {
                return;
            }
        }

        NSPElement element = new NSPElement(uri, fileName, fileSize);
        element.setSelected(true);
        nspElements.add(element);
        nspItemsAdapter.notifyDataSetChanged();
        updateUploadBtnState();  // Enable upload button
    }
    private void uploadFiles() {
        if (requestNotificationsPermission())
            return;

        ArrayList<NSPElement> nspToSend = new ArrayList<>();
        for (NSPElement element: nspElements) {
            if (element.isSelected())
                nspToSend.add(element);
        }

        if (nspToSend.isEmpty()) {
            nsSnack(findViewById(android.R.id.content), getString(R.string.nothing_selected_message));
            return;
        }
        Intent serviceStartIntent = new Intent(this, TransferService.class);
        serviceStartIntent.setAction(ACTION_START_TRANSFER);
        serviceStartIntent.putParcelableArrayListExtra(NSS_CONTENT_LIST, nspToSend);
        serviceStartIntent.putExtra(NSS_PROTOCOL, selector.getSelected());

        if (selector.isNet()) {
            SharedPreferences sp = getSharedPreferences("NSUSBloader", MODE_PRIVATE);

            serviceStartIntent.putExtra(NSS_NS_IP, sp.getString("SNsIP", DEFAULT_NS_IP));
            serviceStartIntent.putExtra(NSS_PHONE_IP, sp.getBoolean("SAutoIP", true)?
                    "":
                    sp.getString("SServerIP", DEFAULT_PHONE_IP));
            serviceStartIntent.putExtra(NSS_PHONE_PORT, sp.getInt("SServerPort", DEFAULT_PHONE_PORT));
        }
        else {
            UsbDevice usbDevice = usbHelper.get();
            if (usbDevice == null) {
                // If it's still not connected then it's really not connected.
                NsUtils.getAlertWindow(this,
                        getResources().getString(R.string.popup_error),
                        getResources().getString(R.string.ns_not_found_in_connected));
                return;
            }
            // If NS connected ask permissions (if not already) TODO: add toast
            if (usbHelper.isNotHavePermission(getApplicationContext())) {
                return;
            }
            serviceStartIntent.putExtra(NSS_NS_DEVICE, usbDevice);
        }
        startService(serviceStartIntent);
        blockUI(true);
    }

    private void blockUI(boolean shouldBlock) {
        selectBtn.setEnabled(!shouldBlock);
        recyclerView.suppressLayout(shouldBlock);
        Drawable uploadBtnDrawable = ContextCompat.getDrawable(this, shouldBlock?
                R.drawable.ic_cancel:
                R.drawable.ic_upload_btn);
        uploadToNsBtn.setCompoundDrawablesWithIntrinsicBounds(
                null, uploadBtnDrawable, null, null);

        if (shouldBlock) {
            uploadToNsBtn.setText(R.string.interrupt_btn);
            uploadToNsBtn.setOnClickListener(view ->
                    stopService(new Intent(this, TransferService.class)));
            progressBarMain.setVisibility(ProgressBar.VISIBLE);
            progressBarMain.setIndeterminate(true);
            uploadToNsBtn.setEnabled(true);
            return;
        }
        uploadToNsBtn.setText(R.string.upload_btn);
        uploadToNsBtn.setOnClickListener(view -> uploadFiles());
        progressBarMain.setVisibility(ProgressBar.INVISIBLE);
        updateUploadBtnState();
    }

    /**
     * Handle broadcast intents
     * */
    private class InnerBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;

            switch (intent.getAction()) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    usbHelper = new UsbHelper(getApplicationContext(),
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                    break;
                case REQUEST_NS_ACCESS_INTENT:
                    if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        NsUtils.getAlertWindow(getApplicationContext(),
                                getResources().getString(R.string.popup_error),
                                getResources().getString(R.string.notification_need_permission));
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    usbHelper.setNsDetached();
                    stopService(new Intent(context, TransferService.class));
                    break;
                case SERVICE_TRANSFER_TASK_PROGRESS_INTENT:
                    int value = intent.getIntExtra(NS_PROGRESS, -1);
                    if (value < 0)
                        progressBarMain.setIndeterminate(true);
                    else {
                        progressBarMain.setIndeterminate(false);
                        progressBarMain.setProgress(value);
                    }
                    break;
                case SERVICE_TRANSFER_TASK_FINISHED_INTENT:
                    ArrayList<NSPElement> nspElementsFromIntent =
                            intent.getParcelableArrayListExtra(NSS_CONTENT_LIST);
                    if (nspElementsFromIntent == null)
                        break;
                    for (int i = 0; i < nspElements.size(); i++) {
                        for (NSPElement receivedNSPe: nspElementsFromIntent) {
                            if (receivedNSPe.getFilename().equals(nspElements.get(i).getFilename()))
                                nspElements.get(i).setStatus(receivedNSPe.getStatus());
                        }
                    }
                    nspItemsAdapter.notifyDataSetChanged();
                    blockUI(false);

                    String finalToastMessage = intent.getStringExtra(NSS_FINAL_TOAST_TEXT);
                    int finalToastDuration = intent.getIntExtra(NSS_FINAL_TOAST_DURATION, Toast.LENGTH_SHORT);
                    if (finalToastMessage != null)
                        Toast.makeText(getApplicationContext(), finalToastMessage, finalToastDuration).show();
                    break;
            }
        }
    }
}