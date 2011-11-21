Fork of Groundhog News Reader
=============================

NNTP news reader for Android originally developed by Juanjo Alvarez.

URL of the original project page on Launchpad: https://launchpad.net/groundhog/ 

Project is still in the process of cleanup. 
James Apache Mime4J fork was moved to a separate project:

https://github.com/zoldar/apache-james-mime4j-groundhog

Quick Start
-----------

In order to build and deploy the application, you need to have Maven (http://maven.apache.org) as well as Android SDK (http://developer.android.com/sdk/index.html) installed and configured on your system.

If not told otherwise, all commands have to be issued from the project's root directory.

1. Build project 

        mvn install

2. Deploy project to the connected phone

        mvn android:deploy
