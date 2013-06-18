
This plugin provides a log storage service interface to a WebDAV repository.
Execution output will be stored in a user defined path.

This plugin was developed and tested against Apache2 and mod_dav.

## Build

    gradle clean build

The build target will be found in build/libs. Eg,

    cp build/libs/rundeck-webdav-logstore-plugin-1.0.2.jar $RDECK_BASE/libext

## Installation

Copy the plugin JAR file to the `RDECK_BASE/libext` directory.

### Configuration

Update the rundeck-config.properties by adding the plugin name `webdav-logstore`:

    cat >>/etc/rundeck/rundeck-config.properties <<EOF
    rundeck.execution.logs.fileStoragePlugin=webdav-logstore
    EOF

Add WebDAV connection info to the framework.properties:

    cat >>/etc/rundeck/framework.properties<<EOF
    framework.plugin.LogFileStorage.webdav-logstore.webdavUrl = $WEBDAV_URL
    framework.plugin.LogFileStorage.webdav-logstore.webdavUsername = admin
    framework.plugin.LogFileStorage.webdav-logstore.webdavPassword = admin
    framework.plugin.LogFileStorage.webdav-logstore.path = rundeck/projects/${job.project}/${job.execid}.rdlog
    EOF

* `webdavUrl` should be the base URL to the WebDAV log store.
* `webdavUsername` is the login account to the store.
* `webdavPassword` is the password to the account.
* `path` is the resource path to the file in the WebDAV store. The default path is "rundeck/projects/${job.project}/${job.execid}.rdlog". You can define any path and reference the following tokens:
  * `${job.execid}`: The job execution id.
  * `${job.id}`: The job ID.
  * `${job.project}`: The project name.