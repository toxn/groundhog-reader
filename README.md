Fork of Groundhog News Reader
=============================

NNTP news reader for Android originally developed by Juanjo Alvarez.

URL of the original project page on Launchpad: https://launchpad.net/groundhog/ 

Project is still in process of cleanup. Libraries forked for the purpose of the project are successively moved out to independent projects.
For the time being, the only such external dependency is apache-mime4j library fitted for the needs of the Groundhog Reader.

https://github.com/zoldar/apache-james-mime4j-groundhog

Quick Start
-----------

In order to build and deploy the application, you need to have Maven installed on your system.

http://maven.apache.org/

If not told otherwise, all commands have to be issued from the project's root directory.

1. Build project 

    mvn install

2. Deploy project to the connected phone

    mvn android:deploy
