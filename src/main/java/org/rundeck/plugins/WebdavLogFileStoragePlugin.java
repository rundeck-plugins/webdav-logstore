package org.rundeck.plugins;


import com.dtolabs.rundeck.core.logging.LogFileStorageException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.logging.LogFileStoragePlugin;
import com.dtolabs.utils.Streams;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example plugin which copies log files to a WebDAV store
 */
@Plugin(service = ServiceNameConstants.LogFileStorage, name = "webdav-logstore")
@PluginDescription(title = "WebDAV Log File Storage Plugin", description = "Webdav Log File Storage")
public class WebdavLogFileStoragePlugin implements LogFileStoragePlugin {
    static final Logger logger = Logger.getLogger(WebdavLogFileStoragePlugin.class.getName());

    public static final String DEFAULT_PATH_FORMAT = "rundeck/projects/${job.project}/${job.execid}.rdlog";

    private Map<String, ?> context;

    @PluginProperty(
            title = "Path",
            required = true,
            description = "The path in the WebDAV to store a log file. You can use these " +
                    "expansion variables: (${job.execid} = execution ID, ${job.project} = project name, " +
                    "${job.id} = job UUID (or blank)." +
                    " Default: "
                    + DEFAULT_PATH_FORMAT,
            defaultValue = DEFAULT_PATH_FORMAT
    )
    private String path;

    @PluginProperty(
            title="WebDAV URL",
            required = true,
            description = "The WebDAV base URL."
    )
    private String webdavUrl;

    @PluginProperty(title="WebDAV username", required = true,
            description = "The WebDAV account username")
    private String webdavUsername;
    @PluginProperty(title="WebDAV password", required = true,
            description = "The WebDAV account password")
    private String webdavPassword;

    // The value of the path after the tokens have been replaced by the context data.
    private String expandedPath;

    public WebdavLogFileStoragePlugin() {
    }


    /**
     * Add collections referenced in the resourcePath after the webdavUrl.
     * Creates intermediate collections as needed.
     *
     * @param resourcePath Collection to create.
     *
     * @return true if the collection was created successfully.
     *
     * @throws IOException   WebDAV method errors are thrown if the server request fails.
     * Exception info contains the HTTP response code.
     */
    private boolean createCollection(final String resourcePath) throws IOException {

        boolean success = false;
        final Sardine sardine = SardineFactory.begin(webdavUsername, webdavPassword);

        // Extract the resource path relative to the DAV url.
        final String relativePath = resourcePath.substring(webdavUrl.length());
        final StringBuilder collectionPath = new StringBuilder(webdavUrl);
        logger.log(Level.FINE, "Creating resourcePath={0}, subPath={1}...", new Object[]{resourcePath, relativePath});

        // Tokenize the relative path into separate elements and check and create each level recursively.
        final String[] pathTokens = relativePath.split("/");
        for (String pathToken : pathTokens) {
            // Ignore blank or null path token values.
            if ("".equals(pathToken) || null == pathToken) continue;

            // add the next path token to the collection path URI.
            collectionPath.append("/").append(pathToken);

            // if the collection already exists, continue to the next path token.
            if (sardine.exists(collectionPath.toString())) continue;

            // Create the next level in the resource path.
            sardine.createDirectory(collectionPath.toString());
            logger.log(Level.INFO, "Created WebDAV collection: {0}", new Object[]{collectionPath.toString()});

            // iterate to next path token.
        }

        return success;
    }

    public boolean store(final InputStream stream, final long length, final Date modtime)
            throws IOException, LogFileStorageException {
        logger.log(Level.FINE, "Storing log to {0}/{1}", new Object[]{webdavUrl,expandedPath});
        Sardine sardine = SardineFactory.begin(webdavUsername, webdavPassword);

        // The expandedPath contains the file resource but we want to create the parent collection if needed.
        final String collection =  new File(expandedPath).getParent();
        if (!sardine.exists(webdavUrl + "/" + collection)) {
            // Create the collection.
            createCollection(webdavUrl + "/" + collection);
        }

        // Add the resource to the store.
        try {
            sardine.put(webdavUrl + "/" + expandedPath, stream);
        } catch (IOException e) {
            throw new LogFileStorageException("Log location: "
                +webdavUrl+"/"+expandedPath+". Reason: " +e.getMessage(), e);
        }
        logger.log(Level.INFO, "Stored log to {0}/{1}", new Object[]{webdavUrl, expandedPath});

        return true;
    }

    public boolean isAvailable() throws LogFileStorageException {
        logger.log(Level.FINE, "Getting state about log {0}/{1}", new Object[]{webdavUrl,expandedPath});

        final Sardine sardine = SardineFactory.begin(webdavUsername,webdavPassword);

        boolean available;
        try {
            available = sardine.exists(webdavUrl + "/" + expandedPath);
        } catch (IOException e) {
            throw new LogFileStorageException("Log location: "
                    +webdavUrl+"/"+expandedPath+". Reason: " +e.getMessage(), e);
        }
        return available;
    }

    public boolean retrieve(final OutputStream stream) throws IOException, LogFileStorageException {

        logger.log(Level.INFO, "Retrieving log from {0}/{1}", new Object[]{webdavUrl, expandedPath});

        final Sardine sardine = SardineFactory.begin(webdavUsername, webdavPassword);
        final InputStream input;
        try {
            input = sardine.get(webdavUrl + "/" + expandedPath);
        } catch (IOException e) {
            throw new LogFileStorageException("Log location: "
                    + webdavUrl + "/" + expandedPath + ". Reason: " + e.getMessage(), e);
        }

        boolean finished = false;
        try {
            Streams.copyStream(input, stream);
            finished=true;
        } finally {
            input.close();
        }
        return finished;
    }

    public void initialize(final Map<String, ?> context) {
        this.context = context;

        if (null == getPath() || "".equals(getPath().trim())) {
            throw new IllegalArgumentException("path was not set");
        }
        if (!getPath().contains("${job.execid}") && !getPath().endsWith("/")) {
            throw new IllegalArgumentException("path must contain ${job.execid} or end with /");
        }
        String configpath = getPath();
        if (!configpath.contains("${job.execid}") && configpath.endsWith("/")) {
            configpath = path + "/${job.execid}.rdlog";
        }
        expandedPath = expandPath(configpath, context);
        if (null == expandedPath || "".equals(expandedPath.trim())) {
            throw new IllegalArgumentException("expanded value of path was empty");
        }
        if (expandedPath.endsWith("/")) {
            throw new IllegalArgumentException("expanded value of path must not end with /");
        }

        logger.log(Level.FINE, "Expanded path for the log {0}/{1}", new Object[]{webdavUrl,expandedPath});

    }


    /**
     * Expands the path format using the context data
     *
     * @param pathFormat
     * @param context
     *
     * @return
     */
    static String expandPath(String pathFormat, Map<String, ? extends Object> context) {
        String result = pathFormat.replaceAll("^/+", "");
        if (null != context) {
            result = result.replaceAll("\\$\\{job.execid\\}", notNull(context, "execid", ""));
            result = result.replaceAll("\\$\\{job.id\\}", notNull(context, "id", ""));
            result = result.replaceAll("\\$\\{job.project\\}", notNull(context, "project", ""));
        }
        result = result.replaceAll("/+", "/");

        return result;
    }

    private static String notNull(Map<String, ?> context1, String execid1, String defaultValue) {
        final Object value = context1.get(execid1);
        return value != null ? value.toString() : defaultValue;
    }

    public String getPath() {
        return path;
    }


}
