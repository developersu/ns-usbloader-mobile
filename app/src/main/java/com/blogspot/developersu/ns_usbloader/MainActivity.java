package com.blogspot.developersu.ns_usbloader;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static com.blogspot.developersu.ns_usbloader.NsUtils.nsSnack;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_NS_DEVICE;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_PROTOCOL;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_GL_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_NET;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_USB;
import static java.util.Objects.nonNull;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;

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

import com.blogspot.developersu.ns_usbloader.model.NsResultReceiver;
import com.blogspot.developersu.ns_usbloader.service.TransferService;
import com.blogspot.developersu.ns_usbloader.view.ApplicationTheme;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;
import com.blogspot.developersu.ns_usbloader.view.NspItemsAdapter;
import com.blogspot.developersu.ns_usbloader.view.NspViewHolder;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.HashMap;

// TODO: add ImageAsset for notification icon instead of SVG-like
// TODO NOTE: If service execution has been finished in background, then no UI updates will come
public class MainActivity extends AppCompatActivity implements
        NsResultReceiver.Receiver,
        NavigationView.OnNavigationItemSelectedListener  {

    private static final int ADD_NSP_INTENT_CODE = 1;

    private RecyclerView recyclerView;
    private NspItemsAdapter mAdapter;
    private ArrayList<NSPElement> mDataset = new ArrayList<>();
    private BroadcastReceiver innerBroadcastReceiver;
    private UsbDevice usbDevice;
    private boolean isUsbDeviceAccessible;
    private Button selectBtn,
            uploadToNsBtn;
    private ProgressBar progressBarMain;
    private NavigationView drawerNavView;
    private NsResultReceiver nsResultReceiver;

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList("DATASET_LIST", mDataset);
        outState.putParcelable("USB_DEVICE", usbDevice);
        outState.putBoolean("IS_USB_DEV_ACCESSIBLE", isUsbDeviceAccessible);
        MenuItem checkedItem = drawerNavView.getCheckedItem();
        outState.putInt("PROTOCOL", checkedItem != null ?
                checkedItem.getItemId() :
                R.id.nav_tf_usb);
        outState.putParcelable("RECEIVER", nsResultReceiver);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Configure intent to receive attached NS
        innerBroadcastReceiver = new InnerBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(NsConstants.REQUEST_NS_ACCESS_INTENT);
        intentFilter.addAction(NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT);
        ContextCompat.registerReceiver(getApplicationContext(),
                innerBroadcastReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED);
        nsResultReceiver.setReceiver(this);
        blockUI(TransferService.isActive());
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(innerBroadcastReceiver);
        SharedPreferences.Editor preferencesEditor = getSharedPreferences("NSUSBloader", MODE_PRIVATE).edit();

        MenuItem checkedItem = drawerNavView.getCheckedItem();
        if (checkedItem != null){
            switch (checkedItem.getItemId()) {
                case R.id.nav_tf_net:
                    preferencesEditor.putInt("PROTOCOL", PROTO_TF_NET);
                    break;
                case R.id.nav_gl:
                    preferencesEditor.putInt("PROTOCOL", PROTO_GL_USB);
                    break;
                case R.id.nav_tf_usb:
                default:
                    preferencesEditor.putInt("PROTOCOL", PROTO_TF_USB);
                    break;
            }
        }
        else
            preferencesEditor.putInt("PROTOCOL", PROTO_TF_USB);
        preferencesEditor.apply();
        nsResultReceiver.setReceiver(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_toolbar, menu);
        menu.findItem(R.id.select_all).setOnMenuItemClickListener(menuItem -> {
            if (mDataset.isEmpty())
                return true;

            if (menuItem.getItemId() == R.id.nav_gl) {
                nsSnack(findViewById(android.R.id.content), getString(R.string.one_item_for_gl_notification));
                return true;
            }

            for (NSPElement element: mDataset)
                element.setSelected(true);
            mAdapter.notifyDataSetChanged();
            return true;
        });
        return true;
    }

    // Drawer actions: Handle navigation view item clicks here
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_gl:
                if (! mDataset.isEmpty()) {
                    for (NSPElement element : mDataset)
                        element.setSelected(false);
                    mAdapter.notifyDataSetChanged();
                }
                break;
            case R.id.nav_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.nav_about:
                startActivity(new Intent(this, AboutActivity.class));
        }

        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{POST_NOTIFICATIONS},
                        1);
                return;
            }
        }

        setContentView(R.layout.activity_main);
        // Initialize ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawerNavView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        drawerNavView.setNavigationItemSelectedListener(this);
        // Initialize Progress Bar
        progressBarMain = findViewById(R.id.mainProgressBar);

        if (savedInstanceState != null){
            mDataset = savedInstanceState.getParcelableArrayList("DATASET_LIST");
            usbDevice = savedInstanceState.getParcelable("USB_DEVICE"); // Restore USB device information
            isUsbDeviceAccessible = savedInstanceState.getBoolean("IS_USB_DEV_ACCESSIBLE", false);
            drawerNavView.setCheckedItem(savedInstanceState.getInt("PROTOCOL", R.id.nav_tf_usb));
            nsResultReceiver = savedInstanceState.getParcelable("RECEIVER");
        }
        else {
            //savedInstanceState == null
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            usbDevice = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);    // If it's started initially, then check if it's started from notification.
            isUsbDeviceAccessible = nonNull(usbDevice) && usbManager.hasPermission(usbDevice);

            SharedPreferences preferences = getSharedPreferences("NSUSBloader", MODE_PRIVATE);
            ApplicationTheme.setApplicationTheme(preferences.getInt("ApplicationTheme", 0));

            switch (preferences.getInt("PROTOCOL", PROTO_TF_USB)) {
                case PROTO_TF_NET:
                    drawerNavView.setCheckedItem(R.id.nav_tf_net);
                    break;
                case PROTO_GL_USB:
                    drawerNavView.setCheckedItem(R.id.nav_gl);
                    break;
                case PROTO_TF_USB:
                default:
                    drawerNavView.setCheckedItem(R.id.nav_tf_usb);
            }
            nsResultReceiver = new NsResultReceiver(new Handler()); // We will set callback in onResume and unset onPause

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
        mAdapter = new NspItemsAdapter(mDataset);
        recyclerView.setAdapter(mAdapter);
        this.setSwipeFunctionsToView();
        // Select files button
        selectBtn = findViewById(R.id.buttonSelect);

        ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                activityResult -> {
                    int requestCode = activityResult.getResultCode();
                    Intent data = activityResult.getData();
                    if (requestCode != ADD_NSP_INTENT_CODE || data == null)
                        return;
                    readFile(data);
                });

        selectBtn.setOnClickListener(e -> {
            Intent fileChooser = new Intent(Intent.ACTION_GET_CONTENT);
            fileChooser.setType("application/octet-stream"); //fileChooser.setType("*/*"); ???
            resultLauncher.launch(Intent.createChooser(fileChooser, getString(R.string.select_file_btn)));
        });
        uploadToNsBtn = findViewById(R.id.buttonUpload);

        Intent intent = getIntent();   //check if it's from file selected
        if (savedInstanceState == null && intent.getData() != null) {
            readFile(intent);
        }
    }

    private void updateUploadBtnState(){    // TODO: this function is bad. It multiplies entropy and sorrow.
            uploadToNsBtn.setEnabled(mAdapter.getItemCount() > 0);
    }
    /**
     * @see MainActivity#onCreate
     * */
    private void setSwipeFunctionsToView(){
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
                mAdapter.move(viewHolder.getAbsoluteAdapterPosition(), target.getAbsoluteAdapterPosition()); // OR  getBindingAdapterPosition() ?
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                 int direction) {
                NspViewHolder nspViewHolder = (NspViewHolder) viewHolder;
                mDataset.remove(nspViewHolder.getData());
                mAdapter.notifyDataSetChanged();
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

        if (fileName == null || fileSize < 0) {
            NsUtils.getAlertWindow(this,
                    getResources().getString(R.string.popup_error),
                    getResources().getString(R.string.popup_incorrect_file));
            return;
        }

        String fileExtension = fileName.replaceAll("^.*\\.", "").toLowerCase();
        switch (fileExtension) {
            case "nsp":
            case "nsz":
            case "xci":
            case "xcz":
                break;
            default:
                NsUtils.getAlertWindow(this,
                        getResources().getString(R.string.popup_error),
                        getResources().getString(R.string.popup_non_supported_format));
                return;
        }

        for (NSPElement element: mDataset){
            if (element.getFilename().equals(fileName)) {
                return;
            }
        }

        NSPElement element = new NSPElement(uri, fileName, fileSize);
        if (drawerNavView.getCheckedItem() != null) // && drawerNavView.getCheckedItem().getItemId() != R.id.nav_gl
            element.setSelected(true);
        mDataset.add(element);
        mAdapter.notifyDataSetChanged();

        updateUploadBtnState();  // Enable upload button
    }
    private void uploadFiles(){
        ArrayList<NSPElement> NSPElementsToSend = new ArrayList<>();
        for (NSPElement element: mDataset){
            if (element.isSelected())
                NSPElementsToSend.add(element);
        }
        // Do we have files to send?
        if (NSPElementsToSend.isEmpty()){
            nsSnack(findViewById(android.R.id.content), getString(R.string.nothing_selected_message));
            return;
        }
        // Do we have selected protocol?
        if (drawerNavView.getCheckedItem() == null) {
            nsSnack(findViewById(android.R.id.content), getString(R.string.no_protocol_selected_message));
            return;
        }
        Intent serviceStartIntent = new Intent(this, TransferService.class);
        serviceStartIntent.putExtra(NsConstants.NS_RESULT_RECEIVER, nsResultReceiver);
        serviceStartIntent.putParcelableArrayListExtra(NsConstants.NS_SERVICE_CONTENT_NSP_LIST, NSPElementsToSend);
        // Is it TF Net transfer?
        if (drawerNavView.getCheckedItem().getItemId() == R.id.nav_tf_net) {
            serviceStartIntent.putExtra(NS_SERVICE_CONTENT_PROTOCOL, PROTO_TF_NET);
            SharedPreferences sp = getSharedPreferences("NSUSBloader", MODE_PRIVATE);

            serviceStartIntent.putExtra(NsConstants.NS_SERVICE_CONTENT_NS_DEVICE_IP, sp.getString("SNsIP", "192.168.1.42"));
            if (sp.getBoolean("SAutoIP", true))
                serviceStartIntent.putExtra(NsConstants.NS_SERVICE_CONTENT_PHONE_IP, "");
            else
                serviceStartIntent.putExtra(NsConstants.NS_SERVICE_CONTENT_PHONE_IP, sp.getString("SServerIP", "192.168.1.142"));
            serviceStartIntent.putExtra(NsConstants.NS_SERVICE_CONTENT_PHONE_PORT, sp.getInt("SServerPort", 6042));
            startService(serviceStartIntent);
            blockUI(true);
            return;
        }
        // Ok, so it's something USB related
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        // Do we have manager?
        if (usbManager == null) {
            NsUtils.getAlertWindow(this,
                    getResources().getString(R.string.popup_error),
                    "Internal issue: getSystemService(Context.USB_SERVICE) returned null");
            return;
        }
        // If device not connected
        if (usbDevice == null) {
            HashMap<String, UsbDevice> deviceHashMap = usbManager.getDeviceList();
            for (UsbDevice device : deviceHashMap.values()) {
                if (device.getVendorId() == 1406 && device.getProductId() == 12288) {
                    usbDevice = device;
                }
            }
            // If it's still not connected then it's really not connected.
            if (usbDevice == null) {
                NsUtils.getAlertWindow(this,
                        getResources().getString(R.string.popup_error),
                        getResources().getString(R.string.ns_not_found_in_connected));
                return;
            }
            // If we have NS connected check for permissions
            if (! usbManager.hasPermission(usbDevice)) {
                usbManager.requestPermission(usbDevice,
                        PendingIntent.getBroadcast(this,
                                0,
                                new Intent(NsConstants.REQUEST_NS_ACCESS_INTENT),
                                PendingIntent.FLAG_IMMUTABLE));
                return;
            }
        }
        if (! isUsbDeviceAccessible){
            usbManager.requestPermission(usbDevice,
                    PendingIntent.getBroadcast(this,
                            0,
                            new Intent(NsConstants.REQUEST_NS_ACCESS_INTENT),
                            PendingIntent.FLAG_IMMUTABLE));
            return;
        }

        int itemId = drawerNavView.getCheckedItem().getItemId();
        if (itemId == R.id.nav_tf_usb)
            serviceStartIntent.putExtra(NS_SERVICE_CONTENT_PROTOCOL, PROTO_TF_USB);
        else if (itemId == R.id.nav_gl)
            serviceStartIntent.putExtra(NS_SERVICE_CONTENT_PROTOCOL, PROTO_GL_USB);
        else {
            nsSnack(findViewById(android.R.id.content), getString(R.string.unknown_protocol_error)); // ?_?
            return;
        }
        serviceStartIntent.putExtra(NS_SERVICE_CONTENT_NS_DEVICE, usbDevice);
        startService(serviceStartIntent);
        blockUI(true);
    }

    private void blockUI(boolean shouldBlock){
        selectBtn.setEnabled(! shouldBlock);
        recyclerView.suppressLayout(shouldBlock);
        Drawable uploadBtnDrawable = ContextCompat.getDrawable(this, shouldBlock?
                R.drawable.ic_cancel:
                R.drawable.ic_upload_btn);
        uploadToNsBtn.setCompoundDrawablesWithIntrinsicBounds(
                null, uploadBtnDrawable, null, null);

        if (shouldBlock) {
            uploadToNsBtn.setText(R.string.interrupt_btn);
            uploadToNsBtn.setOnClickListener(e ->
                    stopService(new Intent(this, TransferService.class)));
            progressBarMain.setVisibility(ProgressBar.VISIBLE);
            progressBarMain.setIndeterminate(true);//TODO
            uploadToNsBtn.setEnabled(true);
            return;
        }
        uploadToNsBtn.setText(R.string.upload_btn);
        uploadToNsBtn.setOnClickListener(e -> this.uploadFiles() );
        progressBarMain.setVisibility(ProgressBar.INVISIBLE);
        this.updateUploadBtnState();
    }
    /**
     * Handle service updates
     * */
    @Override
    public void onReceiveResults(int code, Bundle bundle) {
        if (code == NsConstants.NS_RESULT_PROGRESS_INDETERMINATE)
            progressBarMain.setIndeterminate(true);
        else {                                                                       // Else NsConstants.NS_RESULT_PROGRESS_VALUE ; ALSO THIS PART IS FULL OF SHIT
            if (progressBarMain.isIndeterminate())
                progressBarMain.setIndeterminate(false);
            progressBarMain.setProgress(bundle.getInt("POSITION"));
        }
    }
    /**
     * Deal with global broadcast intents
     * */
    private class InnerBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;
            switch (intent.getAction()) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                    if (usbManager == null)
                        break;  // ???
                    if (usbManager.hasPermission(usbDevice))
                        isUsbDeviceAccessible = true;
                    else {
                        isUsbDeviceAccessible = false;
                        usbManager.requestPermission(usbDevice,
                                PendingIntent.getBroadcast(context,
                                        0,
                                        new Intent(NsConstants.REQUEST_NS_ACCESS_INTENT),
                                        PendingIntent.FLAG_IMMUTABLE));
                    }
                    break;
                case NsConstants.REQUEST_NS_ACCESS_INTENT:
                    isUsbDeviceAccessible = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    usbDevice = null;
                    isUsbDeviceAccessible = false;
                    stopService(new Intent(context, TransferService.class));
                    break;
                case NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT:
                    ArrayList<NSPElement> nspElements = intent.getParcelableArrayListExtra(NsConstants.NS_SERVICE_CONTENT_NSP_LIST);
                    if (nspElements == null)
                        break;
                    for (int i = 0; i < mDataset.size(); i++) {
                        for (NSPElement receivedNSPe : nspElements) {
                            if (receivedNSPe.getFilename().equals(mDataset.get(i).getFilename()))
                                mDataset.get(i).setStatus(receivedNSPe.getStatus());
                        }
                    }
                    mAdapter.notifyDataSetChanged();
                    blockUI(false);
                    break;
            }
        }
    }
}
