package com.blogspot.developersu.ns_usbloader.service;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.ResultReceiver;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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

import static android.content.Context.WIFI_SERVICE;

class TinfoilNET extends TransferTask {

    private HashMap<String, NSPElement> files;

    private Socket handShakeSocket;
    private ServerSocket serverSocket;

    private OutputStream currSockOS;
    private PrintWriter currSockPW;

    private String nsIp;
    private String phoneIp;
    private int phonePort;

    private boolean jobInProgress = true;

    @Override
    void cancel(){
        super.cancel();

        try{
            handShakeSocket.close();
            serverSocket.close();
        }
        catch (IOException | NullPointerException ignored){}
    }

    /**
     * Simple constructor that everybody uses
     * */
    TinfoilNET(ResultReceiver resultReceiver,
               Context context,
               ArrayList<NSPElement> nspElements,
               String nsIp,
               String phoneIp,
               int phonePort) throws Exception {
        super(resultReceiver, context);
        this.nsIp = nsIp;
        this.phoneIp = phoneIp;
        this.phonePort = phonePort;

        this.files = new HashMap<>();
        // Collect and encode NSP files list
        for (NSPElement nspElem : nspElements)
            files.put(URLEncoder.encode(nspElem.getFilename(), "UTF-8").replaceAll("\\+", "%20"), nspElem); // replace + to %20

        // Resolve IP
        if (phoneIp.isEmpty())
            resolvePhoneIp();
        // Open Server Socket on port
        try {
            serverSocket = new ServerSocket(phonePort);
        } catch (IOException ioe) {
            throw new Exception("NET: Can't open socket using port: " + phonePort + ". Returned: "+ioe.getMessage());
        }
    }

    private void resolvePhoneIp() throws Exception{
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm == null)
            throw new Exception("NET: Unable to auto-resolve IP address.");

        int intIp = wm.getConnectionInfo().getIpAddress();
        phoneIp = String.format(Locale.US, "%d.%d.%d.%d",
                (intIp & 0xff),
                (intIp >> 8 & 0xff),
                (intIp >> 16 & 0xff),
                (intIp >> 24 & 0xff));
        if (phoneIp.equals("0.0.0.0"))
            throw new Exception("NET: Unable to auto-resolve IP address (0.0.0.0)");
    }

    @Override
    boolean run(){
        try {
            if (interrupt)
                return false;

            byte[] handshakeCommand = buildHandshakeContent().getBytes();                // android's .getBytes() default == UTF8  // Follow the
            byte[] handshakeCommandSize = ByteBuffer.allocate(4).putInt(handshakeCommand.length).array();                       // defining order ; Integer size = 4 bytes

            sendHandshake(handshakeCommandSize, handshakeCommand);

            serveRequestsLoop();
        }
        catch (Exception e){
            close(true);
            issueDescription = "NET: Unable to connect to NS and send files list: "+e.getMessage();
            return true;
        }
        close(false);
        return true;
    }
    private String buildHandshakeContent(){
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
    private void sendHandshake(byte[] handshakeCommandSize, byte[] handshakeCommand) throws Exception{
        try {
            handShakeSocket = new Socket();
            handShakeSocket.connect(new InetSocketAddress(InetAddress.getByName(nsIp), 2000), 1000); // e.g. 1sec
            OutputStream os = handShakeSocket.getOutputStream();
            os.write(handshakeCommandSize);
            os.write(handshakeCommand);
            os.flush();

            handShakeSocket.close();
        }
        catch (IOException uhe){
            throw new Exception("NET: Unable to send files list: "+uhe.getMessage());
        }
    }

    private void serveRequestsLoop() throws Exception{
        while (jobInProgress){
            Socket clientSocket = serverSocket.accept();

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );

            currSockOS = clientSocket.getOutputStream();
            currSockPW = new PrintWriter(new OutputStreamWriter(currSockOS));

            String line;
            LinkedList<String> tcpPacket = new LinkedList<>();

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {          // If TCP packet is ended
                    handleRequest(tcpPacket);         // Proceed required things
                    tcpPacket.clear();                // Clear data and wait for next TCP packet
                }
                else
                    tcpPacket.add(line);              // Otherwise collect data
            }
            clientSocket.close();
        }
    }
    // 200 206 400 (inv range) 404 416 (Range Not Satisfiable )
    /**
     * Handle requests
     * */
    private void handleRequest(LinkedList<String> packet) throws Exception{
        if (packet.get(0).startsWith("DROP")){
            jobInProgress = false;
            return;
        }

        String reqFileName = packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", "");

        if (! files.containsKey(reqFileName)){
            writeToSocket(NETPacket.getCode404());
            return;
        }
        NSPElement requestedElement = files.get(reqFileName);

        long reqFileSize = requestedElement.getSize();

        if (reqFileSize == 0){   // well.. tell 404 if file exists with 0 length is against standard, but saves time
            writeToSocket(NETPacket.getCode404());
            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
            return;
        }
        if (packet.get(0).startsWith("HEAD")){
            writeToSocket(NETPacket.getCode200(reqFileSize));
            return;
        }
        if (packet.get(0).startsWith("GET")) {
            for (String line: packet) {
                if (line.toLowerCase().startsWith("range")){
                    parseGETrange(requestedElement, reqFileSize, line);
                    return;
                }
            }
        }
    }

    private void parseGETrange(NSPElement requestedElement, long fileSize, String rangeDirective) throws Exception{
        try {
            String[] rangeStr = rangeDirective.toLowerCase().replaceAll("^range:\\s+?bytes=", "").split("-", 2);

            if (! rangeStr[0].isEmpty()){
                if (rangeStr[1].isEmpty()) {
                    writeToSocket(requestedElement, Long.parseLong(rangeStr[0]), fileSize);
                    return;
                }

                long fromRange = Long.parseLong(rangeStr[0]);
                long toRange = Long.parseLong(rangeStr[1]);
                if (fromRange > toRange){ // If start bytes greater then end bytes
                    writeToSocket(NETPacket.getCode400());
                    requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                    return;
                }
                writeToSocket(requestedElement, fromRange, toRange);
                return;
            }

            if (rangeStr[1].isEmpty()) {
                writeToSocket(NETPacket.getCode400());                       // If Range not defined: like "Range: bytes=-"
                requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                return;
            }

            if (fileSize > 500) {
                writeToSocket(requestedElement, fileSize - 500, fileSize);
                return;
            }
            // If file smaller than 500 bytes
            writeToSocket(NETPacket.getCode416());
            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
        }
        catch (NumberFormatException nfe){
            writeToSocket(NETPacket.getCode400());
            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
            throw new Exception("NET: Requested range for "+requestedElement.getFilename()+" has incorrect format. Returning 400\n\t"+nfe.getMessage());
        }
    }
    /** Send commands */
    private void writeToSocket(String string) {
        currSockPW.write(string);
        currSockPW.flush();
    }
    /** Send files */
    private void writeToSocket(NSPElement nspElem, long start, long end) throws Exception{
        if (interrupt){
            throw new Exception("Interrupted by user");
        }
        writeToSocket(NETPacket.getCode206(nspElem.getSize(), start, end));
        try{
            long count = end - start + 1;       // Meeh. Somehow it works

            InputStream elementInputStream = context.getContentResolver().openInputStream(nspElem.getUri());
            if (elementInputStream == null) {
                throw new Exception("NET Unable to obtain input stream");
            }

            BufferedInputStream bis = new BufferedInputStream(elementInputStream);

            int readPice = 4194304;//8388608;// = 8Mb (1024 is slow)
            byte[] byteBuf;

            if (bis.skip(start) != start){
                nspElem.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                throw new Exception("NET: Unable to skip requested range");
            }
            long currentOffset = 0;
            while (currentOffset < count){
                if (interrupt)
                    throw new Exception("Interrupted by user");
                if ((currentOffset+readPice) >= count){
                    readPice = (int) (count - currentOffset);
                }
                byteBuf = new byte[readPice];

                if (bis.read(byteBuf) != readPice){
                    throw new Exception("NET: Reading from file stream suddenly ended");
                }
                currSockOS.write(byteBuf);

                currentOffset += readPice;

                updateProgressBar((int) ((double)currentOffset/((double)count/100.0)));
            }
            currSockOS.flush();         // TODO: check if this really needed.
            bis.close();
            resetProgressBar();
        }
        catch (IOException ioe){
            nspElem.setStatus(context.getResources().getString(R.string.status_failed_to_upload));      // TODO: REDUNDANT?
            throw new Exception("NET: File transmission failed. Returned: "+ioe.getMessage());
        }
    }
    /**
     * Close when done
     * */
    private void close(boolean isFailed){
        if (isFailed)
            status = context.getResources().getString(R.string.status_failed_to_upload);
        else
            status = context.getResources().getString(R.string.status_unkown);
        try {
            if (serverSocket != null)
                serverSocket.close();       // Closing server socket.
        }
        catch (IOException | NullPointerException ignored){}
    }

    String getIssueDescription() {
        return issueDescription;
    }
}
