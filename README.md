
This plugin stores [Rundeck](https://github.com/rundeck/rundeck)
execution logs in a WebDAV repository.
Execution logs are stored in a user defined path.

This plugin was developed and tested against Apache2 and mod_dav.

## Build

    gradle clean build

The build target will be found in build/libs. Eg,

    cp build/libs/rundeck-webdav-logstore-plugin-2.1.0.jar $RDECK_BASE/libext

## Installation

Copy the plugin JAR file to the `RDECK_BASE/libext` directory.

### Configuration

Update the rundeck-config.properties by adding the plugin name `webdav-logstore`:

    rundeck.execution.logs.fileStoragePlugin=webdav-logstore


Add WebDAV connection info to the /etc/rundeck/framework.properties:

    framework.plugin.ExecutionFileStorage.webdav-logstore.webdavUrl = $WEBDAV_URL
    framework.plugin.ExecutionFileStorage.webdav-logstore.webdavUsername = admin
    framework.plugin.ExecutionFileStorage.webdav-logstore.webdavPassword = admin
    framework.plugin.ExecutionFileStorage.webdav-logstore.path = rundeck/projects/${job.project}/${job.execid}.rdlog


* `webdavUrl` should be the base URL to the WebDAV log store.
* `webdavUsername` is the login account to the store.
* `webdavPassword` is the password to the account.
* `path` is the resource path to the file in the WebDAV store. The default path is "rundeck/projects/${job.project}/${job.execid}.rdlog". You can define any path and reference the following tokens:
  * `${job.execid}`: The job execution id.
  * `${job.id}`: The job ID.
  * `${job.project}`: The project name.
