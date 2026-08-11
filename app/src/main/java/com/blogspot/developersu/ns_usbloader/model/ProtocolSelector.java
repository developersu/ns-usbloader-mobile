package com.blogspot.developersu.ns_usbloader.model;

import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_GL_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_NET;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_USB;
import static java.util.Objects.requireNonNull;

import com.blogspot.developersu.ns_usbloader.R;
import com.google.android.material.navigation.NavigationView;

public class ProtocolSelector {

    private final NavigationView navView;

    public ProtocolSelector(NavigationView navView) {
        this.navView = navView;
    }

    public boolean isNet() {
        return requireNonNull(navView.getCheckedItem()).getItemId() == R.id.nav_tf_net;
    }

    public boolean isGl() {
        return requireNonNull(navView.getCheckedItem()).getItemId() == R.id.nav_gl;
    }

    public void select(int proto) {
        switch (proto) {
            case PROTO_TF_NET:
                navView.setCheckedItem(R.id.nav_tf_net);
                break;
            case PROTO_GL_USB:
                navView.setCheckedItem(R.id.nav_gl);
                break;
            case PROTO_TF_USB:
            default:
                navView.setCheckedItem(R.id.nav_tf_usb);
        }
    }

    public int getSelected() {
        if (isNet())
            return PROTO_TF_NET;
        if (isGl())
            return PROTO_GL_USB;
        return PROTO_TF_USB;
    }
}
