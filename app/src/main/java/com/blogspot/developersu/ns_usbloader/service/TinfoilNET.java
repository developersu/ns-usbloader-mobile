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

    private HashMap<String, NSPElement> nspMap;

    private Socket handShakeSocket;
    private ServerSocket serverSocket;

    private OutputStream currSockOS;
    private PrintWriter currSockPW;

    private String nsIp;
    private String phoneIp;
    private int phonePort;


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

        this.nspMap = new HashMap<>();
        // Collect and encode NSP files list

        for (NSPElement nspElem : nspElements)
            nspMap.put(URLEncoder.encode(nspElem.getFilename(), "UTF-8").replaceAll("\\+", "%20"), nspElem); // replace + to %20

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
        if (interrupt)
            return false;
        // Create string that we'll send to TF and which initiates chain
        StringBuilder myStrBuilder;

        myStrBuilder = new StringBuilder();
        for (String fileNameEncoded : nspMap.keySet()) {
            myStrBuilder.append(phoneIp);
            myStrBuilder.append(':');
            myStrBuilder.append(phonePort);
            myStrBuilder.append('/');
            myStrBuilder.append(fileNameEncoded);
            myStrBuilder.append('\n');
        }

        byte[] nspListNames = myStrBuilder.toString().getBytes();                // android's .getBytes() default == UTF8  // Follow the
        byte[] nspListSize = ByteBuffer.allocate(4).putInt(nspListNames.length).array();                       // defining order ; Integer size = 4 bytes
        
        try {
            handShakeSocket = new Socket();
            handShakeSocket.connect(new InetSocketAddress(InetAddress.getByName(nsIp), 2000), 1000); // e.g. 1sec
            OutputStream os = handShakeSocket.getOutputStream();
            os.write(nspListSize);
            os.write(nspListNames);
            os.flush();

            handShakeSocket.close();
        }
        catch (IOException uhe){
            issueDescription = "NET: Unable to connect to NS and send files list. Returned: "+uhe.getMessage();
            close(true);
            return true;
        }
        // Go transfer
        work_routine:
        while (true){
            try {
                Socket clientSocket = serverSocket.accept();

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                );

                currSockOS = clientSocket.getOutputStream();
                currSockPW = new PrintWriter(new OutputStreamWriter(currSockOS));

                String line;
                LinkedList<String> tcpPacket = new LinkedList<>();

                while ((line = br.readLine()) != null) {
                    //System.out.println(line);           // Debug
                    if (line.trim().isEmpty()) {          // If TCP packet is ended
                        if (handleRequest(tcpPacket))     // Proceed required things
                            break work_routine;
                        tcpPacket.clear();                // Clear data and wait for next TCP packet
                    }
                    else
                        tcpPacket.add(line);              // Otherwise collect data
                }
                clientSocket.close();
            }
            catch (IOException ioe){                      // If server socket closed, then client socket also closed.
                break;
            }
        }
        close(false);
        return true;
    }

    // 200 206 400 (inv range) 404 416 (Range Not Satisfiable )
    /**
     * Handle requests
     * @return true if failed
     * */
    private boolean handleRequest(LinkedList<String> packet){
        String reqFileName = packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", "");

        if (! nspMap.containsKey(reqFileName)){
            currSockPW.write(NETPacket.getCode404());
            currSockPW.flush();
            issueDescription = "NET: File "+reqFileName+" doesn't exists or have 0 size. Returning 404";
            return true;
        }
        NSPElement requestedElement = nspMap.get(reqFileName);

        long reqFileSize = requestedElement.getSize();

        if (reqFileSize == 0){   // well.. tell 404 if file exists with 0 length is against standard, but saves time
            currSockPW.write(NETPacket.getCode404());
            currSockPW.flush();
            issueDescription = "NET: File "+reqFileName+" doesn't exists or have 0 size. Returning 404";
            return true;
        }
        if (packet.get(0).startsWith("HEAD")){
            currSockPW.write(NETPacket.getCode200(reqFileSize));
            currSockPW.flush();
            return false;
        }
        if (packet.get(0).startsWith("GET")) {
            for (String line: packet) {
                if (line.toLowerCase().startsWith("range")) {               //todo: fix
                    try {
                        String[] rangeStr = line.toLowerCase().replaceAll("^range:\\s+?bytes=", "").split("-", 2);
                        if (!rangeStr[0].isEmpty() && !rangeStr[1].isEmpty()) {      // If both ranges defined: Read requested
                            if (Long.parseLong(rangeStr[0]) > Long.parseLong(rangeStr[1])){ // If start bytes greater then end bytes
                                currSockPW.write(NETPacket.getCode400());
                                currSockPW.flush();
                                issueDescription = "NET: Requested range for "+requestedElement.getFilename()+" is incorrect. Returning 400";
                                requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                                return true;
                            }
                            if (writeToSocket(requestedElement, Long.parseLong(rangeStr[0]), Long.parseLong(rangeStr[1])))         // DO WRITE
                                return true;
                        }
                        else if (!rangeStr[0].isEmpty()) {                           // If only START defined: Read all
                            if (writeToSocket(requestedElement, Long.parseLong(rangeStr[0]), reqFileSize))         // DO WRITE
                                return true;
                        }
                        else if (!rangeStr[1].isEmpty()) {                           // If only END defined: Try to read last 500 bytes
                            if (reqFileSize > 500){
                                if (writeToSocket(requestedElement, reqFileSize-500, reqFileSize))         // DO WRITE
                                    return true;
                            }
                            else {                                                  // If file smaller than 500 bytes
                                currSockPW.write(NETPacket.getCode416());
                                currSockPW.flush();
                                issueDescription = "NET: File size requested for "+requestedElement.getFilename()+" while actual size of it: "+requestedElement.getSize()+". Returning 416";
                                requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                                return true;
                            }
                        }
                        else {
                            currSockPW.write(NETPacket.getCode400());                       // If Range not defined: like "Range: bytes=-"
                            currSockPW.flush();
                            issueDescription = "NET: Requested range for "+requestedElement.getFilename()+" is incorrect (empty start & end). Returning 400";
                            requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                            return true;
                        }
                        break;
                    }
                    catch (NumberFormatException nfe){
                        currSockPW.write(NETPacket.getCode400());
                        currSockPW.flush();
                        issueDescription = "NET: Requested range for "+requestedElement.getFilename()+" has incorrect format. Returning 400\n\t"+nfe.getMessage();
                        requestedElement.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                        return true;
                    }
                }
            }
        }
        return false;
    }
    /**
     * Send files.
     * */
    private boolean writeToSocket(NSPElement nspElem, long start, long end){
        if (interrupt)
            return true;
        currSockPW.write(NETPacket.getCode206(nspElem.getSize(), start, end));
        currSockPW.flush();
        try{
            long count = end - start + 1;       // Meeh. Somehow it works

            InputStream elementIS = context.getContentResolver().openInputStream(nspElem.getUri());
            if (elementIS == null) {
                issueDescription = "NET Unable to obtain InputStream";
                return true;
            }

            BufferedInputStream bis = new BufferedInputStream(elementIS);

            int readPice = 8388608;                     // = 8Mb
            byte[] byteBuf;

            if (bis.skip(start) != start){
                issueDescription = "NET: Unable to skip requested range.";
                nspElem.setStatus(context.getResources().getString(R.string.status_failed_to_upload));
                return true;
            }
            long currentOffset = 0;
            while (currentOffset < count){
                if (interrupt)
                    return true;
                if ((currentOffset+readPice) >= count){
                    readPice = (int) (count - currentOffset);
                }
                byteBuf = new byte[readPice];

                if (bis.read(byteBuf) != readPice){
                    issueDescription = "NET: Reading of nspElem stream suddenly ended.";
                    return true;
                }
                currSockOS.write(byteBuf);

                currentOffset += readPice;

                updateProgressBar((int) ((currentOffset+1)/(count/100+1)));

            }
            currSockOS.flush();         // TODO: check if this really needed.
            bis.close();
            resetProgressBar();
        }
        catch (IOException ioe){
            issueDescription = "NET: File transmission failed. Returned: "+ioe.getMessage();
            nspElem.setStatus(context.getResources().getString(R.string.status_failed_to_upload));      // TODO: REDUNDANT?
            return true;
        }
        return false;
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
