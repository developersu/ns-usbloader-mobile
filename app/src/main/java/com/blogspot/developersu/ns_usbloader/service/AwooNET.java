package com.blogspot.developersu.ns_usbloader.service;

import static android.content.Context.WIFI_SERVICE;
import static java.lang.Thread.currentThread;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.service.utility.Consumer;
import com.blogspot.developersu.ns_usbloader.service.utility.ServiceResultingDataSet;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

class AwooNET extends TransferTask {

    private final static int NS_PORT = 2000;
    private Socket handShakeSocket;
    private OutputStream currentSocketOutStream;
    private PrintWriter currentSocketWriter;

    private final HashMap<String, NSPElement> files;
    private final String nsIp;
    private final String phoneIp;
    private final int phonePort;
    private final ServerSocket serverSocket;
    private final HttpReply reply;

    private boolean isFailed;

    AwooNET(Context context,
            ArrayList<NSPElement> nspElements,
            Consumer<ServiceResultingDataSet> serviceCallback,
            String nsIp,
            String phoneIp,
            int phonePort) throws Exception {
        super(context, nspElements, serviceCallback);
        this.reply = HttpReply.getInstance();
        this.nsIp = nsIp;
        this.phonePort = phonePort;
        this.files = new HashMap<>();
        // Collect NSP list
        for (NSPElement nspElem : nspElements)
            files.put(encodeNspName(nspElem), nspElem);
        this.phoneIp = phoneIp.isEmpty()?
                resolvePhoneIp():
                phoneIp;
        serverSocket = createServerSocket();
    }
    private String encodeNspName(NSPElement element) throws UnsupportedEncodingException {
        return URLEncoder.encode(element.getFilename(), "UTF-8").replaceAll("\\+", "%20");
    }
    private String resolvePhoneIp() throws Exception {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm == null)
            throw new Exception("NET: Unable to auto-resolve IP address.");

        int intIp = wm.getConnectionInfo().getIpAddress();
        String phoneIp = String.format(Locale.US, "%d.%d.%d.%d",
                (intIp & 0xff),
                (intIp >> 8 & 0xff),
                (intIp >> 16 & 0xff),
                (intIp >> 24 & 0xff));
        if (phoneIp.equals("0.0.0.0"))
            throw new Exception("NET: Unable to auto-resolve IP address (0.0.0.0)");
        return phoneIp;
    }
    private ServerSocket createServerSocket() throws Exception {
        try {
            return new ServerSocket(phonePort);
        }
        catch (IOException ioe) {
            throw new Exception(
                    String.format("NET: Can't open socket using port: %d. %s", phonePort, ioe.getMessage()));
        }
    }

    @Override
    protected void doTransfer() throws Exception {
        try {
            byte[] handshakeCommand = buildHandshakeContent().getBytes(); // android's .getBytes() default == UTF8
            byte[] handshakeCommandSize = ByteBuffer
                    .allocate(4)
                    .putInt(handshakeCommand.length).array(); // defining order; Integer size = 4 bytes
            sendHandshake(handshakeCommandSize, handshakeCommand);
            serveRequestsLoop();
            status = context.getResources().getString(R.string.status_uploaded);
        }
        catch (Exception e) {
            isFailed = true;
            throw new Exception("NET: Unable to connect to NS and send files list: "+e.getMessage());
        }
        finally {
            nspElements = new ArrayList<>();
            for (NSPElement element: files.values()) {
                element.setStatus(status);
                nspElements.add(element);
            }
        }
    }
    private String buildHandshakeContent() {
        StringBuilder myStrBuilder = new StringBuilder();
        for (String fileNameEncoded : files.keySet()) {
            myStrBuilder.append(phoneIp);
            myStrBuilder.append(':');
            myStrBuilder.append(phonePort);
            myStrBuilder.append('/');
            myStrBuilder.append(fileNameEncoded);
            myStrBuilder.append('\n');
        }
        return myStrBuilder.toString();
    }
    private void sendHandshake(byte[] commandSize, byte[] command) throws Exception {
        try {
            handShakeSocket = new Socket();
            handShakeSocket.connect(
                    new InetSocketAddress(InetAddress.getByName(nsIp), NS_PORT), 1000); // e.g. 1sec
            OutputStream outStream = handShakeSocket.getOutputStream();
            outStream.write(commandSize);
            outStream.write(command);
            outStream.flush();

            handShakeSocket.close();
        }
        catch (IOException uhe) {
            throw new Exception("NET: Unable to send files list: "+uhe.getMessage());
        }
    }

    private void serveRequestsLoop() throws Exception {
        while (!currentThread().isInterrupted()) {
            Socket clientSocket = serverSocket.accept();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            currentSocketOutStream = clientSocket.getOutputStream();
            currentSocketWriter = new PrintWriter(new OutputStreamWriter(currentSocketOutStream));

            String line;
            LinkedList<String> tcpPacket = new LinkedList<>();

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {          // If TCP packet is ended
                    handleRequest(tcpPacket);         // Proceed required things
                    tcpPacket.clear();                // Clear data and wait for next TCP packet
                    continue;
                }
                tcpPacket.add(line);              // Otherwise collect data
            }
            clientSocket.close();
        }
    }

    /**
     * Handle requests
     * 200, 206, 400 (invalid range), 404, 416 (Range Not Satisfiable)
     * */
    private void handleRequest(LinkedList<String> packet) throws Exception {
        if (packet.get(0).startsWith("DROP")) {
            currentThread().interrupt();
            return;
        }

        String reqFileName = packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", "");

        if (! files.containsKey(reqFileName)) {
            writeToSocket(reply.get404());
            return;
        }
        NSPElement requestedElement = files.get(reqFileName);

        long reqFileSize = requestedElement.getSize();

        if (reqFileSize == 0) {   //  404 reply for existing file with 0 length saves time
            writeToSocket(reply.get404());
            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
            return;
        }
        if (packet.get(0).startsWith("HEAD")){
            writeToSocket(reply.get200(reqFileSize));
            return;
        }
        if (packet.get(0).startsWith("GET")) {
            for (String line: packet) {
                if (line.toLowerCase().startsWith("range")) {
                    parseGetRange(requestedElement, reqFileSize, line);
                    return;
                }
            }
        }
    }

    private void parseGetRange(NSPElement requestedElement,
                               long fileSize,
                               String rangeDirective) throws Exception {
        try {
            String[] rangeStr = rangeDirective
                    .toLowerCase()
                    .replaceAll("^range:\\s+?bytes=", "")
                    .split("-", 2);

            if (! rangeStr[0].isEmpty()) {
                if (rangeStr[1].isEmpty()) {
                    writeToSocket(requestedElement, Long.parseLong(rangeStr[0]), fileSize);
                    return;
                }

                long fromRange = Long.parseLong(rangeStr[0]);
                long toRange = Long.parseLong(rangeStr[1]);
                if (fromRange > toRange) { // If start bytes greater than end bytes
                    writeToSocket(reply.get400());
                    requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                    return;
                }
                writeToSocket(requestedElement, fromRange, toRange);
                return;
            }

            if (rangeStr[1].isEmpty()) {
                writeToSocket(reply.get400());// If Range not defined: like "Range: bytes=-"
                requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                return;
            }

            if (fileSize > 500) {
                writeToSocket(requestedElement, fileSize - 500, fileSize);
                return;
            }
            // If file smaller than 500 bytes
            writeToSocket(reply.get416());
            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
        }
        catch (NumberFormatException nfe) {
            writeToSocket(reply.get400());
            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
            throw new Exception(
                    String.format("NET: Requested range for %s has incorrect format. Returning 400\n\t%s",
                            requestedElement.getFilename(), nfe.getMessage()));
        }
    }

    /** Send commands */
    private void writeToSocket(String string) {
        currentSocketWriter.write(string);
        currentSocketWriter.flush();
    }

    /** Send files */
    private void writeToSocket(NSPElement nsp, long start, long end) throws Exception {
        writeToSocket(reply.get206(nsp.getSize(), start, end));
        try (BufferedInputStream inputStream =
                     new BufferedInputStream(context.getContentResolver().openInputStream(nsp.getUri()))) {

            long count = end - start + 1; // Somehow it works
            int readPice = 4194304; //8388608;// = 8Mb (1024 is slow)
            byte[] byteBuf;

            if (inputStream.skip(start) != start) {
                nsp.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                throw new Exception("NET: Unable to skip requested range");
            }
            long currentOffset = 0;
            while (currentOffset < count) {
                if ((currentOffset+readPice) >= count) {
                    readPice = (int) (count - currentOffset);
                }
                byteBuf = new byte[readPice];

                if (inputStream.read(byteBuf) != readPice){
                    throw new Exception("NET: Reading from file stream suddenly ended");
                }
                currentSocketOutStream.write(byteBuf);
                currentOffset += readPice;
                updateProgressBar(currentOffset, count);
            }
            currentSocketOutStream.flush();         // TODO: check if this really needed
            inputStream.close();
            resetProgressBar();
        }
        catch (IOException ioe) {
            nsp.setStatus(context.getResources().getString(R.string.status_failed_to_upload)); // TODO: REDUNDANT?
            throw new Exception("NET: File transmission failed. Returned: "+ioe.getMessage());
        }
    }

    @Override
    protected void close() {
        try {
            if (isFailed)
                status = context.getResources().getString(R.string.status_failed_to_upload);
            if (serverSocket != null)
                serverSocket.close();       // Closing server socket.
        }
        catch (IOException | NullPointerException ignored){}
        try{
            handShakeSocket.close();
        }
        catch (IOException | NullPointerException ignored){}
    }
}
