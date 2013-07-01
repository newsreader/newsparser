#!/bin/tcsh

#set the JAVA_HOME to JDK >= 1.7, for instance:
#setenv JAVA_HOME /marquez0/tcc/cgirardi/java/jdk1.7.0_21/

$JAVA_HOME/bin/javac -cp . -d classes/ -sourcepath src/ src/main/java/org/fbk/hlt/newsreader/ZipManager.java 

$JAVA_HOME/bin/javac -cp classes/ TextproParser.java
$JAVA_HOME/bin/javac -cp classes/ TestParser.java

