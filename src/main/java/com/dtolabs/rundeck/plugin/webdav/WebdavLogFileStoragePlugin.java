package com.dtolabs.rundeck.plugin.webdav;

import com.dtolabs.rundeck.core.logging.LogFileState;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.logging.LogFileStoragePlugin;
import com.dtolabs.utils.Streams;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import java.io.*;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example plugin which copies log files to another directory
 */
@Plugin(service = ServiceNameConstants.LogFileStorage, name = "webdav")
@PluginDescription(title = "Webdav Log File Storage Plugin", description = "Webdav Log File Storage")
public class WebdavLogFileStoragePlugin implements LogFileStoragePlugin {
    static final Logger log = Logger.getLogger(WebdavLogFileStoragePlugin.class.getName());
    private Map<String, ?> context;
    //@PluginProperty(required = true)
    private String destinationDirPath;

    public WebdavLogFileStoragePlugin() {
        this.destinationDirPath = "/tmp/rundeck_cluster";
    }

    public boolean store(InputStream stream, long length, Date modtime) throws IOException {
        File storeFile = getDestinationFile();
        File tempFile = getDestinationTempFile();
        if (!storeFile.getParentFile().isDirectory() && !storeFile.getParentFile().mkdirs()) {
            log.log(Level.SEVERE, "Failed creating dirs {0}", storeFile.getParentFile());
        }
        if (!tempFile.getParentFile().isDirectory() && !tempFile.getParentFile().mkdirs()) {
            log.log(Level.SEVERE, "Failed creating dirs {0}", storeFile.getParentFile());
        }
        tempFile.deleteOnExit();
        OutputStream os = new FileOutputStream(tempFile);
        boolean finished=false;
        try {
            Streams.copyStream(stream, os);
            finished=true;
        } finally {
            os.close();
            if(!finished) {
                tempFile.delete();
            }
        }

        finished=tempFile.renameTo(storeFile);
        if(!finished){
            log.log(Level.SEVERE, "Failed to rename output to file {0}", storeFile);
            tempFile.delete();
        }

       
        System.out.println("DEBUG: putting file to http://localhost/uploads/"+storeFile.getName());
        Sardine sardine = SardineFactory.begin("admin", "admin");
        InputStream fis = new FileInputStream(storeFile);
        sardine.put("http://localhost/uploads/"+storeFile.getName(), fis);

        return finished;
    }

    public boolean retrieve(OutputStream stream) throws IOException {

        File getFile = getDestinationFile();

        Sardine sardine = SardineFactory.begin("admin", "admin");
        InputStream inpu = sardine.get("http://localhost/uploads/"+getFile.getName());

        //introduce delay
        boolean finished = false;
        try {
            Streams.copyStream(inpu, stream);
            finished=true;
        } finally {
            inpu.close();
        }
        log.log(Level.INFO, "Retrieved output from file {0}", getFile);
        return finished;
    }

    public void initialize(Map<String, ?> context) {
        this.context = context;
    }

    private File getDestinationFile() {
        return new File(destinationDirPath, "output-log-" + getIdentity() + ".log");
    }

    private File getDestinationTempFile() {
        return new File(destinationDirPath, "output-log-" + getIdentity() + ".log.temp");
    }

    private String getIdentity() {
        return (String) context.get("execid");
    }

    public LogFileState getState() {
        //introduce delay
        File storeFile = getDestinationFile();
        File tempFile = getDestinationTempFile();

        System.out.println("DEBUG: checking for file http://localhost/uploads/"+storeFile.getName());
        Sardine sardine = SardineFactory.begin("admin", "admin");

        LogFileState state;
        try {
            state = sardine.exists("http://localhost/uploads/"+storeFile.getName())? LogFileState.AVAILABLE : tempFile.exists()? LogFileState.PENDING : LogFileState.NOT_FOUND;
        } catch (IOException e) {
            e.printStackTrace(System.err);
            state = LogFileState.NOT_FOUND;
        }

        log.log(Level.SEVERE, "call getState {0}", state);
        return state;
    }

}
